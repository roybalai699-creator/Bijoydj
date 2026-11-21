package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Realistic Pioneer-style metallic rotary knob (EQ, Gain, Filter, FX).
 * Drag vertically to adjust, double-tap to snap back to neutral center.
 */
@Composable
fun RotaryHardwareKnob(
    label: String,
    value: Float, // normalized -1f..1f (0f = 12 o'clock neutral)
    accentColor: Color = DeckACyan,
    knobSize: Dp = 32.dp,
    testTagName: String = "knob_$label",
    onValueChange: (Float) -> Unit,
    onReset: () -> Unit = { onValueChange(0f) }
) {
    val currentValue by androidx.compose.runtime.rememberUpdatedState(value)
    val currentOnValueChange by androidx.compose.runtime.rememberUpdatedState(onValueChange)
    val currentOnReset by androidx.compose.runtime.rememberUpdatedState(onReset)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .widthIn(min = knobSize + 8.dp)
            .testTag(testTagName)
            .pointerInput("${testTagName}_tap") {
                detectTapGestures(onDoubleTap = { currentOnReset() })
            }
            .pointerInput("${testTagName}_drag") {
                var activeValue = currentValue
                detectDragGestures(
                    onDragStart = { activeValue = currentValue },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val primaryDelta = if (kotlin.math.abs(dragAmount.x) >= kotlin.math.abs(dragAmount.y)) {
                            dragAmount.x
                        } else {
                            -dragAmount.y
                        }
                        val next = (activeValue + (primaryDelta / 56f)).coerceIn(-1f, 1f)
                        activeValue = next
                        currentOnValueChange(next)
                    }
                )
            }
    ) {
        Box(modifier = Modifier.size(knobSize)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val r = size.minDimension / 2f
                val c = Offset(size.width / 2f, size.height / 2f)

                // Outer LED arc track (-135 deg to +135 deg)
                drawArc(
                    color = Color(0xFF1E273B),
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    topLeft = Offset(2f, 2f),
                    size = Size(size.width - 4f, size.height - 4f),
                    style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round)
                )

                // Active LED arc from 12 o'clock (-90 deg)
                val sweep = value * 135f
                if (kotlin.math.abs(sweep) > 1f) {
                    drawArc(
                        color = accentColor,
                        startAngle = -90f,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = Offset(2f, 2f),
                        size = Size(size.width - 4f, size.height - 4f),
                        style = Stroke(width = 3.0.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // Metallic knob body
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF475569), Color(0xFF182030), Color(0xFF0A0D14)),
                        center = Offset(c.x - r * 0.2f, c.y - r * 0.2f),
                        radius = r * 0.88f
                    ),
                    radius = r * 0.74f,
                    center = c
                )
                drawCircle(
                    color = if (kotlin.math.abs(value) > 0.05f) accentColor.copy(alpha = 0.75f) else Color(0xFF64748B),
                    radius = r * 0.74f,
                    center = c,
                    style = Stroke(width = 1.1.dp.toPx())
                )

                // White/Accent pointer line
                val angleRad = ((value * 135f) - 90f) * PI.toFloat() / 180f
                drawLine(
                    color = if (kotlin.math.abs(value) > 0.05f) accentColor else TextPrimary,
                    start = Offset(c.x + cos(angleRad) * r * 0.18f, c.y + sin(angleRad) * r * 0.18f),
                    end = Offset(c.x + cos(angleRad) * r * 0.68f, c.y + sin(angleRad) * r * 0.68f),
                    strokeWidth = 2.4.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
        Text(
            text = label,
            color = if (kotlin.math.abs(value) > 0.05f) accentColor else TextSecondary,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = JetBrainsMonoFontFamily,
            maxLines = 1
        )
    }
}

/**
 * Hardware vertical fader (Channel Volume or Pitch/Tempo).
 * value: 0f (bottom) .. 1f (top)
 */
