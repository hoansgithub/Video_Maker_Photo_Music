package com.videomaker.aimusic.modules.onboardingsurvey

import co.alcheclub.lib.acccore.remoteconfig.RemoteConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Minimal RemoteConfig double for unit tests.
 * Only the keys placed in [bools] are considered "present" (getAllKeys); everything else
 * falls back to the supplied default — matching the Firebase behaviour the gate relies on.
 */
class FakeRemoteConfig(
    private val bools: Map<String, Boolean> = emptyMap(),
) : RemoteConfig {
    override fun getAllKeys(): Set<String> = bools.keys
    override fun getBoolean(key: String, default: Boolean): Boolean = bools[key] ?: default

    override fun getString(key: String, default: String): String = default
    override fun getInt(key: String, default: Int): Int = default
    override fun getLong(key: String, default: Long): Long = default
    override fun getDouble(key: String, default: Double): Double = default
    override val updates: Flow<Unit> = emptyFlow()
    override suspend fun fetch(): Result<Boolean> = Result.success(true)
    override suspend fun activate(): Result<Boolean> = Result.success(true)
    override suspend fun fetchAndActivate(): Result<Boolean> = Result.success(true)
    override suspend fun setDefaults(resId: Int): Result<Unit> = Result.success(Unit)
}
