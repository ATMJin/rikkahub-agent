package tw.littleyi.mobileagent

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Closed capability list: neither a prompt nor saved settings can add a shell/MCP tool. */
object AgentPolicy {
    val direct = setOf("list_apps", "launch_app", "open_settings", "read_settings", "read_notifications", "read_network_state")
    val confirmed = setOf("set_brightness", "set_volume", "set_wifi_enabled", "set_airplane_mode")
    val tools = direct + confirmed
    val settingsPages = setOf("wifi", "airplane", "bluetooth", "display", "sound", "notifications", "general")

    fun requireUnlocked(deviceLocked: Boolean?, keyguardLocked: Boolean?) {
        check(deviceLocked == false && keyguardLocked == false) { "請先解鎖手機。" }
    }

    fun validate(name: String, args: JsonObject) {
        require(name in tools) { "精簡版不提供此操作：$name" }
        val fields = when (name) {
            "launch_app" -> setOf("package")
            "open_settings" -> setOf("page")
            "set_brightness", "set_volume" -> setOf("percent")
            "set_wifi_enabled", "set_airplane_mode" -> setOf("enabled")
            else -> emptySet()
        }
        require(args.keys == fields) { "工具參數不符。" }
        when (name) {
            "launch_app" -> require(args["package"]?.jsonPrimitive?.isString == true &&
                args["package"]!!.jsonPrimitive.content.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")))
            "open_settings" -> require(args["page"]?.jsonPrimitive?.isString == true &&
                args["page"]!!.jsonPrimitive.content in settingsPages)
            "set_brightness", "set_volume" -> require(args["percent"]?.jsonPrimitive?.let {
                !it.isString && it.intOrNull in 0..100
            } == true) { "百分比必須為 0 到 100 的整數。" }
            "set_wifi_enabled", "set_airplane_mode" -> require(args["enabled"]?.jsonPrimitive?.let {
                !it.isString && it.content in setOf("true", "false")
            } == true) { "enabled 必須是布林值。" }
        }
    }
}

/** Optional extension point only. This build does not load a TTS service or request a microphone. */
interface SpeechOutput {
    suspend fun speak(text: String)
}