@Composable
fun VerticalHardwareFader(
    value: Float,
    accentColor: Color,
    testTagName: String,
    centerDetent: Boolean = false,
    modifier: Modifier = Modifier,
    onValueChange: (Float) -> Unit
) {
    Box(
        modifier = modifier
            .width(30.dp)
            .testTag(testTagName)
            .pointerInput(testTagName) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val norm = 1f - (change.position.y / size.height.toFloat()).coerceIn(0f, 1f)
                    onValueChange(norm)
                }
            }
            .pointerInput(testTagName) {
                detectTapGestures(
                    onTap = { offset ->
                        val norm = 1f - (offset.y / size.height.toFloat()).coerceIn(0f, 1f)
                        onValueChange(norm)
                    },
                    onDoubleTap = {
                        if (centerDetent) onValueChange(0.5f) else onValueChange(0.85f)
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val padY = 10.dp.toPx()
            val trackH = (size.height - padY * 2f).coerceAtLeast(10f)

            // Tick marks
            for (i in 0..8) {
                val y = padY + (trackH * i / 8f)
                val tickW = if (i == 4 && centerDetent) 10.dp.toPx() else 6.dp.toPx()
                val tickColor = if (i == 4 && centerDetent) accentColor else Color(0xFF475569)
                drawLine(tickColor, Offset(cx - tickW, y), Offset(cx - 3.dp.toPx(), y), 1.dp.toPx())
                drawLine(tickColor, Offset(cx + 3.dp.toPx(), y), Offset(cx + tickW, y), 1.dp.toPx())
            }

            // Recessed slot
            drawRoundRect(
                color = Color(0xFF030508),
                topLeft = Offset(cx - 2.5.dp.toPx(), padY),
                size = Size(5.dp.toPx(), trackH),
                cornerRadius = CornerRadius(3.dp.toPx())
            )

            // Glowing fill
            val thumbY = padY + (1f - value.coerceIn(0f, 1f)) * trackH
            drawLine(
                color = accentColor.copy(alpha = 0.7f),
                start = Offset(cx, size.height - padY),
                end = Offset(cx, thumbY),
                strokeWidth = 2.5.dp.toPx()
            )

            // Metallic Fader Cap
            val capW = 22.dp.toPx()
            val capH = 12.dp.toPx()
            drawRoundRect(
                brush = Brush.verticalGradient(
                    listOf(Color(0xFFCBD5E1), Color(0xFF475569), Color(0xFF1E293B))
                ),
                topLeft = Offset(cx - capW / 2f, thumbY - capH / 2f),
                size = Size(capW, capH),
                cornerRadius = CornerRadius(2.5.dp.toPx())
            )
            // Center groove line on cap
            drawLine(
                color = Color.White,
                start = Offset(cx - capW * 0.38f, thumbY),
                end = Offset(cx + capW * 0.38f, thumbY),
                strokeWidth = 1.6.dp.toPx()
            )
        }
    }
}

/**
 * Horizontal Pioneer-style hardware Crossfader (-1f Deck A .. 0f Center .. +1f Deck B).
 */
@Composable
fun HardwareCrossfader(
    value: Float,
    modifier: Modifier = Modifier,
    onValueChange: (Float) -> Unit
) {
    Box(
        modifier = modifier
            .heightIn(min = 24.dp, max = 34.dp)
            .testTag("hardware_crossfader")
            .pointerInput("crossfader_drag") {
                detectDragGestures { change, _ ->
                    change.consume()
                    val norm = ((change.position.x / size.width.toFloat()).coerceIn(0f, 1f) * 2f) - 1f
                    onValueChange(norm)
                }
            }
            .pointerInput("crossfader_tap") {
                detectTapGestures(
                    onTap = { offset ->
                        val norm = ((offset.x / size.width.toFloat()).coerceIn(0f, 1f) * 2f) - 1f
                        onValueChange(norm)
                    },
                    onDoubleTap = { onValueChange(0f) }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cy = size.height / 2f
            val padX = 14.dp.toPx()
            val trackW = (size.width - padX * 2f).coerceAtLeast(20f)

            // Neon gradient rails (Deck A cyan -> Deck B red/orange)
            drawRoundRect(
                brush = Brush.horizontalGradient(listOf(DeckACyan, Color(0xFF1E273B), DeckBRed)),
                topLeft = Offset(padX, cy - 3.dp.toPx()),
                size = Size(trackW, 6.dp.toPx()),
                cornerRadius = CornerRadius(3.dp.toPx())
            )

            // Tick marks
            for (i in 0..8) {
                val x = padX + trackW * (i / 8f)
                val h = if (i == 4) 10.dp.toPx() else 5.dp.toPx()
                drawLine(
                    color = if (i == 4) Color.White else MetalSilverDark,
                    start = Offset(x, cy - h),
                    end = Offset(x, cy + h),
                    strokeWidth = if (i == 4) 1.5.dp.toPx() else 1.dp.toPx()
                )
            }

            // Metallic Crossfader Handle
            val norm = (value.coerceIn(-1f, 1f) + 1f) * 0.5f
            val thumbX = padX + norm * trackW
            val capW = 16.dp.toPx()
            val capH = 26.dp.toPx()
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    listOf(Color(0xFF94A3B8), Color(0xFFF8FAFC), Color(0xFF64748B))
                ),
                topLeft = Offset(thumbX - capW / 2f, cy - capH / 2f),
                size = Size(capW, capH),
                cornerRadius = CornerRadius(3.dp.toPx())
            )
            drawLine(
                color = ChassisVoid,
                start = Offset(thumbX, cy - capH * 0.4f),
                end = Offset(thumbX, cy + capH * 0.4f),
                strokeWidth = 2.dp.toPx()
            )
        }
    }
}

