package tw.littleyi.mobileagent

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat

/** One conversation entry point. Network operations remain Agent tools, not a control panel. */
class MainActivity : ComponentActivity() {
    private val agent get() = (application as AgentApplication).agent
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
        window.decorView.filterTouchesWhenObscured = true
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            AgentTheme {
                AgentApp(
                    agent = agent,
                    initiallyOpenSettings = intent?.data?.host == "codex",
                    importModel = { importFile.launch(arrayOf("*/*")) },
                    openPermission = { permission ->
                        val destination = when(permission) {
                            AgentPermission.NOTIFICATIONS -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            AgentPermission.ACCESSIBILITY -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            AgentPermission.BRIGHTNESS -> Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName"))
                        }
                        runCatching { startActivity(destination) }.onFailure { notice(it.message.orEmpty()) }
                    },
                )
            }
        }
    }

    override fun onPause() { agent.rejectPendingOnBackground(); super.onPause() }
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if(event.flags and (MotionEvent.FLAG_WINDOW_IS_OBSCURED or MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED) != 0) return false
        return super.dispatchTouchEvent(event)
    }
    private fun notice(message: String) { Toast.makeText(this, message.take(500), Toast.LENGTH_LONG).show() }
}
