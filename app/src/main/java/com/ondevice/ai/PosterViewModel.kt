package com.ondevice.ai

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PosterViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<PosterUiState>(PosterUiState.Initial)
    val uiState: StateFlow<PosterUiState> = _uiState.asStateFlow()

    private var objectDetector: ObjectDetector? = null
    private val emptySpaceFinder = EmptySpaceFinder()
    private val textPlacer = TextPlacer()

    fun initializeObjectDetector(context: Context) {
        if (objectDetector == null) {
            objectDetector = ObjectDetector(context)
        }
    }

    fun processImage(context: Context, imageUri: Uri, sampleText: String = "GALLERY\nEXHIBITION\nAPRIL 15-20") {
        viewModelScope.launch {
            try {
                _uiState.value = PosterUiState.Loading

                // 이미지 로드
                val bitmap = withContext(Dispatchers.IO) {
                    BitmapUtils.loadBitmapFromUri(context, imageUri)
                }

                if (bitmap == null) {
                    _uiState.value = PosterUiState.Error("이미지를 로드할 수 없습니다.")
                    return@launch
                }

                // 객체 감지
                val detectionResults = withContext(Dispatchers.Default) {
                    objectDetector?.detect(bitmap) ?: emptyList()
                }

                Log.d(TAG, "감지된 객체: ${detectionResults.size}")

                // 빈 공간 찾기
                val emptySpaces = withContext(Dispatchers.Default) {
                    emptySpaceFinder.findEmptySpaces(bitmap, detectionResults)
                }

                if (emptySpaces.isEmpty()) {
                    _uiState.value = PosterUiState.Error("적합한 빈 공간을 찾을 수 없습니다.")
                    return@launch
                }

                // 디버깅용 - 빈 공간 시각화
                val visualizedBitmap = emptySpaceFinder.visualizeEmptySpaces(bitmap, emptySpaces)

                // 최적의 빈 공간 선택 및 텍스트 배치
                val bestSpace = emptySpaces.first()
                val finalPoster = withContext(Dispatchers.Default) {
                    textPlacer.placeText(bitmap, bestSpace, sampleText)
                }

                _uiState.value = PosterUiState.Success(
                    originalBitmap = bitmap,
                    detectionResults = detectionResults,
                    visualizedBitmap = visualizedBitmap,
                    finalPoster = finalPoster
                )

            } catch (e: Exception) {
                Log.e(TAG, "이미지 처리 중 오류 발생", e)
                _uiState.value = PosterUiState.Error("이미지 처리 중 오류가 발생했습니다: ${e.message}")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        objectDetector?.close()
        objectDetector = null
    }

    companion object {
        private const val TAG = "PosterViewModel"
    }
}

sealed class PosterUiState {
    object Initial : PosterUiState()
    object Loading : PosterUiState()

    data class Success(
        val originalBitmap: Bitmap,
        val detectionResults: List<ObjectDetector.DetectionResult>,
        val visualizedBitmap: Bitmap,
        val finalPoster: Bitmap
    ) : PosterUiState()

    data class Error(val message: String) : PosterUiState()
}