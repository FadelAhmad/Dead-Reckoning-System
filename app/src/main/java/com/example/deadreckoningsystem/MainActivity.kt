package com.example.deadreckoningsystem

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.deadreckoningsystem.ui.NavigationScreen
import com.example.deadreckoningsystem.ui.theme.DeadReckoningTheme
import com.example.deadreckoningsystem.ui.theme.SlateDarkBg

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DeadReckoningTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = SlateDarkBg
                ) {
                    NavigationScreen()
                }
            }
        }
    }
}
