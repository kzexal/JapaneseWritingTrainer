package com.example.japanesewritingtrainer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.math.min

/**
 * Chuyển tọa độ giữa viewBox của SVG và vùng Canvas hiển thị.
 *
 * Dùng phép fit theo tỷ lệ (giữ nguyên aspect ratio), sau đó căn giữa
 * nội dung. Nhờ vậy chữ không bị méo khi Canvas không vuông.
 */
data class CoordinateTransformer private constructor(
    val scale: Float,
    val offset: Offset,
    val sourceWidth: Float,
    val sourceHeight: Float
) {
    val contentBounds: Rect
        get() = Rect(
            left = offset.x,
            top = offset.y,
            right = offset.x + sourceWidth * scale,
            bottom = offset.y + sourceHeight * scale
        )

    /** Tọa độ từ hệ SVG/viewBox sang pixel Canvas. */
    fun toCanvas(sourcePoint: Offset): Offset = Offset(
        x = offset.x + sourcePoint.x * scale,
        y = offset.y + sourcePoint.y * scale
    )

    /** Tọa độ từ pixel Canvas về hệ SVG/viewBox. */
    fun toSource(canvasPoint: Offset): Offset = Offset(
        x = (canvasPoint.x - offset.x) / scale,
        y = (canvasPoint.y - offset.y) / scale
    )

    fun contains(canvasPoint: Offset): Boolean = contentBounds.contains(canvasPoint)

    companion object {
        fun fit(
            sourceWidth: Float,
            sourceHeight: Float,
            canvasSize: Size
        ): CoordinateTransformer {
            require(sourceWidth > 0f && sourceHeight > 0f) {
                "Kích thước viewBox phải lớn hơn 0"
            }
            require(canvasSize.width > 0f && canvasSize.height > 0f) {
                "Kích thước Canvas phải lớn hơn 0"
            }

            val scale = min(
                canvasSize.width / sourceWidth,
                canvasSize.height / sourceHeight
            )
            val renderedWidth = sourceWidth * scale
            val renderedHeight = sourceHeight * scale

            return CoordinateTransformer(
                scale = scale,
                offset = Offset(
                    x = (canvasSize.width - renderedWidth) / 2f,
                    y = (canvasSize.height - renderedHeight) / 2f
                ),
                sourceWidth = sourceWidth,
                sourceHeight = sourceHeight
            )
        }
    }
}
