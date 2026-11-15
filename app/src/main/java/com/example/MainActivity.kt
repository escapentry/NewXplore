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
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.ui.ExploreDScreen
import com.example.ui.components.OrbitLoadingScreen
import com.example.ui.theme.ExploreDTheme
import kotlinx.coroutines.delay

enum class LaunchStage {
  ICON_SPLASH,
  ORBIT_LOADING,
  MAIN,
}

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
        ExploreDLaunchHost()
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

@Composable
fun ExploreDLaunchHost(
  iconSplashDurationMs: Long = 600L,
  orbitLoadingDurationMs: Long = 1800L,
  mainContent: @Composable () -> Unit = { ExploreDScreen() },
) {
  var stage by remember { mutableStateOf(LaunchStage.ICON_SPLASH) }
  var mountMainContent by remember { mutableStateOf(false) }

  LaunchedEffect(iconSplashDurationMs, orbitLoadingDurationMs) {
    withFrameNanos { }
    delay(iconSplashDurationMs)

    // Enter second stage (OrbitLoadingScreen) before heavy main UI composition
    // so the orbital animation renders immediately without cold-start frame stalls.
    stage = LaunchStage.ORBIT_LOADING
    withFrameNanos { }

    val preWarmDelay = (orbitLoadingDurationMs / 2L).coerceAtLeast(1L)
    delay(preWarmDelay)
    mountMainContent = true

    val remainingOrbitDelay = (orbitLoadingDurationMs - preWarmDelay).coerceAtLeast(1L)
    delay(remainingOrbitDelay)
    stage = LaunchStage.MAIN
  }

  Box(modifier = Modifier.fillMaxSize()) {
    if (mountMainContent || stage == LaunchStage.MAIN) {
      mainContent()
    }

    AnimatedVisibility(
      visible = stage != LaunchStage.MAIN,
      enter = EnterTransition.None,
      exit = fadeOut(animationSpec = tween(durationMillis = 320)),
    ) {
      OrbitLoadingScreen()
    }

    AnimatedVisibility(
      visible = stage == LaunchStage.ICON_SPLASH,
      exit = fadeOut(animationSpec = tween(durationMillis = 240)),
    ) {
      LaunchIconSplashScreen()
    }
  }
}

@Composable
fun LaunchIconSplashScreen(
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier
      .fillMaxSize()
      .background(ComposeColor(0xFF060F14))
      .testTag("launch_icon_splash"),
    contentAlignment = Alignment.Center,
  ) {
    Image(
      painter = painterResource(id = R.drawable.img_explored_icon_fg),
      contentDescription = stringResource(id = R.string.app_name),
      contentScale = ContentScale.Fit,
      modifier = Modifier
        .size(220.dp)
        .clip(RoundedCornerShape(42.dp)),
    )
  }
}
