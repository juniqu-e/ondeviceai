package com.ondevice.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ondevice.ai.BitmapUtils
import com.ondevice.ai.EmptySpaceFinder
import com.ondevice.ai.TextPlacer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// 1) 모델 성능 지표
data class ModelPerformance(
    val modelName: String,
    val inferenceTimeMs: Long,
    val memoryUsageKb: Long
)

// 2) 모델별 시각화 결과
data class ModelDetection(
    val modelName: String,
    val bitmap: Bitmap
)

sealed class PosterUiState {
    object Initial : PosterUiState()
    object Loading : PosterUiState()
    data class Success(
        val originalBitmap: Bitmap,
        val detectionResults: List<ObjectDetector.DetectionResult>,
        val visualizedBitmap: Bitmap,
        val finalPoster: Bitmap,
        val performanceMetrics: List<ModelPerformance>,
        val modelVisualizations: List<ModelDetection>
    ) : PosterUiState()
    data class Error(val message: String) : PosterUiState()
}

class PosterViewModel : ViewModel() {

    // detectors 는 초기에는 비어있다가 processImage() 에서 한 번만 초기화
    private var detectors: List<ObjectDetector> = emptyList()

    private val emptySpaceFinder = EmptySpaceFinder()
    private val textPlacer = TextPlacer()

    private val _uiState = MutableStateFlow<PosterUiState>(PosterUiState.Initial)
    val uiState: StateFlow<PosterUiState> = _uiState.asStateFlow()

    /** Context를 이용해 한 번만 Detector 객체들 생성 */
    private fun initializeDetectors(context: Context) {
        if (detectors.isEmpty()) {
            detectors = listOf(
                ObjectDetector(context, "efficientdet_lite1_384_ptq.tflite",               "coco_labels.txt"),
                ObjectDetector(context, "efficientdet_lite2_448_ptq.tflite",               "coco_labels.txt"),
                ObjectDetector(context, "efficientdet_lite3_512_ptq.tflite",               "coco_labels.txt"),
                ObjectDetector(context, "ssd_mobilenet_v1.tflite",                         "coco_labels.txt"),
                ObjectDetector(context, "ssd_mobilenet_v2_coco_quant_postprocess.tflite",   "coco_labels.txt"),
                ObjectDetector(context, "ssdlite_mobiledet_coco_qat_postprocess.tflite",    "coco_labels.txt"),
                ObjectDetector(context, "tf2_ssd_mobilenet_v2_coco17_ptq.tflite",           "coco_labels.txt")
            )
        }
    }

    /** 비트맵 위에 BoundingBox + 라벨 그리기 */
    private fun annotateDetections(
        bitmap: Bitmap,
        results: List<ObjectDetector.DetectionResult>
    ): Bitmap {
        val annotated = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(annotated)

        val boxPaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            color = Color.RED
            isAntiAlias = true
        }
        val textPaint = Paint().apply {
            color = Color.RED
            textSize = 36f
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        results.forEach { res ->
            // 박스
            canvas.drawRect(res.boundingBox, boxPaint)
            // 라벨 + 신뢰도
            canvas.drawText(
                "${res.label} ${(res.confidence * 100).toInt()}%",
                res.boundingBox.left,
                (res.boundingBox.top - 8f).coerceAtLeast(textPaint.textSize),
                textPaint
            )
        }
        return annotated
    }

    /**
     * 이미지 처리
     * 1) Detector 초기화
     * 2) 비트맵 로드
     * 3) 모델별 detect → 성능 측정 → annotate
     * 4) 첫 모델은 빈공간 찾기 → 텍스트 배치
     * 5) UI 업데이트
     */
    fun processImage(
        context: Context,
        imageUri: Uri,
        sampleText: String = "GALLERY\nEXHIBITION\nAPRIL 15-20"
    ) {
        viewModelScope.launch {
            _uiState.value = PosterUiState.Loading

            // 1) 한 번만 Detector 초기화
            initializeDetectors(context)

            // 2) 비트맵 로드
            val bitmap = withContext(Dispatchers.IO) {
                BitmapUtils.loadBitmapFromUri(context, imageUri)
            } ?: run {
                _uiState.value = PosterUiState.Error("이미지를 불러올 수 없습니다.")
                return@launch
            }

            val metrics = mutableListOf<ModelPerformance>()
            val visualizations = mutableListOf<ModelDetection>()

            var primaryDetections: List<ObjectDetector.DetectionResult> = emptyList()
            var visualizedBitmap: Bitmap = bitmap
            var finalPoster: Bitmap = bitmap

            // 3) 모든 모델을 순회하며 detect + annotate
            detectors.forEachIndexed { idx, detector ->
                // 메모리·시간 측정
                val rt = Runtime.getRuntime()
                System.gc()
                val beforeKb = (rt.totalMemory() - rt.freeMemory()) / 1024

                val startNs = System.nanoTime()
                val results = withContext(Dispatchers.Default) {
                    detector.detect(bitmap)
                }
                val elapsedMs = (System.nanoTime() - startNs) / 1_000_000
                val afterKb = (rt.totalMemory() - rt.freeMemory()) / 1024

                metrics += ModelPerformance(
                    modelName     = detector.modelName,
                    inferenceTimeMs = elapsedMs,
                    memoryUsageKb   = (afterKb - beforeKb).coerceAtLeast(0L)
                )

                // 시각화 비트맵 생성
                val detBmp = annotateDetections(bitmap, results)
                visualizations += ModelDetection(detector.modelName, detBmp)

                // 첫 번째 모델은 포스터 로직에 사용
                if (idx == 0) {
                    primaryDetections = results
                    val spaces = emptySpaceFinder.findEmptySpaces(bitmap, results)
                    visualizedBitmap = emptySpaceFinder.visualizeEmptySpaces(bitmap, spaces)
                    spaces.firstOrNull()?.let { best ->
                        finalPoster = textPlacer.placeText(bitmap, best, sampleText)
                    }
                }
            }

            // 4) UI 업데이트
            _uiState.value = PosterUiState.Success(
                originalBitmap     = bitmap,
                detectionResults   = primaryDetections,
                visualizedBitmap   = visualizedBitmap,
                finalPoster        = finalPoster,
                performanceMetrics = metrics,
                modelVisualizations= visualizations
            )
        }
    }
}
