package me.rerere.rikkahub.data.ai.tools.local

import android.content.ComponentName
import android.app.KeyguardManager
import android.content.Context
import android.provider.Settings
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.rikkahub.service.RikkaNotificationListenerService

/** Missing lock-state evidence must never disclose private notification content. */
internal fun notificationAccessAllowed(deviceLocked: Boolean?, keyguardLocked: Boolean?): Boolean =
    deviceLocked == false && keyguardLocked == false

/**
 * Bridge between the LLM tool factories and the live notification listener service.
 * Mirrors AccessibilityServiceHandle: the service publishes itself via instance,
 * we wrap access in a uniform service-not-bound envelope.
 */
object NotificationListenerHandle {

    /** True iff our component is listed in Settings.Secure.ENABLED_NOTIFICATION_LISTENERS. */
    fun isEnabledInSettings(ctx: Context): Boolean {
        val expected = ComponentName(ctx, RikkaNotificationListenerService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(
            ctx.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        return enabled.split(":").any { it.equals(expected, ignoreCase = true) }
    }

    /** True iff the service is currently bound by the OS. */
    fun isBound(): Boolean = RikkaNotificationListenerService.instance != null

    /**
     * Run [block] with the bound listener. Returns the standard not-bound envelope when the
     * service is unbound (user disabled it, OS unbound it under memory pressure, etc.).
     */
    suspend fun withListener(
        block: suspend (RikkaNotificationListenerService) -> JsonObject
    ): JsonObject {
        val svc = RikkaNotificationListenerService.instance ?: return notBoundEnvelope()
        if (!isUnlocked(svc)) return lockedEnvelope()
        val result = block(svc)
        // A suspending lookup may outlive the unlock state. Check again before its
        // content enters the model context; never ask the model to redact it afterward.
        return if (isUnlocked(svc)) result else lockedEnvelope()
    }

    private fun isUnlocked(context: Context): Boolean {
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        return notificationAccessAllowed(keyguard?.isDeviceLocked, keyguard?.isKeyguardLocked)
    }

    fun lockedEnvelope(): JsonObject = buildJsonObject {
        put("error", "device_locked")
        put("recovery", "請先解鎖手機，再查詢通知本文或執行通知操作。")
    }

    fun notBoundEnvelope(): JsonObject = buildJsonObject {
        put("error", "notification_listener_not_bound")
        put("recovery", "Enable RikkaHub in Settings → Notification access. Then return to the app.")
    }
}
