package com.ondevice.ai

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import kotlin.math.max
import kotlin.math.min

class EmptySpaceFinder {

    /**
     * 객체 검출 결과를 기반으로 이미지에서 텍스트 배치에 적합한 빈 공간을 찾습니다.
     * @param bitmap 분석할 원본 이미지
     * @param detections 객체 검출 결과 목록
     * @param minSpaceWidth 최소 필요 너비
     * @param minSpaceHeight 최소 필요 높이
     * @return 찾은 빈 공간 목록 (가중치가 높은 순으로 정렬됨)
     */
    fun findEmptySpaces(
        bitmap: Bitmap,
        detections: List<ObjectDetector.DetectionResult>,
        minSpaceWidth: Int = 200,
        minSpaceHeight: Int = 100
    ): List<EmptySpace> {
        // 이미지를 그리드로 분할
        val gridSize = 20 // 20x20 그리드
        val gridWidth = bitmap.width / gridSize
        val gridHeight = bitmap.height / gridSize

        // 그리드 점유 맵 생성 (0: 비어있음, 1: 객체 있음)
        val occupancyGrid = Array(gridSize) { Array(gridSize) { 0 } }

        // 검출된 객체로 그리드 채우기
        for (detection in detections) {
            val box = detection.boundingBox
            val startX = max(0, (box.left / bitmap.width * gridSize).toInt())
            val startY = max(0, (box.top / bitmap.height * gridSize).toInt())
            val endX = min(gridSize - 1, (box.right / bitmap.width * gridSize).toInt())
            val endY = min(gridSize - 1, (box.bottom / bitmap.height * gridSize).toInt())

            for (y in startY..endY) {
                for (x in startX..endX) {
                    occupancyGrid[y][x] = 1
                }
            }
        }

        // 가장자리 그리드 채우기 (가장자리에는 텍스트를 넣지 않음)
        val margin = 1 // 가장자리 여백 (그리드 단위)
        for (y in 0 until gridSize) {
            for (x in 0 until gridSize) {
                if (y < margin || y >= gridSize - margin || x < margin || x >= gridSize - margin) {
                    occupancyGrid[y][x] = 1
                }
            }
        }

        // 빈 공간 후보 탐색
        val candidates = mutableListOf<EmptySpace>()

        for (startY in 0 until gridSize) {
            for (startX in 0 until gridSize) {
                if (occupancyGrid[startY][startX] == 0) {
                    // 가능한 가장 큰 직사각형 찾기
                    var maxWidth = 0
                    var maxHeight = 0

                    // 너비 확장
                    while (startX + maxWidth < gridSize && occupancyGrid[startY][startX + maxWidth] == 0) {
                        maxWidth++
                    }

                    // 높이 확장
                    outer@ for (h in 1 until gridSize - startY) {
                        for (w in 0 until maxWidth) {
                            if (occupancyGrid[startY + h][startX + w] == 1) {
                                break@outer
                            }
                        }
                        maxHeight = h + 1
                    }

                    // 최소 크기 이상인 경우만 후보로 추가
                    val realWidth = maxWidth * gridWidth
                    val realHeight = maxHeight * gridHeight

                    if (realWidth >= minSpaceWidth && realHeight >= minSpaceHeight) {
                        // 직사각형의 실제 좌표 계산
                        val rect = RectF(
                            startX * gridWidth.toFloat(),
                            startY * gridHeight.toFloat(),
                            (startX + maxWidth) * gridWidth.toFloat(),
                            (startY + maxHeight) * gridHeight.toFloat()
                        )

                        // 가중치 계산 (중앙에 가까울수록, 크기가 클수록 높은 가중치)
                        val centerWeight = calculateCenterWeight(rect, bitmap.width, bitmap.height)
                        val sizeWeight = calculateSizeWeight(rect.width(), rect.height())
                        val weight = centerWeight * 0.6f + sizeWeight * 0.4f

                        candidates.add(
                            EmptySpace(
                                rect = rect,
                                weight = weight,
                                area = rect.width() * rect.height()
                            )
                        )

                        // 이미 체크한 영역은 건너뛰기 위해 표시
                        for (y in startY until startY + maxHeight) {
                            for (x in startX until startX + maxWidth) {
                                occupancyGrid[y][x] = 2 // 2: 이미 체크한 빈 공간
                            }
                        }
                    }
                }
            }
        }

        // 가중치가 높은 순으로 정렬
        return candidates.sortedByDescending { it.weight }
    }

    /**
     * 중앙에 가까울수록 높은 가중치를 부여합니다.
     */
    private fun calculateCenterWeight(rect: RectF, imageWidth: Int, imageHeight: Int): Float {
        val centerX = imageWidth / 2f
        val centerY = imageHeight / 2f
        val rectCenterX = rect.left + rect.width() / 2
        val rectCenterY = rect.top + rect.height() / 2

        // 중앙까지의 거리 계산 (정규화)
        val distanceX = Math.abs(centerX - rectCenterX) / centerX
        val distanceY = Math.abs(centerY - rectCenterY) / centerY
        val totalDistance = (distanceX + distanceY) / 2

        return 1f - totalDistance // 거리가 가까울수록 높은 가중치
    }

    /**
     * 크기가 클수록 높은 가중치를 부여합니다.
     */
    private fun calculateSizeWeight(width: Float, height: Float): Float {
        // 예: 가로 세로 비율이 1:1에 가까울수록 높은 가중치
        val ratio = min(width, height) / max(width, height)
        return ratio
    }

    /**
     * 디버깅을 위해 찾은 빈 공간을 시각화합니다.
     */
    fun visualizeEmptySpaces(bitmap: Bitmap, emptySpaces: List<EmptySpace>, maxSpaces: Int = 3): Bitmap {
        val resultBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(resultBitmap)
        val paint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }

        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = 40f
            strokeWidth = 2f
            style = Paint.Style.FILL_AND_STROKE
        }

        // 상위 N개 빈 공간만 표시
        val topSpaces = emptySpaces.take(maxSpaces)

        for ((index, space) in topSpaces.withIndex()) {
            // 각 빈 공간마다 다른 색상 사용
            val colors = arrayOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW, Color.CYAN)
            paint.color = colors[index % colors.size]

            // 빈 공간 영역 그리기
            canvas.drawRect(space.rect, paint)

            // 가중치 정보 표시
            canvas.drawText(
                String.format("%.2f", space.weight),
                space.rect.left + 10,
                space.rect.top + 50,
                textPaint
            )
        }

        return resultBitmap
    }

    data class EmptySpace(
        val rect: RectF,
        val weight: Float,
        val area: Float
    )

    companion object {
        private const val TAG = "EmptySpaceFinder"
    }
}