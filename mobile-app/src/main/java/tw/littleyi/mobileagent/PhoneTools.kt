package tw.littleyi.mobileagent

import android.app.KeyguardManager
import android.app.Notification
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.provider.readInternetState

class AgentNotificationService : NotificationListenerService() {
    override fun onListenerConnected() { instance = this }
    override fun onListenerDisconnected() { if (instance === this) instance = null }
    override fun onDestroy() { if (instance === this) instance = null; super.onDestroy() }
    companion object { @Volatile var instance: AgentNotificationService? = null; private set }
}

class PhoneTools(private val context: Context) {
    fun requireUnlocked() {
        val lock = context.getSystemService(KeyguardManager::class.java)
        AgentPolicy.requireUnlocked(lock?.isDeviceLocked, lock?.isKeyguardLocked)
    }

    private fun schema(name: String): InputSchema.Obj {
        val properties = buildJsonObject {
            when (name) {
                "launch_app" -> put("package", buildJsonObject { put("type", "string") })
                "open_settings" -> put("page", buildJsonObject {
                    put("type", "string"); put("enum", JsonArray(AgentPolicy.settingsPages.sorted().map(::JsonPrimitive)))
                })
                "set_volume", "set_brightness" -> put("percent", buildJsonObject {
                    put("type", "integer"); put("minimum", 0); put("maximum", 100)
                })
                "set_wifi_enabled", "set_airplane_mode" -> put("enabled", buildJsonObject { put("type", "boolean") })
            }
        }
        return InputSchema.Obj(properties, properties.keys.toList())
    }

    fun declarations(allowNotifications: Boolean): List<Tool> = AgentPolicy.tools
        .filter { allowNotifications || it != "read_notifications" }.sorted().map { name ->
        Tool(name, when(name) {
            "list_apps" -> "列出可啟動的 App 名稱及 package。"
            "launch_app" -> "開啟指定 package 的主畫面，不執行 App 內動作。"
            "open_settings" -> "開啟系統設定頁面，不修改設定。"
            "read_settings" -> "查詢亮度與媒體音量百分比。"
            "read_notifications" -> "使用者明確要求查通知時，讀取目前通知（最多 30 則）。本文可傳給已選模型做摘要。"
            "set_brightness" -> "修改螢幕亮度，必須逐次經使用者確認。"
            "read_network_state" -> "查詢 Wi-Fi 開關、飛航模式與 Internet 驗證狀態，不連線至外部服務。"
            "set_wifi_enabled" -> "開啟或關閉 Wi-Fi，需確認及受限無障礙權限；不處理 SSID 或密碼。離線仍可執行。"
            "set_airplane_mode" -> "開啟或關閉飛航模式，需確認及受限無障礙權限。離線仍可執行。"
            else -> "修改媒體音量，必須逐次經使用者確認。"
        }, parameters = { schema(name) }, needsApproval = { name in AgentPolicy.confirmed },
            // Providers only receive declarations. Dispatch is exclusively through execute().
            execute = { error("工具必須經過手機 Agent 的確認與參數驗證。") })
    }

    suspend fun execute(name: String, args: JsonObject): String = withContext(Dispatchers.Main.immediate) {
        requireUnlocked()
        AgentPolicy.validate(name, args)
        val result = when (name) {
            "list_apps" -> {
                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                JsonArray(context.packageManager.queryIntentActivities(intent, 0)
                    .filter { it.activityInfo.packageName != context.packageName }
                    .distinctBy { it.activityInfo.packageName }.sortedBy { it.loadLabel(context.packageManager).toString() }
                    .take(150).map { buildJsonObject {
                        put("name", it.loadLabel(context.packageManager).toString())
                        put("package", it.activityInfo.packageName)
                    } }).toString()
            }
            "launch_app" -> {
                val pkg = args.getValue("package").jsonPrimitive.content
                require(pkg != context.packageName) { "不允許模型操作 Agent 自己的介面。" }
                val launch = context.packageManager.getLaunchIntentForPackage(pkg) ?: error("找不到可啟動的 App。")
                context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "已送出開啟 App 的要求；不代表 App 內工作已完成。"
            }
            "open_settings" -> {
                val action = when(args.getValue("page").jsonPrimitive.content) {
                    "wifi" -> Settings.ACTION_WIFI_SETTINGS
                    "airplane" -> Settings.ACTION_AIRPLANE_MODE_SETTINGS
                    "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
                    "display" -> Settings.ACTION_DISPLAY_SETTINGS
                    "sound" -> Settings.ACTION_SOUND_SETTINGS
                    "notifications" -> Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
                    else -> Settings.ACTION_SETTINGS
                }
                context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "已開啟設定頁，未修改設定。"
            }
            "read_settings" -> {
                val audio = context.getSystemService(AudioManager::class.java)
                buildJsonObject {
                    put("brightnessPercent", Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) * 100 / 255)
                    put("mediaVolumePercent", audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1))
                }.toString()
            }
            "set_brightness" -> {
                check(Settings.System.canWrite(context)) { "請先在設定頁授予「修改系統設定」權限。" }
                check(Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS,
                    args.getValue("percent").jsonPrimitive.int * 255 / 100)) { "系統拒絕修改亮度。" }
                "已設定亮度；若開啟自動亮度，系統可能再調整。"
            }
            "set_volume" -> {
                val audio = context.getSystemService(AudioManager::class.java)
                audio.setStreamVolume(AudioManager.STREAM_MUSIC,
                    args.getValue("percent").jsonPrimitive.int * audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC) / 100, 0)
                "已設定媒體音量。"
            }
            "read_notifications" -> readNotifications()
            "read_network_state" -> buildJsonObject {
                put("internet", readInternetState(context).name)
                put("wifiEnabled", context.getSystemService(WifiManager::class.java).isWifiEnabled)
                put("airplaneMode", Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1)
            }.toString()
            "set_wifi_enabled", "set_airplane_mode" -> {
                val service = NetworkAccessibilityService.instance ?: error("請先在設定中啟用「我的手機 Agent：網路開關」無障礙服務。")
                service.setNetworkSwitch(name, args.getValue("enabled").jsonPrimitive.boolean)
            }
            else -> error("不允許此工具。")
        }
        requireUnlocked() // Never return newly-read private data after a lock transition.
        result
    }

    private fun readNotifications(): String {
        val service = AgentNotificationService.instance ?: error("請先授予通知存取權限。")
        return JsonArray(service.activeNotifications.orEmpty().asSequence()
            .filter { it.packageName != context.packageName }
            .sortedByDescending(StatusBarNotification::getPostTime).take(30).map { note -> buildJsonObject {
                put("package", note.packageName)
                put("title", note.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty().take(300))
                put("text", (note.notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                    ?: note.notification.extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty().take(2000))
            } }.toList()).toString()
    }
}
