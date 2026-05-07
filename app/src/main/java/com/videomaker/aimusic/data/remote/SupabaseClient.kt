package com.videomaker.aimusic.data.remote

import com.videomaker.aimusic.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import io.ktor.client.plugins.HttpTimeout

/**
 * Supabase client singleton for database and storage access.
 *
 * Configuration is loaded from BuildConfig fields which are sourced from local.properties:
 * - SUPABASE_URL: Your Supabase project URL
 * - SUPABASE_ANON_KEY: Your Supabase anonymous/public key
 *
 * DEBUG MODE: Uses 60-second request timeouts to handle slow VPN connections during testing.
 * RELEASE MODE: Uses 10-second timeouts for production.
 */
object SupabaseClientProvider {

    @OptIn(SupabaseInternal::class)
    val instance: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY
        ) {
            install(Postgrest)
            install(Storage)  // For beat-sync JSON downloads from beats-cache bucket
            // install(Auth) // Uncomment when authentication is needed

            // Configure HTTP timeouts via Ktor HttpClient
            // DEBUG: 60s for VPN testing
            // RELEASE: 10s for production
            httpConfig {
                install(HttpTimeout) {
                    val timeoutMillis = if (BuildConfig.DEBUG) 60_000L else 10_000L
                    requestTimeoutMillis = timeoutMillis
                    connectTimeoutMillis = timeoutMillis
                    socketTimeoutMillis = timeoutMillis
                }
            }
        }
    }
}
