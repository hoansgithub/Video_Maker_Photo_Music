package com.videomaker.aimusic.core.data.local

import android.content.Context
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.os.ConfigurationCompat
import java.net.HttpURLConnection
import java.net.URL

/**
 * Single source of truth for the user's region code (ISO 3166-1 alpha-2, lowercase).
 *
 * PRODUCTION BUILDS:
 * Resolution order (when IP geolocation disabled via remote config):
 * 1. In-memory cache (set once per process)
 * 2. Persisted value in PreferencesManager (set on first run)
 * 3. Auto-detected from system: SIM → Network → Locale → Fallback "us"
 *
 * IP-BASED DETECTION (when enabled via remote config OR debug builds):
 * Resolution order:
 * 1. IP geolocation (VPN-aware, configurable timeout)
 * 2. Fallback to persisted region
 * 3. Fallback to system detection (SIM → Network → Locale)
 *
 * Remote Config:
 * - region_detection_config.use_ip_geolocation: Enable IP detection in production
 * - region_detection_config.ip_timeout_ms: Timeout for IP detection (default: 5000ms)
 *
 * Used by SongRepositoryImpl and TemplateRepositoryImpl to filter and prioritize content.
 * Region-based sorting is handled by `song_region_sort` table (JOIN in RPC functions).
 * `songs.target_regions` is metadata — a song can belong to multiple catalogs:
 * - ["gl"] = global catalog, ["us"] = US catalog, ["gl","us"] = both, etc.
 */
