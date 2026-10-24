package tw.littleyi.mobileagent

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.net.wifi.WifiManager
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/** Deliberately not a general phone-control API: only two confirmed, idempotent switches. */
class NetworkAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() { instance = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* no autonomous actions */ }
    override fun onInterrupt() { }
    override fun onDestroy() { if (instance === this) instance = null; super.onDestroy() }

    private fun current(name: String): Boolean = when (name) {
        "set_wifi_enabled" -> getSystemService(WifiManager::class.java).isWifiEnabled
        "set_airplane_mode" -> Settings.Global.getInt(contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
        else -> error("不支援此開關。")
    }

    suspend fun setNetworkSwitch(name: String, enabled: Boolean): String {
        require(name in setOf("set_wifi_enabled", "set_airplane_mode"))
        val phone = PhoneTools(this)
        phone.requireUnlocked()
        if (current(name) == enabled) return "開關已符合要求，沒有點擊。"
        val action = if (name == "set_wifi_enabled") Settings.ACTION_WIFI_SETTINGS else Settings.ACTION_AIRPLANE_MODE_SETTINGS
        // Never hand a model-provided URI/component to Accessibility or another package.
        startActivity(Intent(action).setPackage("com.android.settings").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val target = withTimeoutOrNull(5_000) {
            var candidate: AccessibilityNodeInfo? = null
            while (candidate == null) {
                phone.requireUnlocked()
                val root = rootInActiveWindow
                if (root?.packageName?.toString() == "com.android.settings") {
                    val matches = mutableListOf<AccessibilityNodeInfo>()
                    findSwitches(root, name, matches, 0)
                    check(matches.size <= 1) { "找到多個相似開關，停止操作。" }
                    candidate = matches.singleOrNull()
                }
                if (candidate == null) delay(150)
            }
            candidate
        } ?: error("無法安全辨識系統開關；已開啟設定頁，請手動操作。沒有自動點擊。")
        phone.requireUnlocked()
        if (current(name) == enabled) return "系統狀態已符合要求，沒有點擊。"
        check(target.isChecked != enabled) { "畫面狀態與系統狀態不同，停止操作。" }
        var clickable = target
        repeat(2) {
            if (!clickable.isClickable) clickable = clickable.parent ?: clickable
        }
        check(clickable.isClickable && clickable.isEnabled && clickable.packageName?.toString() == "com.android.settings") {
            "開關無法安全操作，請手動處理。"
        }
        check(rootInActiveWindow?.packageName?.toString() == "com.android.settings" && clickable.refresh()) {
            "畫面已改變，停止操作。"
        }
        // At most one click. Never retry a side effect when an OEM dialog/state is unknown.
        check(clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) { "系統拒絕點擊。" }
        val verified = withTimeoutOrNull(5_000) {
            while (current(name) != enabled) { phone.requireUnlocked(); delay(150) }
            true
        } == true
        return if (verified) "已確認 $name = $enabled；Internet 是否恢復需重新驗證。"
        else "已點擊一次，但未確認狀態；請檢查系統畫面。沒有再次點擊或代你確認其他對話框。"
    }

    private fun findSwitches(node: AccessibilityNodeInfo, name: String, output: MutableList<AccessibilityNodeInfo>, depth: Int) {
        if (depth > 20 || output.size > 1) return
        if (node.isCheckable && node.isEnabled && node.isVisibleToUser &&
            node.className?.toString()?.contains("Switch", ignoreCase = true) == true) {
            val labels = mutableListOf<String>()
            var row: AccessibilityNodeInfo? = node
            repeat(2) {
                row?.let { collectLabels(it, labels, 0) }
                row = row?.parent
            }
            if (labels.any { NetworkSwitchPolicy.matches(name, it) }) output += node
        }
        for (i in 0 until node.childCount.coerceAtMost(100)) node.getChild(i)?.let { findSwitches(it, name, output, depth + 1) }
    }

    private fun collectLabels(node: AccessibilityNodeInfo, labels: MutableList<String>, depth: Int) {
        if (depth > 2) return
        node.text?.toString()?.let(labels::add)
        node.contentDescription?.toString()?.let(labels::add)
        for (i in 0 until node.childCount.coerceAtMost(20)) node.getChild(i)?.let { collectLabels(it, labels, depth + 1) }
    }

    companion object { @Volatile var instance: NetworkAccessibilityService? = null; private set }
}

object NetworkSwitchPolicy {
    fun matches(name: String, label: String): Boolean {
        val normalized = label.trim().lowercase().replace('‑', '-').replace('－', '-')
        return normalized in when(name) {
            "set_wifi_enabled" -> setOf("wi-fi", "wifi", "wlan", "使用 wi-fi", "使用wifi", "use wi-fi", "開啟 wi-fi", "使用 wlan")
            "set_airplane_mode" -> setOf("飛航模式", "飛行模式", "飞行模式", "airplane mode", "aeroplane mode")
            else -> emptySet()
        }
    }
}
