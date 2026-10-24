package tw.littleyi.mobileagent

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch

/** Small native UI: no workspace/web browser/terminal/plugin screens or remote control server. */
class MainActivity : ComponentActivity() {
    private val agent get() = (application as AgentApplication).agent
    private lateinit var log: TextView
    private lateinit var status: TextView
    private lateinit var approvalBox: LinearLayout
    private lateinit var approvalText: TextView
    private lateinit var provider: CheckBox
    private lateinit var endpoint: EditText
    private lateinit var key: EditText
    private lateinit var model: EditText
    private lateinit var prompt: EditText
    private val idleControls = mutableListOf<View>()
    private var approvalId: Long? = null
    private val importFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            val name = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else ""
            }.orEmpty()
            agent.importModel(uri, name)
        }.onFailure { notice(it.message.orEmpty()) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val scroll = ScrollView(this)
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24) }
        scroll.addView(column)
        setContentView(scroll)
        fun text(value: String) = TextView(this).apply { text = value; setPadding(0, 8, 0, 8); column.addView(this) }
        fun field(hintText: String, value: String, secret: Boolean = false) = EditText(this).apply {
            hint = hintText; setText(value)
            inputType = if(secret) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_CLASS_TEXT
            column.addView(this); idleControls += this
        }
        fun button(title: String, idleOnly: Boolean = true, action: () -> Unit) = Button(this).apply {
            text = title
            filterTouchesWhenObscured = true
            setOnClickListener { runCatching(action).onFailure { notice(it.message.orEmpty()) } }
            column.addView(this)
            if (idleOnly) idleControls += this
        }
        text("我的手機 Agent · 精簡開發版").textSize = 22f
        status = text("尚未登入。")
        approvalBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE; column.addView(this) }
        approvalText = TextView(this).apply { approvalBox.addView(this) }
        listOf("僅執行這一次" to true, "拒絕" to false).forEach { (title, accepted) ->
            approvalBox.addView(Button(this).apply {
                text = title; filterTouchesWhenObscured = true
                setOnClickListener { approvalId?.let { agent.answerApproval(it, accepted) } }
            })
        }

        val cfg = agent.configuration
        provider = CheckBox(this).apply { text = "使用上游 Codex 登入（需實測帳號可用性）"; isChecked = cfg.useCodex; column.addView(this); idleControls += this }
        endpoint = field("HTTPS API Base URL", cfg.endpoint)
        key = field("API Key（僅 API 模式需要；Keystore 加密保存）", cfg.apiKey, true)
        model = field("線上 Model ID（明確選定，不自動換模型）", cfg.model)
        fun save() = agent.save(Configuration(provider.isChecked, endpoint.text.toString().trim(), key.text.toString(), model.text.toString().trim()))
        button("儲存線上設定") { save() }
        button("Codex 登入") { save(); agent.login.startLogin() }
        button("取得模型清單並選擇") {
            save()
            lifecycleScope.launch {
                runCatching { agent.listModels() }.onSuccess { models ->
                    AlertDialog.Builder(this@MainActivity).setTitle("選擇線上模型")
                        .setItems(models.map { it.displayName.ifBlank { it.modelId } }.toTypedArray()) { _, index ->
                            model.setText(models[index].modelId); runCatching { save() }.onFailure { notice(it.message.orEmpty()) }
                        }.setNegativeButton("取消", null).show()
                }.onFailure { notice(it.message.orEmpty()) }
            }
        }
        button("登出／移除此 App 儲存的 Codex 帳號") {
            lifecycleScope.launch {
                agent.accounts.accounts.value.toList().forEach { agent.accounts.delete(it.id) }
                notice("已刪除此 App 的本地登入資料；未確認遠端撤銷。")
            }
        }
        button("匯入已下載的 Gemma 4 E2B .litertlm") { importFile.launch(arrayOf("*/*")) }
        text("通知本文僅在查詢工具被執行時讀取，可傳給選定線上模型摘要；不保存聊天／通知歷史。所有讀取與推論要求先解鎖。")
        button("授予通知存取權限") { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        button("授予修改亮度權限") { startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName"))) }
        button("啟用受限網路無障礙服務") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        text("本機網路修復：不需模型或 API，也要逐次確認。Wi-Fi SSID 選擇與密碼由系統設定頁手動輸入。")
        button("開啟 Wi-Fi") { agent.networkRecovery("set_wifi_enabled", true) }
        button("關閉 Wi-Fi") { agent.networkRecovery("set_wifi_enabled", false) }
        button("關閉飛航模式") { agent.networkRecovery("set_airplane_mode", false) }
        button("開啟飛航模式") { agent.networkRecovery("set_airplane_mode", true) }
        val allowNotifications = CheckBox(this).apply {
            text = "本次指令允許讀取通知本文，交給已選模型理解／摘要"
            column.addView(this); idleControls += this
        }
        prompt = field("輸入指令，例如：查通知、開啟 Wi-Fi、把媒體音量設為 30%", "")
        prompt.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        button("執行文字指令") {
            val grant = allowNotifications.isChecked
            allowNotifications.isChecked = false
            agent.send(prompt.text.toString(), grant)
        }
        button("查詢目前通知並摘要") { agent.send("請查詢目前通知並以繁體中文摘要。", allowNotifications = true) }
        button("停止", idleOnly = false) { agent.cancel() }
        log = text("").apply { setTextIsSelectable(true) }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { agent.state.collect { state ->
                    log.text = state.text
                    idleControls.forEach { it.isEnabled = !state.busy }
                    approvalId = state.approval?.id
                    approvalBox.visibility = if(state.approval == null) View.GONE else View.VISIBLE
                    approvalText.text = state.approval?.let { "是否執行？（60 秒內有效）\n${it.name}\n${it.arguments}" }.orEmpty()
                    if (state.approval != null) scroll.smoothScrollTo(0, 0)
                } }
                launch { agent.accounts.accounts.collect { accounts -> status.text = "Codex 帳號：${accounts.joinToString { it.email.ifBlank { it.name } }.ifBlank { "尚未登入" }}" } }
                launch { agent.login.status.collect { state ->
                    if (state is me.rerere.rikkahub.data.codex.CodexOAuthStatus.Error) notice("登入失敗：${state.message}")
                    if (state is me.rerere.rikkahub.data.codex.CodexOAuthStatus.Success) notice("已登入；請取得模型清單並實測。")
                } }
            }
        }
    }

    override fun onPause() { agent.rejectPendingOnBackground(); super.onPause() }
    private fun notice(message: String) { Toast.makeText(this, message.take(500), Toast.LENGTH_LONG).show() }
}
