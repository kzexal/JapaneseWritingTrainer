package com.example.japanesewritingtrainer

import kotlin.math.sqrt

/**
 * Kết quả so sánh một nét người dùng với nét SVG tham chiếu.
 *
 * score nằm trong [0, 100]. directionCorrect cho biết người dùng có
 * bắt đầu và kết thúc theo đúng chiều của nét mẫu hay không.
 */
data class StrokeMatchResult(
    val score: Float,
    val accepted: Boolean,
    val directionCorrect: Boolean,
    val averagePathDistance: Float,
    val startDistance: Float,
    val endDistance: Float,
    val lengthRatio: Float
)

/**
 * So khớp hai đường đi đã được chuẩn hóa về cùng hệ tọa độ SVG.
 *
 * Thuật toán:
 * 1. Resample hai polyline thành cùng số điểm theo chiều dài cung.
 * 2. Tính khoảng cách trung bình giữa các cặp điểm tương ứng.
 * 3. Kết hợp độ giống đường đi, hai đầu nét và tỷ lệ độ dài.
 *
 * Đây là bộ chấm điểm hình học phiên bản đầu, chưa dùng machine learning.
 */
object StrokeMatcher {

    fun match(
        reference: ParsedStroke,
        user: AnalyzedUserStroke,
        viewBoxSize: Float,
        config: Config = Config()
    ): StrokeMatchResult {
        require(viewBoxSize > 0f && viewBoxSize.isFinite()) {
            "viewBoxSize phải là số hữu hạn và lớn hơn 0"
        }
        require(config.sampleCount >= 2) { "sampleCount phải >= 2" }

        val diagonal = viewBoxSize * sqrt(2f)
        val referencePoints = resample(reference.points, config.sampleCount)
        val userPoints = resample(user.points, config.sampleCount)

        val averageDistance = referencePoints.indices.map { index ->
            distance(referencePoints[index], userPoints[index])
        }.average().toFloat()

        val startDistance = distance(reference.startPoint, user.startPoint)
        val endDistance = distance(reference.endPoint, user.endPoint)

        val referenceLength = reference.length.coerceAtLeast(EPSILON)
        val lengthRatio = user.length / referenceLength
        val lengthError = kotlin.math.abs(1f - lengthRatio)

        // Chuẩn hóa các sai số theo kích thước viewBox để dùng được với
        // dataset có kích thước khác nhau.
        val pathError = (averageDistance / diagonal).coerceIn(0f, 1f)
        val startError = (startDistance / diagonal).coerceIn(0f, 1f)
        val endError = (endDistance / diagonal).coerceIn(0f, 1f)
        val normalizedLengthError = lengthError.coerceIn(0f, 1f)

        val score = (
            (1f - pathError) * 45f +
                (1f - startError) * 20f +
                (1f - endError) * 20f +
                (1f - normalizedLengthError) * 15f
            ).coerceIn(0f, 100f)

        val directionCorrect =
            startDistance <= diagonal * config.maxEndpointErrorRatio &&
                endDistance <= diagonal * config.maxEndpointErrorRatio

        val accepted = score >= config.acceptanceScore && directionCorrect

        return StrokeMatchResult(
            score = score,
            accepted = accepted,
            directionCorrect = directionCorrect,
            averagePathDistance = averageDistance,
            startDistance = startDistance,
            endDistance = endDistance,
            lengthRatio = lengthRatio
        )
    }

    /**
     * Lấy mẫu đều theo chiều dài polyline. Trả về đúng sampleCount điểm.
     */
    internal fun resample(
        points: List<StrokePoint>,
        sampleCount: Int
    ): List<StrokePoint> {
        require(sampleCount >= 2)
        require(points.isNotEmpty())

        if (points.size == 1) return List(sampleCount) { points.first() }

        val cumulative = FloatArray(points.size)
        for (index in 1 until points.size) {
            cumulative[index] = cumulative[index - 1] +
                distance(points[index - 1], points[index])
        }

        val totalLength = cumulative.last()
        if (totalLength <= EPSILON) return List(sampleCount) { points.first() }

        return (0 until sampleCount).map { sampleIndex ->
            val target = totalLength * sampleIndex / (sampleCount - 1)
            var segment = 1
            while (segment < cumulative.lastIndex && cumulative[segment] < target) {
                segment++
            }

            val segmentStart = cumulative[segment - 1]
            val segmentLength = cumulative[segment] - segmentStart
            val fraction = if (segmentLength <= EPSILON) {
                0f
            } else {
                ((target - segmentStart) / segmentLength).coerceIn(0f, 1f)
            }

            val a = points[segment - 1]
            val b = points[segment]
            StrokePoint(
                x = a.x + (b.x - a.x) * fraction,
                y = a.y + (b.y - a.y) * fraction
            )
        }
    }

    private fun distance(a: StrokePoint, b: StrokePoint): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }

    data class Config(
        val sampleCount: Int = 32,
        val acceptanceScore: Float = 75f,
        val maxEndpointErrorRatio: Float = 0.12f
    ) {
        init {
            require(acceptanceScore in 0f..100f)
            require(maxEndpointErrorRatio in 0f..1f)
        }
    }

    private const val EPSILON = 0.0001f
}
