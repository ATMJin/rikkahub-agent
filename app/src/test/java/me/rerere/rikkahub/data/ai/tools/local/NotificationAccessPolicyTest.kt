package me.rerere.rikkahub.data.ai.tools.local

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationAccessPolicyTest {
    @Test fun `notification access requires positively unlocked device and keyguard`() {
        assertTrue(notificationAccessAllowed(deviceLocked = false, keyguardLocked = false))
        for (deviceLocked in listOf(true, false, null)) {
            for (keyguardLocked in listOf(true, false, null)) {
                if (deviceLocked == false && keyguardLocked == false) continue
                assertFalse(notificationAccessAllowed(deviceLocked, keyguardLocked))
            }
        }
    }
}
