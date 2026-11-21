package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.audio.DeckState
import com.example.ui.theme.*
import kotlin.math.max

/**
 * Real decoded PCM audio waveform visualization with:
 * - Multi-color intensity shading (bass vs treble energy)
 * - Beat grid markers
 * - Hot Cue flags (1..4)
 * - Active Loop IN/OUT shaded region
 * - Playhead cursor & touch-to-seek / scrub navigation
 */
@Composable
fun RealWaveformCanvas(
    deck: DeckState,
    primaryColor: Color,
    secondaryColor: Color,
    modifier: Modifier = Modifier,
    onSeekRatio: (Float) -> Unit
) {
    val progressRatio = if (deck.durationMs > 0L) {
        (deck.positionMs.toFloat() / deck.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF070B13))
            .testTag("waveform_${deck.deckId.name.lowercase()}")
            .pointerInput(deck.deckId) {
                detectTapGestures { offset ->
                    val ratio = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                    onSeekRatio(ratio)
                }
            }
            .pointerInput(deck.deckId) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    val ratio = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                    onSeekRatio(ratio)
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val midY = h * 0.55f
            val peaks = deck.waveform.peaks

            // 1. Active Loop Region Highlight
            if (deck.loopActive && deck.durationMs > 0L && deck.loopInMs != null && deck.loopOutMs != null) {
                val startX = (deck.loopInMs.toFloat() / deck.durationMs) * w
                val endX = (deck.loopOutMs.toFloat() / deck.durationMs) * w
                drawRect(
                    color = LedGreen.copy(alpha = 0.22f),
                    topLeft = Offset(startX, 0f),
                    size = Size((endX - startX).coerceAtLeast(4f), h)
                )
            }

            // 2. Beat Grid Vertical Markers
            val beats = deck.waveform.beatPositionsRatio
            if (beats.isNotEmpty()) {
                val stride = max(1, beats.size / 32)
                var i = 0
                while (i < beats.size) {
                    val bx = beats[i] * w
                    val isDownbeat = (i % 4 == 0)
                    drawLine(
                        color = if (isDownbeat) Color(0x44FFFFFF) else Color(0x1EFFFFFF),
                        start = Offset(bx, 0f),
                        end = Offset(bx, h),
                        strokeWidth = if (isDownbeat) 1.2f else 0.7f
                    )
                    i += stride
                }
            }

            // 3. Real Decoded PCM Waveform Bars
            if (peaks.isNotEmpty()) {
                val zoom = deck.waveformZoom.coerceIn(1f, 4f)
                val visibleCount = (peaks.size / zoom).toInt().coerceAtLeast(24)
                val centerIndex = (progressRatio * peaks.size).toInt()
                val startIdx = if (zoom <= 1.05f) 0 else (centerIndex - visibleCount / 2)
                    .coerceIn(0, (peaks.size - visibleCount).coerceAtLeast(0))
                val barW = w / visibleCount.toFloat()

                for (i in 0 until visibleCount) {
                    val pIdx = (startIdx + i).coerceIn(0, peaks.lastIndex)
                    val peak = peaks[pIdx]
                    val barH = (peak.amplitude * h * 0.88f).coerceAtLeast(3f)
                    val x = i * barW
                    val peakRatio = pIdx.toFloat() / peaks.size.toFloat()
                    val played = peakRatio <= progressRatio

                    // Color-coded by low/high intensity
                    val intensityColor = if (peak.lowEnergy > 0.58f) {
                        secondaryColor
                    } else {
                        primaryColor
                    }

                    drawLine(
                        color = if (played) intensityColor else intensityColor.copy(alpha = 0.42f),
                        start = Offset(x + barW * 0.5f, h - barH),
                        end = Offset(x + barW * 0.5f, h),
                        strokeWidth = (barW * 0.72f).coerceAtLeast(1.4f)
                    )
                }
            } else {
                // Subtle center baseline before load
                drawLine(
                    color = primaryColor.copy(alpha = 0.25f),
                    start = Offset(0f, midY),
                    end = Offset(w, midY),
                    strokeWidth = 1.5f
                )
            }

            // 4. Hot Cue Markers (1..4)
            if (deck.durationMs > 0L) {
                val cueColors = listOf(LedRed, LedAmber, DeckACyan, LedPurple)
                deck.hotCues.forEach { cue ->
                    val pos = cue.positionMs ?: return@forEach
                    val cx = (pos.toFloat() / deck.durationMs) * w
                    val color = cueColors.getOrElse(cue.slotIndex) { DeckACyan }
                    drawLine(
                        color = color,
                        start = Offset(cx, 0f),
                        end = Offset(cx, h),
                        strokeWidth = 2.dp.toPx()
                    )
                    drawCircle(
                        color = color,
                        radius = 3.5.dp.toPx(),
                        center = Offset(cx, 5.dp.toPx())
                    )
                }
            }

            // 5. Real-time Playhead Cursor
            val playX = progressRatio * w
            drawLine(
                color = Color.White,
                start = Offset(playX, 0f),
                end = Offset(playX, h),
                strokeWidth = 2.dp.toPx()
            )
        }
    }
}
