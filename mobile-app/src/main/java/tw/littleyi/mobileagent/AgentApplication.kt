package tw.littleyi.mobileagent

import android.app.Application

class AgentApplication : Application() {
    val agent by lazy { AgentController(this) }
}
