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
private val TemplateStyle = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round)

private val TemplateColor = Color(0xFFD0D0D0)
private val AnimationColor = Color(0xFF1E88E5)
private val NumberColor = Color(0xFFD32F2F)
private val CompletedGuideColor = Color(0xFFBDBDBD)
private val WaitingGuideColor = Color(0xFFE8E8E8)

private enum class WritingMode {
    TRACE,
    GUIDED
}

/**
 * Màn hình luyện viết:
 * - Trace: hiển thị mẫu mờ để người học đồ theo.
 * - Guided: chỉ tô sáng nét cần viết tiếp theo; mỗi lần nhấc tay hoàn thành một nét.
 * - Hỗ trợ Undo, Clear, ẩn/hiện gợi ý và xem animation thứ tự nét.
 *
 * Guided Writing MVP chỉ theo dõi số lượng/thứ tự thao tác viết,
 * chưa so khớp hình dạng nét với SVG.
 */
@Composable
fun WritingCanvas(
    modifier: Modifier = Modifier,
    character: CharacterData? = null
) {
    val strokes = remember(character?.id) { mutableStateListOf<Path>() }
    val currentPath = remember(character?.id) { Path() }
    var redrawTick by remember(character?.id) { mutableIntStateOf(0) }

    val templatePaths: List<Path> = remember(character) {
        character?.strokes?.map { PathParser().parsePathString(it.svgPath).toPath() }
            ?: emptyList()
    }
    val measures: List<PathMeasure> = remember(templatePaths) {
        templatePaths.map { path -> PathMeasure().also { it.setPath(path, false) } }
    }

    // Giữ chế độ đang chọn khi chuyển sang ký tự khác.
    // Các trạng thái nét vẽ và tiến trình vẫn được reset theo character?.id.
    var mode by remember { mutableStateOf(WritingMode.TRACE) }
    var guidedProgress by remember(character?.id) {
        mutableStateOf(GuidedWritingProgress())
    }
    var showTemplate by remember(character?.id) { mutableStateOf(true) }

    val animProgress = remember(character?.id) { Animatable(0f) }
    var playToken by remember(character?.id) { mutableIntStateOf(0) }
    var isAnimating by remember(character?.id) { mutableStateOf(false) }

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
    val totalStrokes = templatePaths.size
    val completedStrokes = guidedProgress.completedStrokeCount.coerceAtMost(totalStrokes)
    val isGuidedComplete = guidedProgress.isComplete(totalStrokes)

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

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    mode = WritingMode.TRACE
                    strokes.clear()
                    currentPath.reset()
                    guidedProgress = guidedProgress.reset()
                    redrawTick++
                },
                enabled = mode != WritingMode.TRACE
            ) {
                Text("Trace")
            }
            OutlinedButton(
                onClick = {
                    mode = WritingMode.GUIDED
                    strokes.clear()
                    currentPath.reset()
                    guidedProgress = guidedProgress.reset()
                    showTemplate = true
                    redrawTick++
                },
                enabled = mode != WritingMode.GUIDED && totalStrokes > 0
            ) {
                Text("Guided Writing")
            }
        }

        if (mode == WritingMode.GUIDED && totalStrokes > 0) {
            Text(
                text = if (isGuidedComplete) {
                    "Hoàn thành! Bạn đã viết đủ $totalStrokes nét."
                } else {
                    "Nét ${completedStrokes + 1} / $totalStrokes"
                },
                color = if (isGuidedComplete) Color(0xFF2E7D32) else Color.Unspecified
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(Color.White)
                .pointerInput(mode, character?.id, totalStrokes) {
                    awaitEachGesture {
                        // Luôn chờ và xử lý trọn vẹn một gesture. Khi Guided đã hoàn
                        // thành, vẫn nhận sự kiện cảm ứng nhưng không ghi thêm nét.
                        // Không return sớm khỏi awaitEachGesture vì có thể tạo vòng lặp
                        // bận trên Main Thread sau khi hoàn thành ký tự.
                        val canRecordStroke = mode != WritingMode.GUIDED ||
                            guidedProgress.canAcceptStroke(totalStrokes)

                        val down = awaitFirstDown()
                        down.consume()

                        var last = down.position
                        var moved = false

                        if (canRecordStroke) {
                            currentPath.reset()
                            currentPath.moveTo(last.x, last.y)
                            redrawTick++
                        }

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                                ?: break
                            if (!change.pressed) break

                            if (canRecordStroke) {
                                for (historical in change.historical) {
                                    val point = historical.position
                                    currentPath.quadraticTo(
                                        last.x,
                                        last.y,
                                        (last.x + point.x) / 2f,
                                        (last.y + point.y) / 2f
                                    )
                                    last = point
                                    moved = true
                                }

                                val point = change.position
                                currentPath.quadraticTo(
                                    last.x,
                                    last.y,
                                    (last.x + point.x) / 2f,
                                    (last.y + point.y) / 2f
                                )
                                last = point
                                moved = true
                            }

                            change.consume()
                            if (canRecordStroke) redrawTick++
                        }

                        if (canRecordStroke) {
                            if (moved) {
                                currentPath.lineTo(last.x, last.y)
                            } else {
                                currentPath.lineTo(last.x + 0.1f, last.y)
                            }

                            strokes.add(Path().apply { addPath(currentPath) })
                            if (mode == WritingMode.GUIDED) {
                                guidedProgress = guidedProgress.completeStroke(totalStrokes)
                            }
                            currentPath.reset()
                            redrawTick++
                        }
                    }
                }
        ) {
            @Suppress("UNUSED_VARIABLE")
            val tick = redrawTick

            drawGrid()

            if (templatePaths.isNotEmpty()) {
                val unit = size.width / (character?.viewBoxSize ?: 109f)

                if (isAnimating) {
                    val progress = animProgress.value
                    for (i in templatePaths.indices) {
                        val fraction = (progress - i).coerceIn(0f, 1f)
                        if (fraction <= 0f) break
                        if (fraction >= 1f) {
                            drawTemplateStroke(templatePaths[i], unit, AnimationColor)
                        } else {
                            val segment = Path()
                            measures[i].getSegment(
                                0f,
                                measures[i].length * fraction,
                                segment,
                                true
                            )
                            drawTemplateStroke(segment, unit, AnimationColor)
                        }
                    }
                } else if (showTemplate && mode == WritingMode.GUIDED) {
                    // Các nét đã hoàn thành và nét chờ được làm mờ.
                    // Chỉ nét kế tiếp được tô xanh để người học tập trung.
                    templatePaths.forEachIndexed { index, path ->
                        val color = when {
                            index < completedStrokes -> CompletedGuideColor
                            index == completedStrokes && !isGuidedComplete -> AnimationColor
                            else -> WaitingGuideColor
                        }
                        drawTemplateStroke(path, unit, color)
                    }

                    measures.forEachIndexed { index, measure ->
                        val start = measure.getPosition(0f)
                        val numberColor = if (index == completedStrokes && !isGuidedComplete) {
                            NumberColor
                        } else {
                            CompletedGuideColor
                        }
                        drawText(
                            textMeasurer = textMeasurer,
                            text = "${index + 1}",
                            topLeft = Offset(start.x * unit - 18f, start.y * unit - 40f),
                            style = TextStyle(fontSize = 14.sp, color = numberColor)
                        )
                    }
                } else if (showTemplate) {
                    templatePaths.forEach { path ->
                        drawTemplateStroke(path, unit, TemplateColor)
                    }

                    measures.forEachIndexed { index, measure ->
                        val start = measure.getPosition(0f)
                        drawText(
                            textMeasurer = textMeasurer,
                            text = "${index + 1}",
                            topLeft = Offset(start.x * unit - 18f, start.y * unit - 40f),
                            style = TextStyle(fontSize = 14.sp, color = NumberColor)
                        )
                    }
                }
            }

            strokes.forEach { drawPath(it, Color.Black, style = InkStyle) }
            drawPath(currentPath, Color.Black, style = InkStyle)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showTemplate = !showTemplate }) {
                Text(if (showTemplate) "Ẩn gợi ý" else "Hiện gợi ý")
            }
            OutlinedButton(
                onClick = { playToken++ },
                enabled = !isAnimating && templatePaths.isNotEmpty()
            ) {
                Text("Xem thứ tự nét")
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = {
                    if (strokes.isNotEmpty()) {
                        strokes.removeAt(strokes.lastIndex)
                        if (mode == WritingMode.GUIDED) {
                            guidedProgress = guidedProgress.undo()
                        }
                        redrawTick++
                    }
                },
                enabled = strokes.isNotEmpty()
            ) {
                Text("Undo")
            }

            Button(
                onClick = {
                    strokes.clear()
                    currentPath.reset()
                    if (mode == WritingMode.GUIDED) {
                        guidedProgress = guidedProgress.reset()
                    }
                    redrawTick++
                },
                enabled = strokes.isNotEmpty() || completedStrokes > 0
            ) {
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
