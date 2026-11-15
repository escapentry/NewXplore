package com.example.ui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.min

class AppleSquircleShape(
  private val cornerRatio: Float = 0.28f,
) : Shape {
  override fun createOutline(
    size: Size,
    layoutDirection: LayoutDirection,
    density: Density,
  ): Outline {
    val radius = min(size.width, size.height) * cornerRatio
    val path = Path().apply {
      addRoundRect(
        RoundRect(
          rect = Rect(0f, 0f, size.width, size.height),
          cornerRadius = CornerRadius(radius, radius),
        ),
      )
    }
    return Outline.Generic(path)
  }
}