/**
 * Vertical LED Audio Level Meter (Green -> Yellow -> Red).
 */
@Composable
fun VerticalVuMeter(
    level: Float,
    segments: Int = 12,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.width(6.dp)) {
        val segH = size.height / segments
        val litCount = (level.coerceIn(0f, 1f) * segments).toInt()
        for (i in 0 until segments) {
            val fromBottom = segments - 1 - i
            val isLit = fromBottom < litCount
            val segColor = when {
                fromBottom >= segments - 2 -> LedRed
                fromBottom >= segments - 5 -> LedAmber
                else -> LedGreen
            }
            drawRoundRect(
                color = if (isLit) segColor else Color(0xFF1A2233),
                topLeft = Offset(0f, i * segH + 1f),
                size = Size(size.width, (segH - 2f).coerceAtLeast(1.5f)),
                cornerRadius = CornerRadius(1.5f)
            )
        }
    }
}

/**
 * Top Header RGB Real-Time Audio Spectrum Visualizer (16 glowing multi-color LED columns).
 */
@Composable
fun RgbSpectrumVisualizer(
    bands: List<Float>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val count = bands.size.coerceAtLeast(16)
        val colW = size.width / count
        val gap = 2.dp.toPx()
        val rows = 8
        val rowH = size.height / rows

        for (i in 0 until count) {
            val mag = bands.getOrElse(i) { 0f }.coerceIn(0f, 1f)
            val litRows = (mag * rows).toInt().coerceAtLeast(1)
            val ratio = i.toFloat() / (count - 1).coerceAtLeast(1)
            val baseColor = when {
                ratio < 0.22f -> DeckACyan
                ratio < 0.40f -> LedPurple
                ratio < 0.58f -> DeckBGold
                ratio < 0.78f -> DeckBOrange
                ratio < 0.90f -> DeckBRed
                else -> DeckABlue
            }
            for (r in 0 until rows) {
                val fromBottom = rows - 1 - r
                val lit = fromBottom < litRows
                drawRoundRect(
                    color = if (lit) baseColor.copy(alpha = 0.95f) else Color(0x221E293B),
                    topLeft = Offset(i * colW + gap / 2f, r * rowH + 1f),
                    size = Size((colW - gap).coerceAtLeast(2f), (rowH - 2f).coerceAtLeast(1.5f)),
                    cornerRadius = CornerRadius(2f)
                )
            }
        }
    }
}

/**
 * Backlit Hardware Push Button (CUE, SYNC, LOOP, KEY LOCK, etc.).
 */
@Composable
fun HardwareLedButton(
    text: String,
    active: Boolean,
    activeColor: Color,
    testTagName: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (active) activeColor.copy(alpha = 0.28f) else Color(0xFF131926)
            )
            .border(
                width = if (active) 1.5.dp else 1.dp,
                color = if (active) activeColor else MetalBorder,
                shape = RoundedCornerShape(6.dp)
            )
            .testTag(testTagName)
            .pointerInput(testTagName) {
                detectTapGestures(onTap = { onClick() })
            }
            .padding(horizontal = 6.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (active) Color.White else TextSecondary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = JetBrainsMonoFontFamily,
            maxLines = 1
        )
    }
}
