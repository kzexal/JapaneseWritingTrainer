package com.example.japanesewritingtrainer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.sqrt

/**
 * Một mẫu cảm ứng được ghi nhận trong hệ tọa độ pixel của Canvas.
 *
 * timestampMillis nên lấy từ event.uptimeMillis để dùng cùng một đồng hồ
 * thời gian cho toàn bộ các điểm của gesture.
 */
data class CanvasStrokePoint(
    val x: Float,
    val y: Float,
    val timestampMillis: Long
)

/**
 * Dữ liệu hình học của một nét do người dùng viết, sau khi chuẩn hóa
 * từ pixel Canvas về hệ tọa độ SVG/viewBox.
 */
data class AnalyzedUserStroke(
    val order: Int,
    val points: List<StrokePoint>,
    val startPoint: StrokePoint,
    val endPoint: StrokePoint,
    val length: Float,
    val bounds: Rect,
    val durationMillis: Long
)

/**
 * Phân tích dữ liệu cảm ứng thô thành hình học có thể so sánh với SVG.
 *
 * Lớp này chưa kết luận nét viết đúng hay sai. Nó chỉ chuẩn hóa tọa độ,
 * loại bỏ các điểm quá gần nhau và tính các đặc trưng cơ bản.
 */
object StrokeInputAnalyzer {

    /**
     * @return AnalyzedUserStroke, hoặc null nếu không đủ điểm hợp lệ để
     * tạo thành một nét có độ dài dương.
     *
     * Nếu có bất kỳ điểm nào nằm ngoài vùng SVG đang hiển thị, gesture
     * được xem là không hợp lệ và trả về null.
     */
    fun analyze(
        order: Int,
        rawPoints: List<CanvasStrokePoint>,
        transformer: CoordinateTransformer,
        minDistancePx: Float = DEFAULT_MIN_DISTANCE_PX
    ): AnalyzedUserStroke? {
        require(order >= 1) { "Thứ tự nét phải bắt đầu từ 1" }
        require(minDistancePx >= 0f && minDistancePx.isFinite()) {
            "minDistancePx phải là số hữu hạn và không âm"
        }

        if (rawPoints.size < 2) return null

        require(rawPoints.all {
            it.x.isFinite() && it.y.isFinite()
        }) {
            "Tọa độ điểm chạm phải là số hữu hạn"
        }

        val canvasOffsets = rawPoints.map { Offset(it.x, it.y) }
        if (canvasOffsets.any { !transformer.contains(it) }) return null

        // Giữ điểm đầu, bỏ các điểm trung gian quá gần điểm đã giữ gần nhất.
        val filtered = mutableListOf<CanvasStrokePoint>()
        filtered += rawPoints.first()

        for (index in 1 until rawPoints.lastIndex) {
            val candidate = rawPoints[index]
            val previous = filtered.last()
            if (distance(candidate.x, candidate.y, previous.x, previous.y) >= minDistancePx) {
                filtered += candidate
            }
        }

        // Luôn giữ chính xác điểm cuối để không làm sai endpoint.
        val last = rawPoints.last()
        val previous = filtered.last()
        if (distance(last.x, last.y, previous.x, previous.y) > 0f) {
            filtered += last
        } else {
            filtered[filtered.lastIndex] = last
        }

        if (filtered.size < 2) return null

        val normalizedPoints = filtered.map { point ->
            val source = transformer.toSource(Offset(point.x, point.y))
            StrokePoint(source.x, source.y)
        }

        var totalLength = 0f
        for (index in 1 until normalizedPoints.size) {
            val previous = normalizedPoints[index - 1]
            val current = normalizedPoints[index]
            totalLength += distance(previous.x, previous.y, current.x, current.y)
        }

        if (totalLength <= 0f || !totalLength.isFinite()) return null

        val minX = normalizedPoints.minOf { it.x }
        val minY = normalizedPoints.minOf { it.y }
        val maxX = normalizedPoints.maxOf { it.x }
        val maxY = normalizedPoints.maxOf { it.y }

        return AnalyzedUserStroke(
            order = order,
            points = normalizedPoints,
            startPoint = normalizedPoints.first(),
            endPoint = normalizedPoints.last(),
            length = totalLength,
            bounds = Rect(minX, minY, maxX, maxY),
            durationMillis = (filtered.last().timestampMillis -
                filtered.first().timestampMillis).coerceAtLeast(0L)
        )
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        return sqrt(dx * dx + dy * dy)
    }

    private const val DEFAULT_MIN_DISTANCE_PX = 2f
}
