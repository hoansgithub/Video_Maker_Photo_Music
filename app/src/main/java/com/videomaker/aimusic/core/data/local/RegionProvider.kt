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
 * Resolution order:
 * 1. In-memory cache (set once per process)
 * 2. Persisted value in PreferencesManager (set on first run)
 * 3. Auto-detected from system: SIM → Network → Locale → Fallback "us"
 *
 * Detection logic matches LanguageConfigService for consistency.
 * Completely independent of language selection (language = UI localization, region = content).
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

    /**
     * Returns the resolved region code. Safe to call from any thread.
     * Result is cached in memory after first call.
     *
     * Auto-migrates from old language-based region cache to new system-based detection.
     */
    fun getRegionCode(): String {
        cached?.let { return it }

        // Check if we need to migrate from old language-based cache
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
     * If user's system location doesn't match cached region, force re-detection.
     */
    private fun shouldMigrateCache(cachedRegion: String?): Boolean {
        if (cachedRegion == null) return false

        // Quick check: If system detection differs from cache, likely old language-based cache
        val systemRegion = detectRegionFromSystem()
        val needsMigration = cachedRegion != systemRegion

        if (needsMigration) {
            Log.d(TAG, "⚠️ Region mismatch: cached='$cachedRegion', system='$systemRegion' (migrating)")
        }

        return needsMigration
    }

    /**
     * Force-clears cached + persisted region.
     * Next call to getRegionCode() will re-detect from system.
     */
    fun invalidate() {
        cached = null
        preferencesManager.setUserRegion("")
    }

    /**
     * Auto-detect user's region.
     *
     * DEBUG MODE: Uses IP-based detection (VPN-friendly for QC testing)
     * RELEASE MODE: Uses SIM/Network detection (VPN-resistant, accurate)
     */
    private fun detectUserRegion(): String {
        // Debug mode ONLY: Allow VPN testing via IP geolocation
        if (BuildConfig.DEBUG) {
            detectRegionFromIP()?.let {
                Log.d(TAG, "🔍 [DEBUG] Region from IP: $it")
                return it
            }
        }

        // Production: Use system detection (SIM → Network → Locale)
        return detectRegionFromSystem()
    }

    /**
     * IP-based region detection for QC testing (DEBUG BUILDS ONLY).
     * Uses ipapi.co (no auth required, 1000 req/day free).
     * Blocking call - acceptable for debug/QC use only.
     *
     * @return Country code from IP geolocation, or null if unavailable
     */
    private fun detectRegionFromIP(): String? {
        return try {
            val url = URL("https://ipapi.co/country/")
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 3000  // 3s timeout
            connection.readTimeout = 3000
            connection.requestMethod = "GET"

            val country = connection.inputStream.bufferedReader()
                .use { it.readText() }
                .trim()
                .lowercase()
                .takeIf { it.length == 2 }  // Valid ISO 3166-1 alpha-2 code

            Log.d(TAG, "🌐 IP geolocation: ${country ?: "unavailable"}")
            country
        } catch (e: Exception) {
            Log.w(TAG, "IP detection failed (falling back to system): ${e.message}")
            null  // Fallback to system detection
        }
    }

    /**
     * System-based region detection (SIM → Network → Locale).
     * Used in production and as fallback in debug.
     *
     * @return Country code from system APIs
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