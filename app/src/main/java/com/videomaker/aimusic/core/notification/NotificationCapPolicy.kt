package com.videomaker.aimusic.core.notification

class NotificationCapPolicy(
    private val scheduleConfigService: NotificationScheduleConfigService? = null,
    private val dailyCap: Int = DAILY_CAP,
    private val perTypeDailyCap: Int = PER_TYPE_DAILY_CAP,
    private val cooldownMinutes: Int = 0,
    private val perItemCooldownHours: Int = PER_ITEM_COOLDOWN_HOURS
) {

    data class Input(
        val nowMs: Long,
        val dailyShownCount: Int,
        val typeDailyShownCount: Int = 0,
        val lastShownAtMs: Long?,
        val sameItemLastShownAtMs: Long?,
        val ignorePerItemCooldown: Boolean = false
    )

    data class Decision(
        val allowed: Boolean,
        val reason: BlockReason? = null
    )

    enum class BlockReason(val analyticsReason: String) {
        GLOBAL_DAILY_CAP_REACHED("global_daily_cap"),
        PER_TYPE_DAILY_CAP_REACHED("per_type_daily_cap"),
        GLOBAL_COOLDOWN_ACTIVE("global_cooldown"),
        PER_ITEM_COOLDOWN_ACTIVE("per_item_cooldown")
    }

    fun evaluate(input: Input): Decision {
        if (scheduleConfigService?.current()?.isFastScheduleMode() == true) {
            return Decision(allowed = true)
        }

        if (input.dailyShownCount >= dailyCap) {
            return Decision(
                allowed = false,
                reason = BlockReason.GLOBAL_DAILY_CAP_REACHED
            )
        }

        if (input.typeDailyShownCount >= perTypeDailyCap) {
            return Decision(
                allowed = false,
                reason = BlockReason.PER_TYPE_DAILY_CAP_REACHED
            )
        }

        val cooldownMs = cooldownMinutes * 60_000L
        if (isWithinWindow(input.nowMs, input.lastShownAtMs, cooldownMs)) {
            return Decision(
                allowed = false,
                reason = BlockReason.GLOBAL_COOLDOWN_ACTIVE
            )
        }

        val perItemCooldownMs = perItemCooldownHours * 60L * 60_000L
        if (!input.ignorePerItemCooldown &&
            isWithinWindow(input.nowMs, input.sameItemLastShownAtMs, perItemCooldownMs)
        ) {
            return Decision(
                allowed = false,
                reason = BlockReason.PER_ITEM_COOLDOWN_ACTIVE
            )
        }

        return Decision(allowed = true)
    }

    private fun isWithinWindow(nowMs: Long, lastMs: Long?, windowMs: Long): Boolean {
        val value = lastMs ?: return false
        return (nowMs - value) < windowMs
    }

    companion object {
        const val DAILY_CAP = 3
        const val PER_TYPE_DAILY_CAP = 1
        const val COOLDOWN_MINUTES = 120
        const val PER_ITEM_COOLDOWN_HOURS = 24
    }
}
