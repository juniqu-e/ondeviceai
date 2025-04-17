package com.ondevice.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ondevice.ai.PosterUiState
import com.ondevice.ai.PosterViewModel

@Composable
fun PosterScreen(viewModel: PosterViewModel) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    // 이미지 선택을 위한 런처
    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.processImage(context, it)
        }
    }

    // ViewModel 초기화
    LaunchedEffect(Unit) {
        viewModel.initializeObjectDetector(context)
    }

    Scaffold { padding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "AI 포스터 제작기",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Button(
                    onClick = { imagePickerLauncher.launch("image/*") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("이미지 선택")
                }

                Spacer(modifier = Modifier.height(16.dp))

                // UI 상태에 따라 다른 내용 표시
                when (val state = uiState) {
                    is PosterUiState.Initial -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("이미지를 선택하세요")
                        }
                    }

                    is PosterUiState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("이미지 분석 중...")
                            }
                        }
                    }

                    is PosterUiState.Success -> {
                        ResultContent(state)
                    }

                    is PosterUiState.Error -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "오류 발생",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(state.message)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ResultContent(state: PosterUiState.Success) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("원본", "객체 인식", "최종 포스터")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // 탭 선택
        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 선택된 탭에 따라 다른 내용 표시
        when (selectedTab) {
            0 -> {
                // 원본 이미지
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = state.originalBitmap.asImageBitmap(),
                        contentDescription = "원본 이미지",
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "감지된 객체: ${state.detectionResults.size}개",
                    style = MaterialTheme.typography.titleMedium
                )

                // 감지된 객체 목록
                state.detectionResults.forEachIndexed { index, result ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${index + 1}.")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "${result.label} (신뢰도: ${String.format("%.2f", result.confidence)})",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            1 -> {
                // 객체 인식 결과 시각화 이미지
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = state.visualizedBitmap.asImageBitmap(),
                        contentDescription = "객체 인식 결과",
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "빨간색: 가장 적합한 빈 공간\n초록색, 파란색: 다른 빈 공간 후보",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            2 -> {
                // 최종 포스터
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = state.finalPoster.asImageBitmap(),
                        contentDescription = "최종 포스터",
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Button(onClick = { /* 포스터 저장 기능 */ }) {
                        Text("포스터 저장")
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Button(onClick = { /* 포스터 공유 기능 */ }) {
                        Text("포스터 공유")
                    }
                }
            }
        }
    }
}