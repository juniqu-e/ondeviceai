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

class ObjectDetector(
    private val context: Context,
    /** 모델 파일 이름 (예: "efficientdet_lite1_384_ptq.tflite") */
    val modelName: String,
    /** 라벨 파일 이름 ("coco_labels.txt") */
    private val labelsName: String = "coco_labels.txt"
) {
    private val interpreter: Interpreter
    private val imageProcessor: ImageProcessor
    private val labels: List<String>

    init {
        // 1) Interpreter 로드
        val options = Interpreter.Options().setNumThreads(4)
        interpreter = Interpreter(
            FileUtil.loadMappedFile(context, modelName),
            options
        )

        // 2) 입력 텐서 shape 조회 ([1, H, W, C])
        val inShape = interpreter.getInputTensor(0).shape()
        val inputH = inShape[1]
        val inputW = inShape[2]
        Log.d(TAG, "[$modelName] input tensor shape = ${inShape.joinToString()}")

        // 3) 동적 입력 크기에 맞춘 ImageProcessor
        imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(inputH, inputW, ResizeOp.ResizeMethod.BILINEAR))
            .build()

        // 4) 라벨 로드
        labels = FileUtil.loadLabels(context, labelsName)
    }

    data class DetectionResult(
        val id: Int,
        val label: String,
        val confidence: Float,
        val boundingBox: RectF
    )

    fun detect(bitmap: Bitmap): List<DetectionResult> {
        // A) 입력 준비
        val tensorImage    = TensorImage.fromBitmap(bitmap)
        val processedImage = imageProcessor.process(tensorImage)
        val inputBuffer    = processedImage.buffer

        // B) 출력 버퍼를 동적으로 생성
        val outputs = mutableMapOf<Int, Any>()
        var boxesRaw: Array<Array<FloatArray>>? = null
        var classesRaw: Array<FloatArray>?     = null
        var scoresRaw: Array<FloatArray>?      = null
        var countRaw: FloatArray?              = null
        var boxesAssigned = false

        for (i in 0 until interpreter.outputTensorCount) {
            val shape = interpreter.getOutputTensor(i).shape()
            when {
                // [1, N, 4] → 바운딩 박스
                shape.size == 3 && shape[2] == 4 && !boxesAssigned -> {
                    val raw = Array(shape[0]) { Array(shape[1]) { FloatArray(shape[2]) } }
                    outputs[i] = raw
                    boxesRaw = raw
                    boxesAssigned = true
                }
                // [1, N] → 클래스 or 스코어
                shape.size == 2 -> {
                    val raw = Array(shape[0]) { FloatArray(shape[1]) }
                    outputs[i] = raw
                    if (classesRaw == null) classesRaw = raw else scoresRaw = raw
                }
                // [1] → 검출 개수
                shape.size == 1 -> {
                    val raw = FloatArray(shape[0])
                    outputs[i] = raw
                    countRaw = raw
                }
                else -> {
                    // 기타 텐서는 무시
                }
            }
        }

        // C) 추론 실행
        interpreter.runForMultipleInputsOutputs(arrayOf(inputBuffer), outputs)

        // D) 결과 파싱
        val detectedCount = countRaw?.get(0)?.toInt() ?: boxesRaw?.get(0)?.size ?: 0
        val results = mutableListOf<DetectionResult>()
        for (i in 0 until detectedCount) {
            val conf = scoresRaw?.get(0)?.get(i) ?: continue
            if (conf < 0.5f) continue

            val clsIdx = classesRaw?.get(0)?.get(i)?.toInt() ?: continue
            val label  = labels.getOrElse(clsIdx) { "Unknown" }

            val coords = boxesRaw?.get(0)?.get(i) ?: continue
            val box = RectF(
                coords[1] * bitmap.width,
                coords[0] * bitmap.height,
                coords[3] * bitmap.width,
                coords[2] * bitmap.height
            )
            results.add(DetectionResult(clsIdx, label, conf, box))
        }

        return results
    }

    fun close() {
        interpreter.close()
    }

    companion object {
        private const val TAG = "ObjectDetector"
    }
}
