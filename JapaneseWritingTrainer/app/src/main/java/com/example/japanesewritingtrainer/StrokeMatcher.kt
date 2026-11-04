package com.example.japanesewritingtrainer

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Kết quả so sánh một nét người dùng với nét SVG tham chiếu.
 *
 * score nằm trong [0, 100]. directionCorrect cho biết nét có đúng chiều
 * và nằm trong dung sai đầu/cuối hay không. directionReversed nhận diện
 * trường hợp người dùng đi từ cuối nét mẫu về đầu nét mẫu.
 */
data class StrokeMatchResult(
    val score: Float,
    val accepted: Boolean,
    val directionCorrect: Boolean,
    val directionReversed: Boolean,
    val averagePathDistance: Float,
    val startDistance: Float,
    val endDistance: Float,
    val lengthRatio: Float
)

/**
 * So khớp hai đường đi đã được chuẩn hóa về cùng hệ tọa độ SVG.
 *
 * Điểm hình dạng được tính riêng trước. Nếu phát hiện nét bị viết ngược,
 * điểm cuối cùng bị giới hạn để không tạo cảm giác một nét sai chiều vẫn
 * đạt điểm cao chỉ vì hình học của nó giống mẫu.
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

        // So sánh cả hai đầu theo chiều ngược để phân biệt viết ngược
        // với trường hợp viết đúng chiều nhưng bị lệch vị trí.
        val reverseStartDistance = distance(reference.startPoint, user.endPoint)
        val reverseEndDistance = distance(reference.endPoint, user.startPoint)
        val forwardEndpointError = startDistance + endDistance
        val reverseEndpointError = reverseStartDistance + reverseEndDistance

        val referenceLength = reference.length.coerceAtLeast(EPSILON)
        val lengthRatio = user.length / referenceLength
        val lengthError = abs(1f - lengthRatio)

        val pathError = (averageDistance / diagonal).coerceIn(0f, 1f)
        val startError = (startDistance / diagonal).coerceIn(0f, 1f)
        val endError = (endDistance / diagonal).coerceIn(0f, 1f)
        val normalizedLengthError = lengthError.coerceIn(0f, 1f)

        // Điểm hình học trước khi áp dụng hình phạt sai chiều.
        val shapeScore = (
            (1f - pathError) * 45f +
                (1f - startError) * 20f +
                (1f - endError) * 20f +
                (1f - normalizedLengthError) * 15f
            ).coerceIn(0f, 100f)

        val endpointTolerance = diagonal * config.maxEndpointErrorRatio
        val directionReversed =
            reverseEndpointError + config.directionDetectionMargin < forwardEndpointError * 0.5f

        val directionCorrect =
            !directionReversed &&
                startDistance <= endpointTolerance &&
                endDistance <= endpointTolerance

        // Sai chiều luôn bị giới hạn điểm, bất kể độ giống hình dạng.
        val finalScore = if (directionReversed) {
            minOf(shapeScore, config.reversedDirectionScoreCap)
        } else {
            shapeScore
        }

        val accepted = finalScore >= config.acceptanceScore && directionCorrect

        return StrokeMatchResult(
            score = finalScore,
            accepted = accepted,
            directionCorrect = directionCorrect,
            directionReversed = directionReversed,
            averagePathDistance = averageDistance,
            startDistance = startDistance,
            endDistance = endDistance,
            lengthRatio = lengthRatio
        )
    }

    /** Lấy mẫu đều theo chiều dài polyline. Trả về đúng sampleCount điểm. */
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
        val maxEndpointErrorRatio: Float = 0.12f,
        val reversedDirectionScoreCap: Float = 49f,
        val directionDetectionMargin: Float = 1f
    ) {
        init {
            require(acceptanceScore in 0f..100f)
            require(maxEndpointErrorRatio in 0f..1f)
            require(reversedDirectionScoreCap in 0f..100f)
            require(directionDetectionMargin >= 0f && directionDetectionMargin.isFinite())
        }
    }

    private const val EPSILON = 0.0001f
}
