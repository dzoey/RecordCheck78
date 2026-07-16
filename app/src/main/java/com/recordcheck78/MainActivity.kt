package com.recordcheck78

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Main activity — hosts the Jetpack Compose UI with navigation between
 * camera screen and donation list screen.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                App()
            }
        }
    }
}

@Composable
fun App() {
    val viewModel: AppViewModel = viewModel()
    var showList by remember { mutableStateOf(false) }

    if (showList) {
        DonationListScreen(
            viewModel = viewModel,
            onBack = { showList = false }
        )
    } else {
        CameraScreen(
            viewModel = viewModel,
            onNavigateToList = { showList = true }
        )
    }
}