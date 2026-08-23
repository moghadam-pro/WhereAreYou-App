package com.whereareyou.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.whereareyou.app.platform.AppContainer
import com.whereareyou.app.platform.WhereAreYouApplication
import com.whereareyou.app.ui.theme.WhereAreYouTheme

class MainActivity : ComponentActivity() {

    private val container: AppContainer
        get() = (application as WhereAreYouApplication).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WhereAreYouTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    WhereAreYouApp(container = container)
                }
            }
        }
    }
}
