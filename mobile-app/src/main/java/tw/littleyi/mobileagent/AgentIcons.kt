package tw.littleyi.mobileagent

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

enum class AgentGlyph { SETTINGS, SEND, CAMERA, SUN, BELL, WIFI, SPARKLE, BACK, SHIELD, CHECK, STOP, CLOSE }

/** Small code-native line icons; no image, browser, icon-font or image-loading dependency. */
@Composable
fun AgentIcon(glyph: AgentGlyph, modifier: Modifier = Modifier, color: Color = LocalContentColor.current) {
    Canvas(modifier.size(20.dp)) {
        val stroke = Stroke(1.6f, cap = StrokeCap.Round)
        scale(size.minDimension / 24f, pivot = Offset.Zero) {
            fun line(x: Float, y: Float, x2: Float, y2: Float) = drawLine(color, Offset(x,y), Offset(x2,y2), 1.6f, StrokeCap.Round)
            fun path(block: Path.() -> Unit) = drawPath(Path().apply(block), color, style = stroke)
            when(glyph) {
                AgentGlyph.SETTINGS, AgentGlyph.SUN -> {
                    drawCircle(color, if(glyph == AgentGlyph.SUN) 4f else 4.8f, Offset(12f,12f), style=stroke)
                    repeat(8) { i ->
                        val angle = i * Math.PI / 4
                        val inner = if(glyph == AgentGlyph.SUN) 8f else 9f
                        line(12f + cos(angle).toFloat()*inner,12f + sin(angle).toFloat()*inner,
                            12f + cos(angle).toFloat()*11f,12f + sin(angle).toFloat()*11f)
                    }
                    if(glyph == AgentGlyph.SETTINGS) drawCircle(color, 8f, Offset(12f,12f), style=stroke)
                }
                AgentGlyph.SEND -> { line(12f,20f,12f,4f); line(5f,11f,12f,4f); line(12f,4f,19f,11f) }
                AgentGlyph.BACK -> { line(20f,12f,4f,12f); line(4f,12f,11f,5f); line(4f,12f,11f,19f) }
                AgentGlyph.CAMERA -> {
                    drawRoundRect(color, Offset(3f,7f), Size(18f,14f), CornerRadius(2f), style=stroke)
                    drawCircle(color, 4f, Offset(12f,14f), style=stroke)
                    path { moveTo(7f,7f);lineTo(9f,4f);lineTo(15f,4f);lineTo(17f,7f) }
                }
                AgentGlyph.WIFI -> {
                    path { moveTo(3f,9f);quadraticTo(12f,1f,21f,9f) }
                    path { moveTo(6f,12f);quadraticTo(12f,6f,18f,12f) }
                    path { moveTo(9f,15f);quadraticTo(12f,12f,15f,15f) }
                    drawCircle(color,1.4f,Offset(12f,19f))
                }
                AgentGlyph.BELL -> {
                    path { moveTo(6f,17f);lineTo(18f,17f);lineTo(16f,14f);lineTo(16f,10f);quadraticTo(16f,6f,12f,6f);quadraticTo(8f,6f,8f,10f);lineTo(8f,14f);close() }
                    line(12f,3f,12f,5f)
                    path { moveTo(10f,20f);quadraticTo(12f,22f,14f,20f) }
                }
                AgentGlyph.SPARKLE -> path { moveTo(12f,2f);lineTo(15f,9f);lineTo(22f,12f);lineTo(15f,15f);lineTo(12f,22f);lineTo(9f,15f);lineTo(2f,12f);lineTo(9f,9f);close() }
                AgentGlyph.SHIELD -> {
                    path { moveTo(12f,3f);lineTo(20f,6f);lineTo(20f,12f);quadraticTo(20f,18f,12f,22f);quadraticTo(4f,18f,4f,12f);lineTo(4f,6f);close() }
                    path { moveTo(8f,12f);lineTo(11f,15f);lineTo(16f,10f) }
                }
                AgentGlyph.CHECK -> path { moveTo(5f,12f);lineTo(10f,17f);lineTo(20f,7f) }
                AgentGlyph.STOP -> drawRoundRect(color, Offset(6f,6f), Size(12f,12f), CornerRadius(2f))
                AgentGlyph.CLOSE -> { line(6f,6f,18f,18f);line(18f,6f,6f,18f) }
            }
        }
    }
}
