package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.*
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Pioneer-inspired brushed silver metallic DJ Jog Wheel with:
 * - Concentric machined aluminum grooves
 * - Outer neon LED ring (glows brighter when playing or scratching)
 * - Rotating platter marker & center artwork display
 * - Real angular touch-drag scratch & pitch-nudge gesture
 */
@Composable
fun MetallicJogWheel(
    angleDeg: Float,
    isPlaying: Boolean,
    accentColor: Color,
    artworkResName: String?,
    bpm: Float,
    deckTag: String,
    onTouchStart: () -> Unit,
    onDragAngle: (Float) -> Unit,
    onTouchEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isTouching by remember { mutableStateOf(false) }
    var lastTouchAngle by remember { mutableFloatStateOf(0f) }

    val artRes = when (artworkResName) {
        "deck_b" -> R.drawable.img_deck_b_art_1795192576972
        else -> R.drawable.img_deck_a_art_1795192577075
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .testTag("jog_wheel_$deckTag")
            .pointerInput(deckTag) {
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

            // 1. Outer dark chassis recess
            drawCircle(
                color = Color(0xFF060910),
                radius = radius,
                center = center
            )

            // 2. Outer dimpled jog bezel ring
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color(0xFF323B4B),
                        Color(0xFF7B879B),
                        Color(0xFF1F2633),
                        Color(0xFF93A1B8),
                        Color(0xFF2B3444),
                        Color(0xFF323B4B)
                    ),
                    center = center
                ),
                radius = radius * 0.96f,
                center = center
            )

            // Knurled grip notches around rim
            val notchCount = 48
            val notchR = radius * 0.93f
            for (i in 0 until notchCount) {
                val a = ((i * (360f / notchCount)) + angleDeg * 0.5f) * PI.toFloat() / 180f
                val x = center.x + cos(a) * notchR
                val y = center.y + sin(a) * notchR
                drawCircle(
                    color = Color(0xAA0A0E17),
                    radius = radius * 0.015f,
                    center = Offset(x, y)
                )
            }

            // 3. Glowing Neon LED Halo Ring
            val ledAlpha = if (isTouching) 1f else if (isPlaying) 0.9f else 0.45f
            drawCircle(
                color = accentColor.copy(alpha = ledAlpha * 0.35f),
                radius = radius * 0.87f,
                center = center,
                style = Stroke(width = radius * 0.08f)
            )
            drawCircle(
                color = accentColor.copy(alpha = ledAlpha),
                radius = radius * 0.86f,
                center = center,
                style = Stroke(width = radius * 0.03f)
            )

            // 4. Brushed Metallic Silver Platter (conical metallic sheen)
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color(0xFFD8E1EC),
                        Color(0xFF8A97AA),
                        Color(0xFFF1F5F9),
                        Color(0xFF64748B),
                        Color(0xFFE2E8F0),
                        Color(0xFF94A3B8),
                        Color(0xFFD8E1EC)
                    ),
                    center = center
                ),
                radius = radius * 0.82f,
                center = center
            )

            // 5. Fine concentric vinyl/aluminum machined grooves
            for (g in 4..10) {
                val gr = radius * (0.36f + g * 0.044f)
                drawCircle(
                    color = Color(0x28000000),
                    radius = gr,
                    center = center,
                    style = Stroke(width = 1.1f)
                )
            }

            // 6. Rotating Platter Cue Needle / Strobe Indicator
            val rad = (angleDeg - 90f) * PI.toFloat() / 180f
            val innerStart = Offset(
                center.x + cos(rad) * radius * 0.34f,
                center.y + sin(rad) * radius * 0.34f
            )
            val outerEnd = Offset(
                center.x + cos(rad) * radius * 0.80f,
                center.y + sin(rad) * radius * 0.80f
            )
            drawLine(
                color = accentColor,
                start = innerStart,
                end = outerEnd,
                strokeWidth = radius * 0.04f,
                cap = StrokeCap.Round
            )

            // 7. Inner dark OLED display bezel
            drawCircle(
                color = Color(0xFF0A0D16),
                radius = radius * 0.35f,
                center = center
            )
            drawCircle(
                color = accentColor.copy(alpha = 0.7f),
                radius = radius * 0.35f,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )
        }

        // Center rotating album art disc + BPM readout
        Box(
            modifier = Modifier
                .fillMaxSize(0.30f)
                .clip(CircleShape)
                .background(ChassisVoid),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = artRes),
                contentDescription = "Deck $deckTag platter art",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .rotate(angleDeg)
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x7005070D)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = String.format("%.0f", bpm),
                    color = TextPrimary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = JetBrainsMonoFontFamily
                )
            }
        }
    }
}
