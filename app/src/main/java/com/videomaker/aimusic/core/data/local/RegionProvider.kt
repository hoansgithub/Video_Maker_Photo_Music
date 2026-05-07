package com.videomaker.aimusic.core.data.local

import android.content.Context
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.os.ConfigurationCompat
import com.videomaker.aimusic.BuildConfig
import java.net.HttpURLConnection
import java.net.URL

/**
 * Single source of truth for the user's region code (ISO 3166-1 alpha-2, lowercase).
 *
 * PRODUCTION BUILDS:
 * Resolution order:
 * 1. In-memory cache (set once per process)
 * 2. Persisted value in PreferencesManager (set on first run)
 * 3. Auto-detected from system: SIM → Network → Locale → Fallback "us"
 *
 * DEBUG BUILDS ONLY (for QC testing with VPN):
 * Uses IP-based geolocation to detect region, allowing VPN testing.
 * Waits up to 15 seconds for IP detection to complete before falling back to system detection.
 *
 * Used by SongRepositoryImpl and TemplateRepositoryImpl to filter and prioritize content:
 * - Priority 1: Content targeting user's region (target_regions = ["us"])
 * - Priority 2: Explicit global content (target_regions = ["all"])
 * - Priority 3: Implicit global content (target_regions = [] empty)
 */
class RegionProvider(
    private val context: Context,
    private val preferencesManager: PreferencesManager
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
     * DEBUG MODE: Waits for IP-based detection (VPN-aware) before returning.
     * RELEASE MODE: Uses fast system-based detection (SIM/Network/Locale).
     */
    fun getRegionCode(): String {
        cached?.let { return it }

        // DEBUG MODE: Use IP detection (VPN-aware)
        if (BuildConfig.DEBUG) {
            // Start IP detection in background (if not already started)
            if (!ipDetectionStarted) {
                ipDetectionStarted = true
                startIPDetectionInBackground()
                Log.d(TAG, "🌐 [DEBUG] IP detection started in background")
            }

            // Use cached IP region if available (from previous detection)
            ipRegionCached?.let {
                Log.d(TAG, "🔍 [DEBUG] Region from IP cache: $it")
                cached = it
                return it
            }

            // Wait for IP detection to complete (max 15 seconds)
            if (!ipDetectionComplete) {
                Log.d(TAG, "⏳ [DEBUG] Waiting for IP detection to complete (max 15s)...")
                val startTime = System.currentTimeMillis()
                while (!ipDetectionComplete && System.currentTimeMillis() - startTime < 15_000) {
                    Thread.sleep(100) // Poll every 100ms
                }

                if (ipDetectionComplete) {
                    ipRegionCached?.let {
                        Log.d(TAG, "✅ [DEBUG] IP detection complete, using: $it")
                        cached = it
                        return it
                    }
                } else {
                    Log.w(TAG, "⏱️ [DEBUG] IP detection timeout after 15s, falling back to system")
                }
            }

            // IP detection failed or timed out - fall back to persisted region or system detection
            val persistedRegion = preferencesManager.getUserRegion()?.takeIf { it.isNotBlank() }
            val systemRegion = detectRegionFromSystem()
            val region = persistedRegion ?: systemRegion
            Log.d(TAG, "⏳ [DEBUG] IP detection incomplete")
            Log.d(TAG, "  ├─ Persisted region: ${persistedRegion ?: "null"}")
            Log.d(TAG, "  ├─ System region: $systemRegion")
            Log.d(TAG, "  └─ Using: $region (${if (persistedRegion != null) "persisted" else "system"})")
            cached = region
            return region
        }

        // RELEASE MODE: Use persisted system region or detect from system
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
     * Start IP-based region detection in background thread (DEBUG BUILDS ONLY).
     * Tries multiple IP geolocation services until one works (VPN-friendly).
     */
    private fun startIPDetectionInBackground() {
        if (!BuildConfig.DEBUG) {
            Log.e(TAG, "❌ IP detection called in RELEASE build - should never happen!")
            return
        }

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
                    connection.connectTimeout = 10000  // 10s timeout (VPN-friendly)
                    connection.readTimeout = 10000
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
                    Log.d(TAG, "🌐 [DEBUG] ${service.name} failed: ${e.message}")
                    // Continue to next service
                }
            }

            if (detectedCountry != null) {
                val previousRegion = cached ?: preferencesManager.getUserRegion()
                ipRegionCached = detectedCountry
                cached = detectedCountry
                preferencesManager.setUserRegion(detectedCountry)
                Log.d(TAG, "🌐 [DEBUG] IP detection complete via $workingService: $detectedCountry (updated cache)")

                // Log region change for debugging
                if (previousRegion != null && previousRegion != detectedCountry) {
                    Log.w(TAG, "⚠️ Region changed: $previousRegion → $detectedCountry")
                }
            } else {
                Log.w(TAG, "🌐 [DEBUG] All IP geolocation services failed - using system detection")
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