class RegionProvider(
    private val context: Context,
    private val preferencesManager: PreferencesManager,
    private val regionDetectionConfig: com.videomaker.aimusic.core.data.remote.RegionDetectionConfig? = null
) {
    @Volatile
    private var cached: String? = null

    @Volatile
    private var ipRegionCached: String? = null

    @Volatile
    private var ipDetectionStarted = false

    @Volatile
    private var ipDetectionComplete = false

    /**
     * Returns the resolved region code. Safe to call from any thread.
     * Result is cached in memory after first call.
     *
     * IP DETECTION MODE (when enabled via remote config OR debug builds):
     * Waits for IP-based detection (VPN-aware) before returning.
     *
     * SYSTEM DETECTION MODE (default):
     * Uses fast system-based detection (SIM/Network/Locale).
     */
    /**
     * Returns region immediately using cache/persisted/system detection.
     * Never blocks — skips IP detection wait. Also kicks off IP detection
     * in background so a subsequent [getRegionCode] call may return the IP result.
     */
    fun getRegionCodeImmediate(): String {
        cached?.let { return it }
        ipRegionCached?.let { cached = it; return it }

        // Kick off IP detection if enabled but don't wait
        val shouldUseIp = regionDetectionConfig?.useIpGeolocation == true
        if (shouldUseIp && !ipDetectionStarted) {
            ipDetectionStarted = true
            val timeout = regionDetectionConfig?.ipTimeoutMs ?: 15_000
            startIPDetectionInBackground(timeout)
        }

        val persisted = preferencesManager.getUserRegion()?.takeIf { it.isNotBlank() }
        return persisted ?: detectRegionFromSystem()
    }

    fun getRegionCode(): String {
        cached?.let { return it }

        // Check if IP detection is enabled (remote config only)
        // Removed BuildConfig.DEBUG to prevent 5s blocking delay in debug builds
        val shouldUseIp = regionDetectionConfig?.useIpGeolocation == true

        if (shouldUseIp) {
            // Start IP detection in background (if not already started)
            if (!ipDetectionStarted) {
                ipDetectionStarted = true
                val timeout = regionDetectionConfig?.ipTimeoutMs ?: 15_000
                startIPDetectionInBackground(timeout)
                Log.d(TAG, "🌐 IP detection started (timeout: ${timeout}ms)")
            }

            // Use cached IP region if available (from previous detection)
            ipRegionCached?.let {
                Log.d(TAG, "🔍 Region from IP cache: $it")
                cached = it
                return it
            }

            // Wait for IP detection to complete
            if (!ipDetectionComplete) {
                val timeout = regionDetectionConfig?.ipTimeoutMs ?: 15_000
                Log.d(TAG, "⏳ Waiting for IP detection (max ${timeout}ms)...")
                val startTime = System.currentTimeMillis()
                while (!ipDetectionComplete && System.currentTimeMillis() - startTime < timeout) {
                    Thread.sleep(100) // Poll every 100ms
                }

                if (ipDetectionComplete) {
                    ipRegionCached?.let {
                        Log.d(TAG, "✅ IP detection complete: $it")
                        cached = it
                        return it
                    }
                } else {
                    Log.w(TAG, "⏱️ IP detection timeout after ${timeout}ms, falling back to system")
                }
            }

            // IP detection failed or timed out - fall back to persisted region or system detection
            val persistedRegion = preferencesManager.getUserRegion()?.takeIf { it.isNotBlank() }
            val systemRegion = detectRegionFromSystem()
            val region = persistedRegion ?: systemRegion
            Log.d(TAG, "⚠️ IP detection incomplete, using fallback")
            Log.d(TAG, "  ├─ Persisted: ${persistedRegion ?: "null"}")
            Log.d(TAG, "  ├─ System: $systemRegion")
            Log.d(TAG, "  └─ Final: $region")
            cached = region
            return region
        }

        // SYSTEM DETECTION MODE: Use persisted region or detect from system
        val cachedRegion = preferencesManager.getUserRegion()?.takeIf { it.isNotBlank() }

        val region = if (shouldMigrateCache(cachedRegion)) {
            Log.d(TAG, "🔄 Migrating from old language-based region cache")
            detectUserRegion().also { preferencesManager.setUserRegion(it) }
        } else {
            cachedRegion ?: detectUserRegion().also { preferencesManager.setUserRegion(it) }
        }

        cached = region
        return region
    }

    /**
     * Detect if cached region might be from old language-based system.
     */
    private fun shouldMigrateCache(cachedRegion: String?): Boolean {
        if (cachedRegion == null) return false

        val systemRegion = detectRegionFromSystem()
        val needsMigration = cachedRegion != systemRegion

        if (needsMigration) {
            Log.d(TAG, "⚠️ Region mismatch: cached='$cachedRegion', system='$systemRegion' (migrating)")
        }

        return needsMigration
    }

    /**
     * Force-clears cached + persisted region.
     */
    fun invalidate() {
        cached = null
        ipRegionCached = null
        ipDetectionStarted = false
        ipDetectionComplete = false
        preferencesManager.setUserRegion("")
    }

    /**
     * Auto-detect user's region.
     */
    private fun detectUserRegion(): String {
        return detectRegionFromSystem()
    }

    /**
     * Start IP-based region detection in background thread.
     * Tries multiple IP geolocation services until one works (VPN-friendly).
     *
     * @param timeoutMs Timeout for each HTTP request (from remote config)
     */
    private fun startIPDetectionInBackground(timeoutMs: Long = 15_000) {
        Thread {
            val services = listOf(
                IPGeoService("https://ipinfo.io/country", "ipinfo.io") { it.trim().lowercase() },
                IPGeoService("https://ip-api.com/line/?fields=countryCode", "ip-api.com") { it.trim().lowercase() },
                IPGeoService("https://ifconfig.co/country-iso", "ifconfig.co") { it.trim().lowercase() },
                IPGeoService("https://ipapi.co/country/", "ipapi.co") { it.trim().lowercase() }
            )

            var detectedCountry: String? = null
            var workingService: String? = null

            for (service in services) {
                try {
                    val url = URL(service.url)
                    val connection = url.openConnection() as HttpURLConnection
                    connection.connectTimeout = timeoutMs.toInt()
                    connection.readTimeout = timeoutMs.toInt()
                    connection.requestMethod = "GET"
                    connection.setRequestProperty("User-Agent", "VideoMaker-Android")

                    val response = connection.inputStream.bufferedReader()
                        .use { it.readText() }

                    val country = service.parser(response)
                        .takeIf { it.length == 2 }  // Valid ISO 3166-1 alpha-2 code

                    if (country != null) {
                        detectedCountry = country
                        workingService = service.name
                        break  // Success - stop trying other services
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "🌐 ${service.name} failed: ${e.message}")
                    // Continue to next service
                }
            }

            if (detectedCountry != null) {
                val previousRegion = cached ?: preferencesManager.getUserRegion()
                ipRegionCached = detectedCountry
                cached = detectedCountry
                preferencesManager.setUserRegion(detectedCountry)
                Log.d(TAG, "🌐 IP detection complete via $workingService: $detectedCountry")

                // Log region change for debugging
                if (previousRegion != null && previousRegion != detectedCountry) {
                    Log.w(TAG, "⚠️ Region changed: $previousRegion → $detectedCountry")
                }
            } else {
                Log.w(TAG, "🌐 All IP geolocation services failed - using system detection")
            }

            ipDetectionComplete = true
        }.start()
    }

    /**
     * IP geolocation service configuration
     */
    private data class IPGeoService(
        val url: String,
        val name: String,
        val parser: (String) -> String
    )

    /**
     * System-based region detection (SIM → Network → Locale).
     */
    private fun detectRegionFromSystem(): String {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

        // Try SIM card country (most reliable)
        tm?.simCountryIso?.lowercase()?.takeIf { it.isNotEmpty() }?.let {
            Log.d(TAG, "📱 Region from SIM: $it")
            return it
        }

        // Try network country (fallback)
        tm?.networkCountryIso?.lowercase()?.takeIf { it.isNotEmpty() }?.let {
            Log.d(TAG, "📡 Region from Network: $it")
            return it
        }

        // Try system locale country (second fallback)
        val config = context.resources.configuration
        ConfigurationCompat.getLocales(config).get(0)?.country?.lowercase()?.takeIf { it.isNotEmpty() }?.let {
            Log.d(TAG, "🌍 Region from Locale: $it")
            return it
        }

        // Hardcoded fallback
        Log.d(TAG, "⚠️ Region fallback: us")
        return "us"
    }

    companion object {
        private const val TAG = "RegionProvider"
    }
}
