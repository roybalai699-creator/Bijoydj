package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.JogMode
import com.example.ui.theme.*
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Pioneer-inspired brushed silver metallic DJ Jog Wheel matching the BIJOY DJ reference:
 * - Concentric machined aluminum platter with radial brushed metallic sheen
 * - Glowing Blue RGB LED outer halo ring
 * - Rotating black center DJ emblem disc ("DJ" + red strobe arc)
 * - Real angular touch-drag scratch (Vinyl), pitch nudge (CDJ), and rapid seek (Search)
 */
@Composable
fun MetallicJogWheel(
    angleDeg: Float,
    isPlaying: Boolean,
    accentColor: Color,
    artworkResName: String?,
    bpm: Float,
    deckTag: String,
    jogMode: JogMode = JogMode.VINYL,
    onTouchStart: () -> Unit,
    onDragAngle: (Float) -> Unit,
    onTouchEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isTouching by remember { mutableStateOf(false) }
    var lastTouchAngle by remember { mutableFloatStateOf(0f) }

    // Reference image uses vibrant Blue/Cyan RGB ring lighting on both metallic jog wheels
    val rgbRingColor = if (isTouching) DeckACyan else Color(0xFF0091FF)

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .testTag("jog_wheel_$deckTag")
            .pointerInput(deckTag, jogMode) {
                detectDragGestures(
                    onDragStart = { offset ->
                        isTouching = true
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        lastTouchAngle = (atan2(offset.y - cy, offset.x - cx) * 180f / PI.toFloat())
                        onTouchStart()
                    },
                    onDragEnd = {
                        isTouching = false
                        onTouchEnd()
                    },
                    onDragCancel = {
                        isTouching = false
                        onTouchEnd()
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val curr = (atan2(change.position.y - cy, change.position.x - cx) * 180f / PI.toFloat())
                        var delta = curr - lastTouchAngle
                        if (delta > 180f) delta -= 360f
                        if (delta < -180f) delta += 360f
                        lastTouchAngle = curr
                        onDragAngle(delta)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f
            val center = Offset(size.width / 2f, size.height / 2f)

            // 1. Dark recessed chassis housing
            drawCircle(
                color = Color(0xFF05070D),
                radius = radius,
                center = center
            )

            // 2. Machined dark outer grip bezel with knurled notches
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color(0xFF28303E),
                        Color(0xFF5B677B),
                        Color(0xFF1A202C),
                        Color(0xFF6B778D),
                        Color(0xFF232A37),
                        Color(0xFF28303E)
                    ),
                    center = center
                ),
                radius = radius * 0.97f,
                center = center
            )

            val notchCount = 44
            val notchR = radius * 0.935f
            for (i in 0 until notchCount) {
                val a = ((i * (360f / notchCount)) + angleDeg * 0.4f) * PI.toFloat() / 180f
                drawCircle(
                    color = Color(0xBB070A12),
                    radius = radius * 0.014f,
                    center = Offset(center.x + cos(a) * notchR, center.y + sin(a) * notchR)
                )
            }

            // 3. Glowing Blue RGB Outer Halo Ring
            val ledAlpha = if (isTouching) 1f else if (isPlaying) 0.92f else 0.58f
            drawCircle(
                color = rgbRingColor.copy(alpha = ledAlpha * 0.38f),
                radius = radius * 0.875f,
                center = center,
                style = Stroke(width = radius * 0.085f)
            )
            drawCircle(
                color = rgbRingColor.copy(alpha = ledAlpha),
                radius = radius * 0.87f,
                center = center,
                style = Stroke(width = radius * 0.032f)
            )

            // 4. Brushed Metallic Silver Platter (conical metallic reflections)
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color(0xFFE2E8F0),
                        Color(0xFF7D8A9E),
                        Color(0xFFF8FAFC),
                        Color(0xFF5D6A7D),
                        Color(0xFFCBD5E1),
                        Color(0xFF94A3B8),
                        Color(0xFFE2E8F0)
                    ),
                    center = center
                ),
                radius = radius * 0.835f,
                center = center
            )

            // 5. Fine concentric aluminum platter grooves
            for (g in 3..11) {
                val gr = radius * (0.34f + g * 0.043f)
                drawCircle(
                    color = Color(0x28000000),
                    radius = gr,
                    center = center,
                    style = Stroke(width = 1.0f)
                )
            }

            // 6. Rotating Strobe Position Marker Line
            val rad = (angleDeg - 90f) * PI.toFloat() / 180f
            drawLine(
                color = accentColor,
                start = Offset(center.x + cos(rad) * radius * 0.34f, center.y + sin(rad) * radius * 0.34f),
                end = Offset(center.x + cos(rad) * radius * 0.81f, center.y + sin(rad) * radius * 0.81f),
                strokeWidth = radius * 0.036f,
                cap = StrokeCap.Round
            )

            // 7. Inner dark platter ring
            drawCircle(
                color = Color(0xFF0A0D16),
                radius = radius * 0.34f,
                center = center
            )
            drawCircle(
                color = Color(0xFF94A3B8).copy(alpha = 0.7f),
                radius = radius * 0.34f,
                center = center,
                style = Stroke(width = 1.8.dp.toPx())
            )
            // Signature red cue arc inside center disc (matches reference jog wheel center)
            drawArc(
                color = DeckBRed,
                startAngle = angleDeg - 60f,
                sweepAngle = 110f,
                useCenter = false,
                topLeft = Offset(center.x - radius * 0.25f, center.y - radius * 0.25f),
                size = Size(radius * 0.50f, radius * 0.50f),
                style = Stroke(width = radius * 0.03f, cap = StrokeCap.Round)
            )
        }

        // Rotating Center DJ Emblem Disc ("DJ")
        Box(
            modifier = Modifier
                .fillMaxSize(0.28f)
                .clip(CircleShape)
                .background(Color(0xFF070A12))
                .border(1.2.dp, Color(0xFF475569), CircleShape)
                .rotate(angleDeg * 0.15f),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "DJ",
                    color = Color.White,
                    fontFamily = OrbitronFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 11.sp
                )
                Text(
                    text = if (isTouching) jogMode.shortLabel else String.format("%.0f", bpm),
                    color = if (isTouching) DeckACyan else TextSecondary,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 6.5.sp
                )
            }
        }
    }
}
