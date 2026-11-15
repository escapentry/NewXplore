package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.OrbitCyan
import com.example.ui.theme.OrbitIndigo
import com.example.ui.theme.OrbitMint
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun OrbitLoadingScreen(
  modifier: Modifier = Modifier,
) {
  val infiniteTransition = rememberInfiniteTransition(label = "orbit_loader")
  val outerAngle by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 360f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 4800, easing = LinearEasing),
      repeatMode = RepeatMode.Restart,
    ),
    label = "outer_angle",
  )
  val innerAngle by infiniteTransition.animateFloat(
    initialValue = 360f,
    targetValue = 0f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 3200, easing = LinearEasing),
      repeatMode = RepeatMode.Restart,
    ),
    label = "inner_angle",
  )
  val pulse by infiniteTransition.animateFloat(
    initialValue = 0.72f,
    targetValue = 1.08f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 1400, easing = LinearEasing),
      repeatMode = RepeatMode.Reverse,
    ),
    label = "core_pulse",
  )

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(ObsidianVoid)
      .testTag("orbit_loading_screen"),
    contentAlignment = Alignment.Center,
  ) {
    // Subtle deep-space ambient gradient
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(
          Brush.radialGradient(
            colors = listOf(
              OrbitCyan.copy(alpha = 0.12f),
              OrbitIndigo.copy(alpha = 0.07f),
              Color.Transparent,
            ),
          ),
        ),
    )

    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
      modifier = Modifier.padding(32.dp),
    ) {
      Canvas(modifier = Modifier.size(180.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxRadius = size.minDimension * 0.44f

        // Outer elliptical orbit (Cyan)
        rotate(degrees = -22f, pivot = center) {
          drawOval(
            color = OrbitCyan.copy(alpha = 0.25f),
            topLeft = Offset(center.x - maxRadius, center.y - maxRadius * 0.54f),
            size = Size(maxRadius * 2f, maxRadius * 1.08f),
            style = Stroke(width = 2.dp.toPx()),
          )
          val rad = Math.toRadians(outerAngle.toDouble())
          val satX = center.x + (maxRadius * cos(rad)).toFloat()
          val satY = center.y + (maxRadius * 0.54f * sin(rad)).toFloat()
          drawCircle(
            color = OrbitCyan.copy(alpha = 0.28f),
            radius = 9.dp.toPx(),
            center = Offset(satX, satY),
          )
          drawCircle(
            color = OrbitCyan,
            radius = 4.5.dp.toPx(),
            center = Offset(satX, satY),
          )
        }

        // Middle tilted orbit (Indigo)
        rotate(degrees = 34f, pivot = center) {
          val r2 = maxRadius * 0.84f
          drawOval(
            color = OrbitIndigo.copy(alpha = 0.3f),
            topLeft = Offset(center.x - r2, center.y - r2 * 0.48f),
            size = Size(r2 * 2f, r2 * 0.96f),
            style = Stroke(width = 1.8.dp.toPx()),
          )
          val rad2 = Math.toRadians(innerAngle.toDouble())
          val satX2 = center.x + (r2 * cos(rad2)).toFloat()
          val satY2 = center.y + (r2 * 0.48f * sin(rad2)).toFloat()
          drawCircle(
            color = OrbitIndigo.copy(alpha = 0.28f),
            radius = 8.dp.toPx(),
            center = Offset(satX2, satY2),
          )
          drawCircle(
            color = OrbitIndigo,
            radius = 4.dp.toPx(),
            center = Offset(satX2, satY2),
          )
        }

        // Inner circular arc (Mint)
        val r3 = maxRadius * 0.46f
        drawArc(
          color = OrbitMint.copy(alpha = 0.65f),
          startAngle = outerAngle * 1.3f,
          sweepAngle = 210f,
          useCenter = false,
          topLeft = Offset(center.x - r3, center.y - r3),
          size = Size(r3 * 2f, r3 * 2f),
          style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
        )

        // Glowing central planetary core
        drawCircle(
          brush = Brush.radialGradient(
            colors = listOf(
              OrbitCyan.copy(alpha = 0.45f),
              OrbitIndigo.copy(alpha = 0.12f),
              Color.Transparent,
            ),
            center = center,
            radius = maxRadius * 0.38f * pulse,
          ),
          radius = maxRadius * 0.38f * pulse,
          center = center,
        )
        drawCircle(
          color = OrbitCyan,
          radius = 12.dp.toPx(),
          center = center,
        )
        drawCircle(
          color = ObsidianVoid,
          radius = 5.5.dp.toPx(),
          center = center,
        )
      }

      Spacer(modifier = Modifier.height(18.dp))

      Text(
        text = "ExploreD",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        color = MaterialTheme.colorScheme.onBackground,
      )

      Spacer(modifier = Modifier.height(6.dp))

      Text(
        text = "Initializing root & filesystem…",
        style = MaterialTheme.typography.bodyMedium,
        fontFamily = FontFamily.Monospace,
        color = OrbitCyan.copy(alpha = 0.85f),
      )

      Spacer(modifier = Modifier.height(20.dp))

      LinearProgressIndicator(
        color = OrbitCyan,
        trackColor = OrbitCyan.copy(alpha = 0.16f),
        modifier = Modifier
          .width(160.dp)
          .height(3.dp)
          .clip(RoundedCornerShape(4.dp)),
      )
    }
  }
}
