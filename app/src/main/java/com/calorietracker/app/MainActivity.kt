package com.calorietracker.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.calorietracker.app.ui.navigation.CalorieNavHost
import com.calorietracker.app.ui.theme.CalorieTrackerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 全面屏（Android 15+ 强制边到边，这里统一处理，10~16 表现一致）
        enableEdgeToEdge()
        setContent {
            CalorieTrackerTheme {
                CalorieNavHost()
            }
        }
    }
}
