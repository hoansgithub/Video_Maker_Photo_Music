package com.videomaker.aimusic.data.remote

import com.videomaker.aimusic.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

/**
 * Supabase client singleton for database access.
 *
 * Configuration is loaded from BuildConfig fields which are sourced from local.properties:
 * - SUPABASE_URL: Your Supabase project URL
 * - SUPABASE_ANON_KEY: Your Supabase anonymous/public key
 */
object SupabaseClientProvider {

    val instance: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY
        ) {
            install(Postgrest)
            // install(Auth) // Uncomment when authentication is needed
        }
    }
}
