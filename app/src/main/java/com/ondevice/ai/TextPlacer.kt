package com.ondevice.ai

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.palette.graphics.Palette
import kotlin.math.max
import kotlin.math.min

class TextPlacer {

    /**
     * 발견된 빈 공간에 텍스트를 배치합니다.
     * @param bitmap 원본 이미지
     * @param emptySpace 텍스트를 배치할 빈 공간
     * @param text 배치할 텍스트
     * @return 텍스트가 배치된 이미지
     */
    fun placeText(
        bitmap: Bitmap,
        emptySpace: EmptySpaceFinder.EmptySpace,
        text: String
    ): Bitmap {
        val resultBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(resultBitmap)

        // 빈 공간 영역에서 배경색 추출
        val backgroundColors = extractBackgroundColors(bitmap, emptySpace.rect)

        // 추출한 배경색을 기반으로 텍스트 색상과 스타일 결정
        val textStyle = determineTextStyle(backgroundColors)

        // 텍스트 크기 계산 (빈 공간 크기에 맞춤)
        val maxTextSize = calculateMaxTextSize(
            canvas, text, emptySpace.rect.width(), emptySpace.rect.height(), textStyle.paint
        )

        // 텍스트 그리기
        textStyle.paint.textSize = maxTextSize
        drawTextInRect(canvas, text, emptySpace.rect, textStyle.paint)

        return resultBitmap
    }

    /**
     * 지정된 영역의 배경색을 추출합니다.
     */
    private fun extractBackgroundColors(bitmap: Bitmap, rect: RectF): Palette {
        val left = max(0, rect.left.toInt())
        val top = max(0, rect.top.toInt())
        val right = min(bitmap.width, rect.right.toInt())
        val bottom = min(bitmap.height, rect.bottom.toInt())

        // 지정 영역 추출
        val regionBitmap = Bitmap.createBitmap(
            bitmap,
            left, top,
            right - left,
            bottom - top
        )

        // Palette API를 사용하여 주요 색상 추출
        return Palette.from(regionBitmap).generate()
    }

    /**
     * 배경색을 분석하여 최적의 텍스트 스타일을 결정합니다.
     */
    private fun determineTextStyle(palette: Palette): TextStyle {
        // 배경이 어두운지 판단
        val dominantSwatch = palette.dominantSwatch
        val isDarkBackground = dominantSwatch?.let { swatch ->
            val luminance = 0.299 * Color.red(swatch.rgb) +
                    0.587 * Color.green(swatch.rgb) +
                    0.114 * Color.blue(swatch.rgb)
            luminance < 128
        } ?: true

        // 텍스트 색상 결정 (대비가 높도록)
        val textColor = if (isDarkBackground) Color.WHITE else Color.BLACK

        // 텍스트 스타일 설정
        val textPaint = Paint().apply {
            color = textColor
            isAntiAlias = true
            textAlign = Paint.Align.CENTER

            // 배경이 복잡하면 외곽선 추가
            if (palette.swatches.size > 3) {
                style = Paint.Style.FILL_AND_STROKE
                strokeWidth = 2f
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND

                // 배경 색상에 따라 외곽선 색상 조정
                // Paint 클래스에서는 외곽선 색상을 별도로 설정하지 않고,
                // color 속성이 그리기 색상과 외곽선 색상 모두에 적용됨
                color = textColor
            } else {
                style = Paint.Style.FILL
            }

            // 폰트 설정
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        return TextStyle(textPaint, isDarkBackground)
    }

    /**
     * 지정된 영역에 맞는 최대 텍스트 크기를 계산합니다.
     */
    private fun calculateMaxTextSize(
        canvas: Canvas,
        text: String,
        maxWidth: Float,
        maxHeight: Float,
        paint: Paint
    ): Float {
        var textSize = 12f
        paint.textSize = textSize

        // 텍스트 크기를 점진적으로 증가시키며 최적 크기 찾기
        val lines = text.split("\n")
        var fits = true

        while (fits) {
            var maxLineWidth = 0f

            // 모든 라인이 너비에 맞는지 확인
            for (line in lines) {
                val width = paint.measureText(line)
                maxLineWidth = max(maxLineWidth, width)
            }

            // 높이 계산
            val lineHeight = paint.fontSpacing
            val totalHeight = lineHeight * lines.size

            // 텍스트가 영역을 벗어나는지 확인
            fits = maxLineWidth <= maxWidth * 0.9f && totalHeight <= maxHeight * 0.9f

            if (fits) {
                textSize += 1f
                paint.textSize = textSize
            }
        }

        // 마지막으로 증가시킨 크기 (맞지 않는)를 되돌림
        return max(12f, textSize - 1f)
    }

    /**
     * 지정된 영역에 텍스트를 중앙 정렬하여 그립니다.
     */
    private fun drawTextInRect(
        canvas: Canvas,
        text: String,
        rect: RectF,
        paint: Paint
    ) {
        val lines = text.split("\n")
        val lineHeight = paint.fontSpacing
        val totalHeight = lineHeight * lines.size

        // 세로 중앙 정렬
        var y = rect.centerY() - (totalHeight / 2) + lineHeight / 2

        // 각 라인 그리기
        for (line in lines) {
            canvas.drawText(line, rect.centerX(), y, paint)
            y += lineHeight
        }
    }

    data class TextStyle(
        val paint: Paint,
        val isDarkBackground: Boolean
    )

    companion object {
        private const val TAG = "TextPlacer"
    }
}