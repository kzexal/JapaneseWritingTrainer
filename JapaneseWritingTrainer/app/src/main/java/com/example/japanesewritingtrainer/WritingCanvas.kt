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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
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

/**
 * Màn hình luyện viết:
 * - Trace: hiển thị mẫu mờ để người học đồ theo.
 * - Guided: hiển thị nét cần viết tiếp theo và hướng dẫn chiều viết.
 * - Recall: ẩn mẫu trong lúc viết, yêu cầu người học tự nhớ thứ tự và hình dạng nét.
 * - Hỗ trợ Undo, Clear, ẩn/hiện đáp án và xem animation thứ tự nét.
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
    val analyzedStrokes = remember(character?.id) { mutableStateListOf<AnalyzedUserStroke>() }
    val strokeMatches = remember(character?.id) {
        mutableStateListOf<Pair<Int, StrokeMatchResult>>()
    }
    val referenceStrokes = remember(character?.id) {
        character?.strokes?.let { SvgStrokeParser.parseAll(it) } ?: emptyList()
    }
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
    var latestAnalysisFailed by remember(character?.id) { mutableStateOf(false) }

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
    val latestMatch = strokeMatches.lastOrNull()?.second

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
                    analyzedStrokes.clear()
                    strokeMatches.clear()
                    latestAnalysisFailed = false
                    currentPath.reset()
                    guidedProgress = guidedProgress.reset()
                    showTemplate = true
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
                    analyzedStrokes.clear()
                    strokeMatches.clear()
                    latestAnalysisFailed = false
                    currentPath.reset()
                    guidedProgress = guidedProgress.reset()
                    showTemplate = true
                    redrawTick++
                },
                enabled = mode != WritingMode.GUIDED && totalStrokes > 0
            ) {
                Text("Guided")
            }
            OutlinedButton(
                onClick = {
                    mode = WritingMode.RECALL
                    strokes.clear()
                    analyzedStrokes.clear()
                    strokeMatches.clear()
                    latestAnalysisFailed = false
                    currentPath.reset()
                    guidedProgress = guidedProgress.reset()
                    showTemplate = false
                    redrawTick++
                },
                enabled = mode != WritingMode.RECALL && totalStrokes > 0
            ) {
                Text("Recall")
            }
        }

        if (mode.requiresStrokeValidation && totalStrokes > 0) {
            Text(
                text = when {
                    isGuidedComplete && mode == WritingMode.RECALL ->
                        "Hoàn thành! Hãy đối chiếu bài viết với mẫu."
                    isGuidedComplete ->
                        "Hoàn thành! Bạn đã viết đủ $totalStrokes nét."
                    mode == WritingMode.RECALL ->
                        "Tự nhớ và viết · Nét ${completedStrokes + 1} / $totalStrokes"
                    else ->
                        "Nét ${completedStrokes + 1} / $totalStrokes"
                },
                color = if (isGuidedComplete) Color(0xFF2E7D32) else Color.Unspecified
            )
            if (latestAnalysisFailed) {
                Text(
                    text = "Không đủ dữ liệu để đánh giá nét này. Hãy viết lại.",
                    color = Color(0xFFEF6C00)
                )
            } else if (latestMatch != null) {
                Text(
                    text = if (latestMatch.accepted) {
                        "Nét khá giống mẫu · " + latestMatch.score.toInt() + "/100"
                    } else if (latestMatch.directionReversed) {
                        "Sai chiều viết nét · " + latestMatch.score.toInt() + "/100"
                    } else if (!latestMatch.directionCorrect) {
                        "Sai vị trí bắt đầu hoặc kết thúc nét · " + latestMatch.score.toInt() + "/100"
                    } else if (!latestMatch.lengthWithinTolerance) {
                        val lengthMessage = if (latestMatch.lengthRatio < 1f) {
                            "Nét quá ngắn"
                        } else {
                            "Nét quá dài"
                        }
                        "$lengthMessage · " + latestMatch.score.toInt() + "/100"
                    } else {
                        "Nét chưa khớp mẫu · " + latestMatch.score.toInt() + "/100"
                    },
                    color = if (latestMatch.accepted) {
                        Color(0xFF2E7D32)
                    } else {
                        Color(0xFFEF6C00)
                    }
                )
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(Color.White)
                .pointerInput(mode, character?.id, totalStrokes) {
                    val viewBoxSize = character?.viewBoxSize ?: 109f
                    val inputTransformer = CoordinateTransformer.fit(
                        sourceWidth = viewBoxSize,
                        sourceHeight = viewBoxSize,
                        canvasSize = Size(size.width.toFloat(), size.height.toFloat())
                    )

                    awaitEachGesture {
                        // Luôn chờ và xử lý trọn vẹn một gesture. Khi Guided đã hoàn
                        // thành, vẫn nhận sự kiện cảm ứng nhưng không ghi thêm nét.
                        // Không return sớm khỏi awaitEachGesture vì có thể tạo vòng lặp
                        // bận trên Main Thread sau khi hoàn thành ký tự.
                        val canRecordStroke = !mode.requiresStrokeValidation ||
                            guidedProgress.canAcceptStroke(totalStrokes)

                        val down = awaitFirstDown()
                        down.consume()

                        var last = down.position
                        var moved = false
                        val rawStrokePoints = mutableListOf<CanvasStrokePoint>()

                        if (canRecordStroke) {
                            latestAnalysisFailed = false
                            rawStrokePoints += CanvasStrokePoint(
                                x = last.x,
                                y = last.y,
                                timestampMillis = down.uptimeMillis
                            )
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
                                    rawStrokePoints += CanvasStrokePoint(
                                        x = point.x,
                                        y = point.y,
                                        timestampMillis = historical.uptimeMillis
                                    )
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
                                rawStrokePoints += CanvasStrokePoint(
                                    x = point.x,
                                    y = point.y,
                                    timestampMillis = change.uptimeMillis
                                )
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
                            val attemptOrder = strokes.size
                            val targetOrder = if (mode.requiresStrokeValidation) {
                                guidedProgress.nextStrokeOrder(totalStrokes) ?: attemptOrder
                            } else {
                                attemptOrder
                            }
                            val analyzed = StrokeInputAnalyzer.analyze(
                                order = attemptOrder,
                                rawPoints = rawStrokePoints,
                                transformer = inputTransformer
                            )
                            var acceptedAttempt = false
                            if (analyzed == null) {
                                latestAnalysisFailed = true
                            } else {
                                analyzedStrokes.add(analyzed)
                                val reference = referenceStrokes.getOrNull(targetOrder - 1)
                                if (reference != null) {
                                    val result = StrokeMatcher.match(
                                        reference = reference,
                                        user = analyzed,
                                        viewBoxSize = viewBoxSize
                                    )
                                    strokeMatches.add(attemptOrder to result)
                                    acceptedAttempt = result.accepted
                                }
                            }

                            if (mode.requiresStrokeValidation && acceptedAttempt) {
                                val updatedProgress = guidedProgress.completeStroke(totalStrokes)
                                guidedProgress = updatedProgress
                                // Chỉ mở mẫu đáp án sau khi người học tự hoàn thành
                                // tất cả các nét trong Recall Writing.
                                if (mode == WritingMode.RECALL &&
                                    updatedProgress.isComplete(totalStrokes)
                                ) {
                                    showTemplate = true
                                }
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
                val viewBoxSize = character?.viewBoxSize ?: 109f
                val transformer = CoordinateTransformer.fit(
                    sourceWidth = viewBoxSize,
                    sourceHeight = viewBoxSize,
                    canvasSize = size
                )

                if (isAnimating) {
                    val progress = animProgress.value
                    for (i in templatePaths.indices) {
                        val fraction = (progress - i).coerceIn(0f, 1f)
                        if (fraction <= 0f) break
                        if (fraction >= 1f) {
                            drawTemplateStroke(templatePaths[i], transformer, AnimationColor)
                        } else {
                            val segment = Path()
                            measures[i].getSegment(
                                0f,
                                measures[i].length * fraction,
                                segment,
                                true
                            )
                            drawTemplateStroke(segment, transformer, AnimationColor)
                        }
                    }
                } else if (showTemplate && mode.showsStrokeGuidance) {
                    // Các nét đã hoàn thành và nét chờ được làm mờ.
                    // Chỉ nét kế tiếp được tô xanh để người học tập trung.
                    templatePaths.forEachIndexed { index, path ->
                        val color = when {
                            index < completedStrokes -> CompletedGuideColor
                            index == completedStrokes && !isGuidedComplete -> AnimationColor
                            else -> WaitingGuideColor
                        }
                        drawTemplateStroke(path, transformer, color)
                    }

                    measures.forEachIndexed { index, measure ->
                        val start = transformer.toCanvas(measure.getPosition(0f))
                        val isNextStroke = index == completedStrokes && !isGuidedComplete
                        val numberColor = if (isNextStroke) NumberColor else CompletedGuideColor

                        drawCircle(
                            color = numberColor,
                            radius = if (isNextStroke) 5f else 3f,
                            center = start
                        )
                        drawText(
                            textMeasurer = textMeasurer,
                            text = "${index + 1}",
                            topLeft = Offset(start.x - 18f, start.y - 40f),
                            style = TextStyle(fontSize = 14.sp, color = numberColor)
                        )

                        if (isNextStroke) {
                            drawStrokeDirection(measure, transformer)
                        }
                    }
                } else if (
                    showTemplate &&
                    (mode == WritingMode.TRACE ||
                        (mode == WritingMode.RECALL && isGuidedComplete))
                ) {
                    templatePaths.forEach { path ->
                        drawTemplateStroke(path, transformer, TemplateColor)
                    }

                    measures.forEachIndexed { index, measure ->
                        val start = transformer.toCanvas(measure.getPosition(0f))
                        drawText(
                            textMeasurer = textMeasurer,
                            text = "${index + 1}",
                            topLeft = Offset(start.x - 18f, start.y - 40f),
                            style = TextStyle(fontSize = 14.sp, color = NumberColor)
                        )
                    }
                }
            }

            strokes.forEach { drawPath(it, Color.Black, style = InkStyle) }
            drawPath(currentPath, Color.Black, style = InkStyle)
        }

        if (mode != WritingMode.RECALL || isGuidedComplete) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showTemplate = !showTemplate }) {
                    Text(if (showTemplate) "Ẩn mẫu" else "Hiện mẫu")
                }
                OutlinedButton(
                    onClick = { playToken++ },
                    enabled = !isAnimating && templatePaths.isNotEmpty()
                ) {
                    Text("Xem thứ tự nét")
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = {
                    if (strokes.isNotEmpty()) {
                        val removedOrder = strokes.size
                        val removedMatch = strokeMatches.lastOrNull { it.first == removedOrder }?.second
                        strokes.removeAt(strokes.lastIndex)
                        analyzedStrokes.removeAll { it.order == removedOrder }
                        strokeMatches.removeAll { it.first == removedOrder }
                        latestAnalysisFailed = false
                        if (mode.requiresStrokeValidation && removedMatch?.accepted == true) {
                            guidedProgress = guidedProgress.undo()
                            if (mode == WritingMode.RECALL && !guidedProgress.isComplete(totalStrokes)) {
                                showTemplate = false
                            }
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
                    analyzedStrokes.clear()
                    strokeMatches.clear()
                    latestAnalysisFailed = false
                    currentPath.reset()
                    if (mode.requiresStrokeValidation) {
                        guidedProgress = guidedProgress.reset()
                        if (mode == WritingMode.RECALL) showTemplate = false
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

/**
 * Hiển thị mũi tên nhỏ trên nét đang được hướng dẫn.
 * Mũi tên chỉ chiều đi từ đầu nét đến cuối nét theo SVG tham chiếu.
 */
