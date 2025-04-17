package com.ondevice.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ObjectDetector(
    private val context: Context,
    private val modelName: String = "ssd_mobilenet_v1.tflite",
    private val labelsName: String = "labels.txt"
) {
    private var interpreter: Interpreter? = null
    private var labels: List<String> = emptyList()
    private val imageSizeX: Int = 300
    private val imageSizeY: Int = 300
    private val inputSize: Int = 300
    private val numDetections = 10 // 모델에 따라 달라질 수 있음

    private val imageProcessor = ImageProcessor.Builder()
        .add(ResizeOp(inputSize, inputSize, ResizeOp.ResizeMethod.BILINEAR))
        .build()

    init {
        try {
            val options = Interpreter.Options()
            options.setNumThreads(4)
            interpreter = Interpreter(FileUtil.loadMappedFile(context, modelName), options)
            labels = FileUtil.loadLabels(context, labelsName)
            Log.d(TAG, "TFLite model loaded successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading TFLite model: ${e.message}")
        }
    }

    data class DetectionResult(
        val id: Int,
        val label: String,
        val confidence: Float,
        val boundingBox: RectF
    )

    fun detect(bitmap: Bitmap): List<DetectionResult> {
        if (interpreter == null) {
            Log.e(TAG, "Interpreter is null")
            return emptyList()
        }

        // 입력 이미지 준비
        val tensorImage = TensorImage.fromBitmap(bitmap)
        val processedImage = imageProcessor.process(tensorImage)

        // 출력 버퍼 준비
        val outputLocations = Array(1) { Array(numDetections) { FloatArray(4) } } // [1][10][4]
        val outputClasses = Array(1) { FloatArray(numDetections) } // [1][10]
        val outputScores = Array(1) { FloatArray(numDetections) } // [1][10]
        val numDetectionOutput = FloatArray(1) // [1]

        val outputs = mapOf(
            0 to outputLocations,
            1 to outputClasses,
            2 to outputScores,
            3 to numDetectionOutput
        )

        // 추론 실행
        interpreter?.runForMultipleInputsOutputs(arrayOf(processedImage.buffer), outputs)

        // 결과 파싱
        val detectionResults = mutableListOf<DetectionResult>()
        val numDetectionsOutput = numDetectionOutput[0].toInt()

        for (i in 0 until numDetectionsOutput) {
            val confidence = outputScores[0][i]
            if (confidence >= 0.5f) { // 신뢰도 임계값 (0.5)
                val detectionClass = outputClasses[0][i].toInt()
                val label = if (detectionClass < labels.size) labels[detectionClass] else "Unknown"

                val location = outputLocations[0][i]
                val boundingBox = RectF(
                    location[1] * bitmap.width,
                    location[0] * bitmap.height,
                    location[3] * bitmap.width,
                    location[2] * bitmap.height
                )

                detectionResults.add(
                    DetectionResult(
                        id = detectionClass,
                        label = label,
                        confidence = confidence,
                        boundingBox = boundingBox
                    )
                )
            }
        }

        return detectionResults
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }

    companion object {
        private const val TAG = "ObjectDetector"
    }
}