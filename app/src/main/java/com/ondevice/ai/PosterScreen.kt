package com.ondevice.ai

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ondevice.ai.ModelDetection
import com.ondevice.ai.ModelPerformance
import com.ondevice.ai.PosterUiState
import com.ondevice.ai.PosterViewModel

@Composable
fun PosterScreen(viewModel: PosterViewModel) {
    val context = LocalContext.current

    // 1) 선택된 이미지 URI 상태
    var imageUri by remember { mutableStateOf<Uri?>(null) }

    // 2) 갤러리에서 이미지를 가져오는 런처
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        imageUri = uri
    }

    // 3) URI가 설정되면 탐지 트리거 (한 번만)
    imageUri?.let { uri ->
        LaunchedEffect(uri) {
            viewModel.processImage(context, uri)
        }
    }

    // 4) ViewModel 상태 구독
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 이미지 선택 버튼
        Button(
            onClick = { launcher.launch("image/*") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("이미지 선택")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 아직 이미지가 선택되지 않았을 때
        if (imageUri == null) {
            Text(
                text = "포스터용 이미지를 선택해주세요.",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            return@Column
        }

        // 로딩 / 에러 / 성공 화면 전환
        when (uiState) {
            is PosterUiState.Initial,
            is PosterUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
            }

            is PosterUiState.Error -> {
                Text(
                    text = (uiState as PosterUiState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }

            is PosterUiState.Success -> {
                SuccessContent(state = uiState as PosterUiState.Success)
            }
        }
    }
}

@Composable
private fun SuccessContent(state: PosterUiState.Success) {
    var selectedTab by remember { mutableStateOf(0) }
    // “최종 포스터” 탭 제거
    val tabs = listOf("원본", "객체 인식", "성능 비교")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // 탭 구성
        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { idx, title ->
                Tab(
                    selected = selectedTab == idx,
                    onClick = { selectedTab = idx }
                ) {
                    Text(title, modifier = Modifier.padding(16.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        when (selectedTab) {
            // 1) 원본
            0 -> {
                Image(
                    bitmap = state.originalBitmap.asImageBitmap(),
                    contentDescription = "원본 이미지",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp)
                )
            }

            // 2) 객체 인식 결과를 크게
            1 -> {
                Text(
                    "모델별 객체 인식 결과",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 16.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    items(state.modelVisualizations) { mv: ModelDetection ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillParentMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            Text(
                                mv.modelName.removeSuffix(".tflite"),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Image(
                                bitmap = mv.bitmap.asImageBitmap(),
                                contentDescription = mv.modelName,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(400.dp) // 높이 400dp로 확대
                            )
                        }
                    }
                }
            }

            // 3) 성능 비교
            2 -> {
                PerformanceTable(metrics = state.performanceMetrics)
            }
        }
    }
}

@Composable
private fun PerformanceTable(metrics: List<ModelPerformance>) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text("모델별 성능 비교", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        metrics.forEach { m ->
            Text(
                "${m.modelName.removeSuffix(".tflite")}: ${m.inferenceTimeMs} ms / ${m.memoryUsageKb} KB",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}
