package com.videomaker.aimusic.core.data.local

import android.content.Context
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.os.ConfigurationCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Single source of truth for the user's region code (ISO 3166-1 alpha-2, lowercase).
 *
 * Resolution order:
 * 1. In-memory cache (set once per process)
 * 2. IP-based detection result (from background detection, updated asynchronously)
 * 3. Persisted value in PreferencesManager (set on first run)
 * 4. Auto-detected from system: SIM -> Network -> Locale -> Fallback "us"
 *
 * IP detection always runs in the background on first access (non-blocking).
 * When it completes, it updates the cached region and persists the result.
 * Calling [invalidate] clears all caches and re-triggers IP detection on next access
 * (useful after VPN changes).
 *
 * Registered as a singleton in AppModule -- scope lives for the entire app lifetime.
 *
 * Used by SongRepositoryImpl and TemplateRepositoryImpl to filter and prioritize content.
 * Region-based sorting is handled by `song_region_sort` table (JOIN in RPC functions).
 * `songs.target_regions` is metadata -- a song can belong to multiple catalogs:
 * - ["gl"] = global catalog, ["us"] = US catalog, ["gl","us"] = both, etc.
 */
class RegionProvider(
    private val context: Context,
    private val preferencesManager: PreferencesManager
) {
    /** Application-lifetime scope -- matches singleton registration in AppModule. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var cached: String? = null

    @Volatile
    private var ipRegionCached: String? = null

    private val ipDetectionStarted = AtomicBoolean(false)

    @Volatile
    private var ipDetectionDone = CompletableDeferred<Unit>()

    /**
     * Returns the resolved region code. Safe to call from any thread.
     * Never blocks -- returns immediately with the best available region.
     *
     * On first call, kicks off IP detection in the background. When IP detection
     * completes, subsequent calls will return the IP-detected region.
     */
    fun getRegionCode(): String {
        cached?.let { return it }
        ipRegionCached?.let { cached = it; return it }

        // compareAndSet ensures only one thread starts IP detection
        if (ipDetectionStarted.compareAndSet(false, true)) {
            startIPDetectionInBackground()
        }

        // Return persisted or system-detected immediately (never block)
        val persisted = preferencesManager.getUserRegion()?.takeIf { it.isNotBlank() }
        val region = if (shouldMigrateCache(persisted)) {
            detectRegionFromSystem().also { preferencesManager.setUserRegion(it) }
        } else {
            persisted ?: detectRegionFromSystem().also { preferencesManager.setUserRegion(it) }
        }
        cached = region
        return region
    }

    /**
     * Returns region immediately using cache/persisted/system detection.
     * Never blocks. Also kicks off IP detection in background so a subsequent
     * call may return the IP-detected result.
     *
     * Functionally identical to [getRegionCode] -- both are non-blocking.
     * Kept for API compatibility.
     */
    fun getRegionCodeImmediate(): String = getRegionCode()

    /**
     * Suspends until IP detection finishes (or times out), then returns the
     * best available region code. Use this when accurate geo-targeting matters
     * more than instant return (e.g. onboarding song fetch during splash).
     */
    suspend fun awaitRegionCode(timeoutMs: Long = OVERALL_TIMEOUT_MS): String {
        // Ensure IP detection is running
        getRegionCode()
        withTimeoutOrNull(timeoutMs) { ipDetectionDone.await() }
        return getRegionCode()
    }

    /**
     * Detect if cached region might be from old language-based system.
     */
    private fun shouldMigrateCache(cachedRegion: String?): Boolean {
        if (cachedRegion == null) return false

        val systemRegion = detectRegionFromSystem()
        val needsMigration = cachedRegion != systemRegion

        if (needsMigration) {
            Log.d(TAG, "Region mismatch: cached='$cachedRegion', system='$systemRegion' (migrating)")
        }

        return needsMigration
    }

    /**
     * Force-clears cached + persisted region and re-enables IP detection.
     * Call after VPN changes to force fresh IP detection on next access.
     */
    fun invalidate() {
        cached = null
        ipRegionCached = null
        ipDetectionStarted.set(false)
        ipDetectionDone = CompletableDeferred()
        preferencesManager.setUserRegion("")
    }

    /**
     * Start IP-based region detection using coroutines.
     *
     * Strategy: race all services in parallel, first valid result wins.
     * This avoids the sequential problem where one slow service eats the
     * entire timeout budget. All services share the same [OVERALL_TIMEOUT_MS]
     * deadline; individual socket timeouts ([PER_SERVICE_TIMEOUT_MS]) act as
     * a secondary guard per connection.
     */
    private fun startIPDetectionInBackground() {
        scope.launch {
            // ip-api.com free tier does NOT support HTTPS -- excluded
            val services = listOf(
                IPGeoService("https://ipinfo.io/country", "ipinfo.io"),
                IPGeoService("https://ifconfig.co/country-iso", "ifconfig.co"),
                IPGeoService("https://ipapi.co/country/", "ipapi.co")
            )

            val result = withTimeoutOrNull(OVERALL_TIMEOUT_MS) {
                val deferred = services.map { service ->
                    async { tryService(service)?.let { country -> country to service.name } }
                }

                // Race: return the first non-null result
                val remaining = deferred.toMutableList()
                var winner: Pair<String, String>? = null
                while (remaining.isNotEmpty() && winner == null) {
                    winner = select {
                        remaining.forEach { d ->
                            d.onAwait { it }
                        }
                    }
                    if (winner == null) {
                        // Remove all completed (failed) entries before next select
                        remaining.removeAll { it.isCompleted }
                    }
                }

                // Cancel services still in-flight
                deferred.filter { !it.isCompleted }.forEach { it.cancel() }
                winner
            }

            if (result != null) {
                applyDetectedRegion(result.first, result.second)
            } else {
                Log.w(TAG, "IP detection failed (all services timed out or errored)")
            }
            ipDetectionDone.complete(Unit)
        }
    }

    /**
     * Try a single IP geolocation service.
     * @return lowercase 2-letter country code, or null on failure
     */
    private suspend fun tryService(service: IPGeoService): String? {
        return try {
            withContext(Dispatchers.IO) {
                val connection = URL(service.url).openConnection() as HttpURLConnection
                connection.connectTimeout = PER_SERVICE_TIMEOUT_MS.toInt()
                connection.readTimeout = PER_SERVICE_TIMEOUT_MS.toInt()
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", "VideoMaker-Android")

                try {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val country = response.trim().lowercase()
                    if (country.length == 2) country else null
                } finally {
                    connection.disconnect()
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "${service.name} failed: ${e.message}")
            null
        }
    }

    /**
     * Apply a successfully detected region from IP geolocation.
     */
    private fun applyDetectedRegion(country: String, serviceName: String) {
        val previousRegion = cached ?: preferencesManager.getUserRegion()
        ipRegionCached = country
        cached = country
        preferencesManager.setUserRegion(country)
        Log.d(TAG, "IP detection complete via $serviceName: $country")

        if (previousRegion != null && previousRegion != country) {
            Log.w(TAG, "Region changed: $previousRegion -> $country")
        }
    }

    private data class IPGeoService(val url: String, val name: String)

    /**
     * System-based region detection (SIM -> Network -> Locale).
     */
    private fun detectRegionFromSystem(): String {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

        // Try SIM card country (most reliable)
        tm?.simCountryIso?.lowercase()?.takeIf { it.isNotEmpty() }?.let {
            Log.d(TAG, "Region from SIM: $it")
            return it
        }

        // Try network country (fallback)
        tm?.networkCountryIso?.lowercase()?.takeIf { it.isNotEmpty() }?.let {
            Log.d(TAG, "Region from Network: $it")
            return it
        }

        // Try system locale country (second fallback)
        val config = context.resources.configuration
        ConfigurationCompat.getLocales(config).get(0)?.country?.lowercase()?.takeIf { it.isNotEmpty() }?.let {
            Log.d(TAG, "Region from Locale: $it")
            return it
        }

        // Hardcoded fallback
        Log.d(TAG, "Region fallback: us")
        return "us"
    }

    companion object {
        private const val TAG = "RegionProvider"
        private const val PER_SERVICE_TIMEOUT_MS = 2000L
        private const val OVERALL_TIMEOUT_MS = 5000L
    }
}
