package tw.littleyi.mobileagent

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightAgentColors = lightColorScheme(
    primary=Color(0xFF345F49), onPrimary=Color.White,
    primaryContainer=Color(0xFFEEF3ED), onPrimaryContainer=Color(0xFF233127),
    background=Color(0xFFFAFBF9), onBackground=Color(0xFF233127),
    surface=Color.White, onSurface=Color(0xFF233127),
    surfaceVariant=Color(0xFFEEF3ED), onSurfaceVariant=Color(0xFF68766D),
    outlineVariant=Color(0xFFE0E7E1),
)
private val DarkAgentColors = darkColorScheme(
    primary=Color(0xFFA1CDB0), onPrimary=Color(0xFF162B1D),
    primaryContainer=Color(0xFF2A352D), onPrimaryContainer=Color(0xFFE4ECE6),
    background=Color(0xFF171C19), onBackground=Color(0xFFE4ECE6),
    surface=Color(0xFF242B26), onSurface=Color(0xFFE4ECE6),
    surfaceVariant=Color(0xFF2A352D), onSurfaceVariant=Color(0xFFA9B8AD),
    outlineVariant=Color(0xFF3B473E),
)

@Composable
fun AgentTheme(content: @Composable () -> Unit) {
    val dark=isSystemInDarkTheme()
    val view=LocalView.current
    SideEffect {
        (view.context as? Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window,view).apply {
                isAppearanceLightStatusBars=!dark
                isAppearanceLightNavigationBars=!dark
            }
        }
    }
    MaterialTheme(colorScheme=if(dark) DarkAgentColors else LightAgentColors, content=content)
}
