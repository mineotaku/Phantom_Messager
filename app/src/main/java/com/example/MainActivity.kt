package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.phantom.ui.PhantomViewModel
import com.example.phantom.ui.screens.MainScreen
import com.example.ui.theme.PhantomTheme

import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PhantomTheme {
                val phantomViewModel: PhantomViewModel = hiltViewModel()
                MainScreen(viewModel = phantomViewModel)
            }
        }
    }
}
