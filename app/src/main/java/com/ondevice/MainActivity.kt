package com.ondevice

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.ondevice.ai.PosterViewModel
import com.ondevice.ui.PosterScreen
import com.ondevice.ui.theme.OnDeviceTheme

class MainActivity : ComponentActivity() {

    private val viewModel: PosterViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OnDeviceTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PosterScreen(viewModel)
                }
            }
        }
    }
}