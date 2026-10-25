package tw.littleyi.mobileagent

import me.rerere.ai.provider.InternetState
import org.junit.Assert.*
import org.junit.Test

class AgentPresentationTest {
    @Test fun `home examples send natural-language tasks not direct switches`() {
        assertEquals(4, AgentPresentation.examples.size)
        assertTrue(AgentPresentation.examples.all { it.prompt.isNotBlank() })
        assertTrue(AgentPresentation.examples.any { it.title == "開啟相機" })
        assertTrue(AgentPresentation.examples.any { it.title == "亮度調到 40%" })
    }
    @Test fun `only explicit notification example grants notification reading`() {
        val granted=AgentPresentation.examples.filter { it.allowNotifications }
        assertEquals(1, granted.size)
        assertEquals("整理通知", granted.single().title)
    }
    @Test fun `online label never silently selects a local model`() {
        val configured=AgentPresentation.route(InternetState.ONLINE,"chosen-model",true)
        assertEquals("線上模式",configured.title)
        assertEquals("chosen-model",configured.detail)
        val missing=AgentPresentation.route(InternetState.ONLINE,"",true)
        assertTrue(missing.detail.contains("設定"))
        assertFalse(missing.detail.contains("E2B"))
    }
    @Test fun `offline label reports missing model honestly`() {
        assertTrue(AgentPresentation.route(InternetState.OFFLINE,"cloud",false).detail.contains("尚未匯入"))
        assertTrue(AgentPresentation.route(InternetState.OFFLINE,"cloud",true).detail.contains("本機"))
    }
    @Test fun `unverified label does not pretend internet or local inference works`() {
        val label=AgentPresentation.route(InternetState.UNVERIFIED,"cloud",true)
        assertEquals("暫停推論",label.title)
        assertTrue(label.detail.contains("不擅自切換"))
    }
    @Test fun `confirmation labels preserve exact setting direction and numeric parameter`() {
        assertTrue(AgentPresentation.approval("set_brightness","{\"percent\":40}").title.contains("40%"))
        assertTrue(AgentPresentation.approval("set_volume","{\"percent\":14}").title.contains("14%"))
        assertTrue(AgentPresentation.approval("set_wifi_enabled","{\"enabled\":true}").title.contains("開啟"))
        assertTrue(AgentPresentation.approval("set_wifi_enabled","{\"enabled\":false}").title.contains("關閉"))
        assertTrue(AgentPresentation.approval("set_airplane_mode","{\"enabled\":false}").title.contains("關閉飛航"))
    }
    @Test fun `unknown or malformed actions use neutral title not an invented result`() {
        assertEquals("允許這次操作？",AgentPresentation.approval("unknown","{}").title)
        assertEquals("允許這次操作？",AgentPresentation.approval("set_brightness","invalid").title)
        assertEquals("允許這次操作？",AgentPresentation.approval("set_wifi_enabled","{\"enabled\":\"false\"}").title)
    }
    @Test fun `countdown matches the sixty second approval boundary and fails closed on invalid clock`() {
        assertEquals(60,AgentPresentation.remainingApprovalSeconds(1000,1000))
        assertEquals(1,AgentPresentation.remainingApprovalSeconds(1000,60_999))
        assertEquals(0,AgentPresentation.remainingApprovalSeconds(1000,61_000))
        assertEquals(0,AgentPresentation.remainingApprovalSeconds(1000,62_000))
        assertEquals(0,AgentPresentation.remainingApprovalSeconds(1000,999))
    }
    @Test fun `every allowed tool has a readable title`() {
        AgentPolicy.tools.forEach { assertNotEquals(it,AgentPresentation.toolTitle(it)) }
    }
    @Test fun `tool results and cancelled operations have distinct presentation kinds`() {
        assertNotEquals(AgentEventKind.TOOL,AgentEventKind.CANCELLED)
        assertNotEquals(AgentEventKind.ASSISTANT,AgentEventKind.STATUS)
    }
}
