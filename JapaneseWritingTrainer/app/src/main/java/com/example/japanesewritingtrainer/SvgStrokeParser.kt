package com.example.japanesewritingtrainer

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.vector.PathParser

/**
 * Một điểm được lấy mẫu trên nét SVG, giữ nguyên hệ tọa độ của viewBox.
 */
data class StrokePoint(
    val x: Float,
    val y: Float
)

/**
 * Hình học đã phân tích của một nét tham chiếu.
 *
 * points được lấy mẫu theo khoảng cách cung (arc length), không phải
 * chia đều theo tham số Bézier. Vì vậy các điểm phân bố tương đối đều
 * dọc theo chiều dài nét.
 */
data class ParsedStroke(
    val order: Int,
    val points: List<StrokePoint>,
    val startPoint: StrokePoint,
    val endPoint: StrokePoint,
    val length: Float,
    val bounds: Rect
)

/**
 * Chuyển svgPath trong dataset thành dữ liệu hình học dùng cho các bước sau:
 * - hướng di chuyển (startPoint -> endPoint),
 * - vùng bao (bounds),
 * - độ dài nét (length),
 * - so khớp đường đi (points).
 *
 * Lớp này chỉ phân tích nét tham chiếu, chưa đánh giá nét người dùng.
 */
object SvgStrokeParser {

    /**
     * @param stroke nét lấy từ hiragana.json.
     * @param sampleCount số đoạn chia đều theo chiều dài; số điểm trả về là sampleCount + 1.
     */
    fun parse(stroke: StrokeData, sampleCount: Int = DEFAULT_SAMPLE_COUNT): ParsedStroke {
        require(sampleCount >= 1) { "sampleCount phải >= 1" }
        require(stroke.svgPath.isNotBlank()) { "svgPath không được để trống" }

        val path = PathParser()
            .parsePathString(stroke.svgPath)
            .toPath()

        val measure = PathMeasure().apply {
            setPath(path, false)
        }

        val length = measure.length
        require(length > 0f && length.isFinite()) {
            "SVG path của nét ${stroke.order} không tạo ra đường có độ dài hợp lệ"
        }

        fun pointAt(distance: Float): StrokePoint {
            val position = measure.getPosition(distance.coerceIn(0f, length))
            return StrokePoint(position.x, position.y)
        }

        val points = (0..sampleCount).map { index ->
            pointAt(length * index / sampleCount)
        }

        return ParsedStroke(
            order = stroke.order,
            points = points,
            startPoint = points.first(),
            endPoint = points.last(),
            length = length,
            bounds = path.getBounds()
        )
    }

    fun parseAll(
        strokes: List<StrokeData>,
        sampleCount: Int = DEFAULT_SAMPLE_COUNT
    ): List<ParsedStroke> {
        return strokes.sortedBy { it.order }.map { parse(it, sampleCount) }
    }

    private const val DEFAULT_SAMPLE_COUNT = 64
}
