package tw.littleyi.mobileagent

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.*
import org.junit.Test

class AgentPolicyTest {
    private fun args(value: String) = Json.parseToJsonElement(value) as JsonObject
    @Test fun `product has only ten closed capabilities`() {
        assertEquals(10, AgentPolicy.tools.size)
        for (tool in listOf("send_sms", "delete_file", "tap", "shell", "mcp__server__tool", "workflow_run")) {
            assertThrows(IllegalArgumentException::class.java) { AgentPolicy.validate(tool, args("{}")) }
        }
    }
    @Test fun `network mutations require fresh confirmation`() {
        assertTrue(AgentPolicy.confirmed.containsAll(listOf("set_wifi_enabled", "set_airplane_mode")))
        assertFalse("read_network_state" in AgentPolicy.confirmed)
        assertFalse("launch_app" in AgentPolicy.confirmed)
    }
    @Test fun `all unknown lock states fail closed`() {
        AgentPolicy.requireUnlocked(false, false)
        for (device in listOf(true, false, null)) for (keyguard in listOf(true, false, null)) {
            if (device == false && keyguard == false) continue
            assertThrows(IllegalStateException::class.java) { AgentPolicy.requireUnlocked(device, keyguard) }
        }
    }
    @Test fun `percent types and boundaries are strict`() {
        for (tool in listOf("set_brightness", "set_volume")) {
            for (value in listOf("0", "50", "100")) AgentPolicy.validate(tool, args("{\"percent\":$value}"))
            for (value in listOf("-1", "101", "0.5", "null", "\"50\"")) {
                assertThrows(IllegalArgumentException::class.java) { AgentPolicy.validate(tool, args("{\"percent\":$value}")) }
            }
        }
    }
    @Test fun `network switches accept only actual booleans`() {
        for (tool in listOf("set_wifi_enabled", "set_airplane_mode")) {
            AgentPolicy.validate(tool, args("{\"enabled\":true}"))
            AgentPolicy.validate(tool, args("{\"enabled\":false}"))
            for (value in listOf("1", "0", "null", "\"true\"")) {
                assertThrows(IllegalArgumentException::class.java) { AgentPolicy.validate(tool, args("{\"enabled\":$value}")) }
            }
        }
    }
    @Test fun `extra or missing arguments cannot smuggle a side effect`() {
        for ((tool, value) in listOf("list_apps" to "{\"command\":\"rm\"}", "launch_app" to "{}",
            "launch_app" to "{\"package\":\"https://example.com\"}", "open_settings" to "{\"page\":\"arbitrary_component\"}",
            "set_wifi_enabled" to "{\"enabled\":true,\"tap\":true}")) {
            assertThrows(IllegalArgumentException::class.java) { AgentPolicy.validate(tool, args(value)) }
        }
    }
    @Test fun `wifi and airplane settings pages are available offline`() {
        AgentPolicy.validate("open_settings", args("{\"page\":\"wifi\"}"))
        AgentPolicy.validate("open_settings", args("{\"page\":\"airplane\"}"))
        AgentPolicy.validate("launch_app", args("{\"package\":\"com.android.settings\"}"))
    }
    @Test fun `network labels require exact known switch text`() {
        assertTrue(NetworkSwitchPolicy.matches("set_wifi_enabled", "使用 Wi-Fi"))
        assertTrue(NetworkSwitchPolicy.matches("set_airplane_mode", "飛航模式"))
        for (label in listOf("請開啟 Wi-Fi", "Bluetooth", "Wi-Fi 密碼", "Airplane mode settings", "飛航模式說明")) {
            assertFalse(NetworkSwitchPolicy.matches("set_wifi_enabled", label))
            assertFalse(NetworkSwitchPolicy.matches("set_airplane_mode", label))
        }
        assertFalse(NetworkSwitchPolicy.matches("unknown", "Wi-Fi"))
    }
}
