package com.example.japanesewritingtrainer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val InkStyle = Stroke(width = 14f, cap = StrokeCap.Round, join = StrokeJoin.Round)

// Đơn vị theo hệ tọa độ của dataset (viewBox 109) nên sẽ được phóng to theo canvas.
private val TemplateStyle = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round)

private val TemplateColor = Color(0xFFD0D0D0)
private val AnimationColor = Color(0xFF1E88E5)
private val NumberColor = Color(0xFFD32F2F)

/**
 * Màn hình viết chữ:
 * - Khung vuông có lưới, vẽ bằng ngón tay.
 * - Chữ mẫu mờ (Trace) + số thứ tự nét.
 * - Animation thứ tự nét.
 * - Undo / Xóa hết.
 */
@Composable
fun WritingCanvas(
    modifier: Modifier = Modifier,
    character: CharacterData? = null
) {
    // ---- Nét người dùng vẽ ----
    val strokes = remember { mutableStateListOf<Path>() }
    val currentPath = remember { Path() }
    var redrawTick by remember { mutableIntStateOf(0) }

    // ---- Dữ liệu chữ mẫu ----
    val templatePaths: List<Path> = remember(character) {
        character?.strokes?.map { PathParser().parsePathString(it.svgPath).toPath() }
            ?: emptyList()
    }
    val measures: List<PathMeasure> = remember(templatePaths) {
        templatePaths.map { p -> PathMeasure().also { it.setPath(p, false) } }
    }

    // ---- Trạng thái hiển thị ----
    var showTemplate by remember { mutableStateOf(true) }
    val animProgress = remember { Animatable(0f) }
    var playToken by remember { mutableIntStateOf(0) }
    var isAnimating by remember { mutableStateOf(false) }

    LaunchedEffect(playToken) {
        if (playToken == 0 || templatePaths.isEmpty()) return@LaunchedEffect
        isAnimating = true
        animProgress.snapTo(0f)
        animProgress.animateTo(
            targetValue = templatePaths.size.toFloat(),
            animationSpec = tween(
                durationMillis = 800 * templatePaths.size,
                easing = LinearEasing
            )
        )
        isAnimating = false
    }

    val textMeasurer = rememberTextMeasurer()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (character != null) {
            Text(
                text = "${character.character}  (${character.reading}) · ${character.strokeCount} nét",
                style = TextStyle(fontSize = 24.sp)
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(Color.White)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()

                        var last: Offset = down.position
                        var moved = false
                        currentPath.reset()
                        currentPath.moveTo(last.x, last.y)
                        redrawTick++

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break

                            for (h in change.historical) {
                                val p = h.position
                                currentPath.quadraticTo(
                                    last.x, last.y,
                                    (last.x + p.x) / 2f, (last.y + p.y) / 2f
                                )
                                last = p
                                moved = true
                            }
                            val p = change.position
                            currentPath.quadraticTo(
                                last.x, last.y,
                                (last.x + p.x) / 2f, (last.y + p.y) / 2f
                            )
                            last = p
                            moved = true

                            change.consume()
                            redrawTick++
                        }

                        if (moved) {
                            currentPath.lineTo(last.x, last.y)
                        } else {
                            currentPath.lineTo(last.x + 0.1f, last.y)
                        }
                        strokes.add(Path().apply { addPath(currentPath) })
                        currentPath.reset()
                        redrawTick++
                    }
                }
        ) {
            @Suppress("UNUSED_VARIABLE")
            val tick = redrawTick

            drawGrid()

            // --- Lớp 1: chữ mẫu (nằm dưới nét của người dùng) ---
            if (templatePaths.isNotEmpty()) {
                val unit = size.width / (character?.viewBoxSize ?: 109f)

                if (isAnimating) {
                    val progress = animProgress.value
                    for (i in templatePaths.indices) {
                        val frac = (progress - i).coerceIn(0f, 1f)
                        if (frac <= 0f) break
                        if (frac >= 1f) {
                            drawTemplateStroke(templatePaths[i], unit, AnimationColor)
                        } else {
                            val m = measures[i]
                            val segment = Path()
                            m.getSegment(0f, m.length * frac, segment, true)
                            drawTemplateStroke(segment, unit, AnimationColor)
                        }
                    }
                } else if (showTemplate) {
                    templatePaths.forEach { drawTemplateStroke(it, unit, TemplateColor) }

                    // Số thứ tự nét, đặt gần điểm bắt đầu của mỗi nét.
                    measures.forEachIndexed { i, m ->
                        val start = m.getPosition(0f)
                        drawText(
                            textMeasurer = textMeasurer,
                            text = "${i + 1}",
                            topLeft = Offset(start.x * unit - 18f, start.y * unit - 40f),
                            style = TextStyle(fontSize = 14.sp, color = NumberColor)
                        )
                    }
                }
            }

            // --- Lớp 2: nét người dùng ---
            strokes.forEach { drawPath(it, Color.Black, style = InkStyle) }
            drawPath(currentPath, Color.Black, style = InkStyle)
        }

        if (character != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { showTemplate = !showTemplate }) {
                    Text(if (showTemplate) "Ẩn mẫu" else "Hiện mẫu")
                }
                OutlinedButton(
                    onClick = { playToken++ },
                    enabled = !isAnimating
                ) {
                    Text("Xem thứ tự nét")
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = {
                if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex)
            }) {
                Text("Undo")
            }
            Button(onClick = {
                strokes.clear()
                currentPath.reset()
                redrawTick++
            }) {
                Text("Xóa hết")
            }
        }
    }
}

/** Vẽ một nét mẫu, phóng từ hệ tọa độ dataset sang pixel của canvas. */
private fun DrawScope.drawTemplateStroke(path: Path, unit: Float, color: Color) {
    scale(scale = unit, pivot = Offset.Zero) {
        drawPath(path, color, style = TemplateStyle)
    }
}

/** Vẽ khung viền và hai đường gạch giữa (ngang, dọc) dạng nét đứt. */
private fun DrawScope.drawGrid() {
    val gridColor = Color(0xFFB0B0B0)
    val dash = PathEffect.dashPathEffect(floatArrayOf(16f, 16f))

    drawRect(color = gridColor, style = Stroke(width = 3f))
    drawLine(
        color = gridColor,
        start = Offset(size.width / 2, 0f),
        end = Offset(size.width / 2, size.height),
        strokeWidth = 2f,
        pathEffect = dash
    )
    drawLine(
        color = gridColor,
        start = Offset(0f, size.height / 2),
        end = Offset(size.width, size.height / 2),
        strokeWidth = 2f,
        pathEffect = dash
    )
}