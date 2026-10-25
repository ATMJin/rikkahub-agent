package tw.littleyi.mobileagent

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.provider.InternetState

enum class AgentEventKind { STATUS, ASSISTANT, TOOL, CANCELLED, ERROR }
data class AgentEvent(val kind: AgentEventKind, val text: String, val toolName: String? = null)
data class RouteLabel(val title: String, val detail: String)
data class ApprovalLabel(val title: String, val description: String)
data class TaskExample(val title: String, val prompt: String, val allowNotifications: Boolean = false)

/** Pure presentation logic: never dispatches a tool or invents an execution result. */
object AgentPresentation {
    fun remainingApprovalSeconds(createdAt: Long, now: Long): Int {
        val age = now - createdAt
        if(age < 0 || age >= 60_000) return 0
        return ((60_000 - age + 999) / 1000).toInt()
    }

    val examples = listOf(
        TaskExample("開啟相機", "幫我開啟相機 App。"),
        TaskExample("亮度調到 40%", "把螢幕亮度調到 40%。"),
        TaskExample("整理通知", "請查詢目前通知並以繁體中文摘要。", allowNotifications = true),
        TaskExample("幫我連回網路", "請檢查目前連線與系統設定，協助我恢復網路。"),
    )

    fun route(internet: InternetState, cloudModel: String, hasOfflineModel: Boolean): RouteLabel = when(internet) {
        InternetState.ONLINE -> RouteLabel("線上模式", cloudModel.ifBlank { "請先到設定選擇帳號與模型" })
        InternetState.OFFLINE -> RouteLabel("本機 E2B", if (hasOfflineModel) "已確認斷網，由本機模型處理" else "離線模型尚未匯入，請先到設定匯入")
        InternetState.UNVERIFIED -> RouteLabel("暫停推論", "網路狀態尚未確認，不擅自切換模型")
    }

    fun toolTitle(name: String): String = when(name) {
        "list_apps" -> "查詢可開啟的 App"
        "launch_app" -> "開啟 App"
        "open_settings" -> "開啟系統設定頁"
        "read_settings" -> "查詢亮度與音量"
        "read_notifications" -> "讀取本回合授權的通知"
        "read_network_state" -> "查詢連線與系統開關"
        "set_brightness" -> "調整螢幕亮度"
        "set_volume" -> "調整媒體音量"
        "set_wifi_enabled" -> "切換 Wi-Fi"
        "set_airplane_mode" -> "切換飛航模式"
        else -> name
    }

    fun approval(name: String, arguments: String): ApprovalLabel {
        val args = runCatching { Json.parseToJsonElement(arguments).jsonObject }.getOrNull()
        val percent = runCatching { args?.get("percent")?.jsonPrimitive?.takeUnless { it.isString }?.intOrNull }.getOrNull()
        val enabled = runCatching { args?.get("enabled")?.jsonPrimitive?.takeUnless { it.isString }?.booleanOrNull }.getOrNull()
        val title = when {
            name == "set_brightness" && percent != null -> "允許將亮度改為 $percent%？"
            name == "set_volume" && percent != null -> "允許將媒體音量改為 $percent%？"
            name == "set_wifi_enabled" && enabled != null -> "允許${if(enabled) "開啟" else "關閉"} Wi-Fi？"
            name == "set_airplane_mode" && enabled != null -> "允許${if(enabled) "開啟" else "關閉"}飛航模式？"
            else -> "允許這次操作？"
        }
        return ApprovalLabel(title, when(name) {
            "set_wifi_enabled", "set_airplane_mode" -> "會改變手機的網路連線狀態，只授權下列這一次操作。"
            else -> "只修改這項設定，不代表同意後續其他操作。"
        })
    }
}
