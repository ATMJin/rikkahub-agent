package me.rerere.rikkahub.data.ai.tools

import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Mobile-agent policy: ordinary app launches are direct; arbitrary activities and
 * sensitive mutations require fresh confirmation even when a standing grant exists.
 */
class ToolApprovalDefaultsTest {

    @Test
    fun `arbitrary activity launches always require fresh approval`() {
        assertTrue(ToolApprovalDefaults.requiresApproval("launch_activity"))
        assertFalse(ToolApprovalDefaults.allowsAlwaysAllow("launch_activity"))
    }

    @Test fun `ordinary app launch and notification queries do not need confirmation`() {
        for (tool in listOf("launch_app", "list_active_notifications", "get_battery_status")) {
            assertFalse(tool, ToolApprovalDefaults.requiresApproval(tool))
        }
    }

    @Test fun `sensitive actions ignore standing grants including yolo and headless grants`() {
        for (tool in ToolApprovalDefaults.MANDATORY_CONFIRMATION + ToolApprovalDefaults.NO_ALWAYS_ALLOW + "mcp__server__send") {
            assertTrue(tool, ToolApprovalDefaults.requiresApproval(tool))
            assertFalse(tool, ToolApprovalDefaults.canAutoApprove(tool, hasStandingGrant = true))
            assertFalse(tool, ToolApprovalDefaults.allowsAlwaysAllow(tool))
        }
    }

    @Test fun `ordinary grants still work for non-sensitive tools`() {
        assertTrue(ToolApprovalDefaults.canAutoApprove("vibrate", hasStandingGrant = true))
        assertFalse(ToolApprovalDefaults.canAutoApprove("vibrate", hasStandingGrant = false))
    }
}
