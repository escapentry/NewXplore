package com.example

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.ui.ExploreDScreen
import com.example.ui.components.OrbitLoadingScreen
import com.example.ui.theme.ExploreDTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
    )
    super.onCreate(savedInstanceState)
    enableHighestRefreshRate()

    setContent {
      ExploreDTheme {
        var showOrbitSplash by remember { mutableStateOf(true) }

        LaunchedEffect(Unit) {
          delay(1350L)
          showOrbitSplash = false
        }

        Box(modifier = Modifier.fillMaxSize()) {
          ExploreDScreen()
          AnimatedVisibility(
            visible = showOrbitSplash,
            exit = fadeOut(animationSpec = tween(durationMillis = 320)),
          ) {
            OrbitLoadingScreen()
          }
        }
      }
    }
  }

  private fun enableHighestRefreshRate() {
    runCatching {
      val currentDisplay: Display? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
          display
        } else {
          @Suppress("DEPRECATION")
          windowManager.defaultDisplay
        }

      if (currentDisplay != null) {
        val currentMode = currentDisplay.mode
        val bestMode = currentDisplay.supportedModes
          .filter {
            it.physicalWidth == currentMode.physicalWidth &&
              it.physicalHeight == currentMode.physicalHeight
          }
          .maxByOrNull { it.refreshRate }

        val lp = window.attributes
        if (bestMode != null) {
          lp.preferredDisplayModeId = bestMode.modeId
          lp.preferredRefreshRate = bestMode.refreshRate
        }
        window.attributes = lp
      }
    }
  }
}
