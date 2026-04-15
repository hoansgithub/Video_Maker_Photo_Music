package com.videomaker.aimusic.core.notification

import co.alcheclub.lib.acccore.remoteconfig.ConfigContainer
import co.alcheclub.lib.acccore.remoteconfig.ConfigurableObject
import com.videomaker.aimusic.core.constants.RemoteConfigKeys

class NotificationScheduleConfigService(
    private val onEffectiveConfigChanged: () -> Unit = {}
) : ConfigurableObject {

    private val lock = Any()

    @Volatile
    private var currentConfig: NotificationScheduleConfig = NotificationScheduleConfig()

    @Volatile
    private var effectiveFingerprint: String? = null

    fun current(): NotificationScheduleConfig = currentConfig

    override suspend fun update(config: ConfigContainer) {
        applyRawJsonForTesting(config.getString(RemoteConfigKeys.NOTIFICATION_SCHEDULE_CONFIG))
    }

    internal fun applyRawJsonForTesting(raw: String?) {
        val parsed = NotificationScheduleConfigParser.parse(raw)
        val fingerprint = parsed.fingerprint()
        val shouldNotify: Boolean

        synchronized(lock) {
            currentConfig = parsed
            shouldNotify = fingerprint != effectiveFingerprint
            effectiveFingerprint = fingerprint
        }

        if (shouldNotify) {
            onEffectiveConfigChanged()
        }
    }
}