private fun DrawScope.drawStrokeDirection(
    measure: PathMeasure,
    transformer: CoordinateTransformer
) {
    if (measure.length <= 0f) return

    val distance = measure.length * 0.35f
    val sourcePosition = measure.getPosition(distance)
    val sourceTangent = measure.getTangent(distance)
    val position = transformer.toCanvas(sourcePosition)
    val tangentLength = kotlin.math.sqrt(
        sourceTangent.x * sourceTangent.x + sourceTangent.y * sourceTangent.y
    )
    if (tangentLength <= 0f) return

    val direction = Offset(
        sourceTangent.x / tangentLength,
        sourceTangent.y / tangentLength
    )
    val arrowLength = 13f
    val arrowHalfWidth = 6f
    val tip = position + direction * arrowLength
    val base = tip - direction * arrowLength
    val perpendicular = Offset(-direction.y, direction.x)

    drawLine(
        color = AnimationColor,
        start = base,
        end = tip,
        strokeWidth = 3f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = AnimationColor,
        start = tip,
        end = tip - direction * 7f + perpendicular * arrowHalfWidth,
        strokeWidth = 3f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = AnimationColor,
        start = tip,
        end = tip - direction * 7f - perpendicular * arrowHalfWidth,
        strokeWidth = 3f,
        cap = StrokeCap.Round
    )
}

/** Vẽ một nét mẫu, phóng từ hệ tọa độ dataset sang pixel của canvas. */
private fun DrawScope.drawTemplateStroke(
    path: Path,
    transformer: CoordinateTransformer,
    color: Color
) {
    translate(left = transformer.offset.x, top = transformer.offset.y) {
        scale(scale = transformer.scale, pivot = Offset.Zero) {
            drawPath(path, color, style = TemplateStyle)
        }
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
