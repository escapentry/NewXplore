package com.example.display

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class WindowWidthTier {
  COMPACT,
  MEDIUM,
  EXPANDED,
}

@Immutable
data class ExploreDDisplayProfile(
  val widthTier: WindowWidthTier,
  val isCompactHeight: Boolean,
  val isLandscape: Boolean,
  val showSideInspector: Boolean,
  val availableWidthDp: Float,
  val availableHeightDp: Float,
  val density: Float,
  val systemFontScale: Float,
  val horizontalPaddingDp: Dp,
  val titleFontSp: TextUnit,
  val rowTitleFontSp: TextUnit,
  val rowMetaFontSp: TextUnit,
  val iconBoxSizeDp: Dp,
)

object DisplayAutoScaler {

  fun computeProfile(
    availableWidthDp: Float,
    availableHeightDp: Float,
    density: Float,
    systemFontScale: Float,
  ): ExploreDDisplayProfile {
    val safeWidth = availableWidthDp.coerceAtLeast(280f)
    val safeHeight = availableHeightDp.coerceAtLeast(240f)
    val isLandscape = safeWidth > safeHeight * 1.1f
    val isCompactHeight = safeHeight < 420f

    val widthTier = when {
      safeWidth < 600f -> WindowWidthTier.COMPACT
      safeWidth < 840f -> WindowWidthTier.MEDIUM
      else -> WindowWidthTier.EXPANDED
    }

    val horizontalPaddingDp = when {
      safeWidth < 360f -> 12.dp
      widthTier == WindowWidthTier.COMPACT -> 16.dp
      widthTier == WindowWidthTier.MEDIUM -> 22.dp
      else -> 28.dp
    }

    val fontGuard = if (systemFontScale > 1.25f) 1.25f / systemFontScale else 1f
    val baseRowSp = when {
      safeWidth < 360f -> 13.5f
      widthTier == WindowWidthTier.COMPACT -> 14.5f
      widthTier == WindowWidthTier.MEDIUM -> 15.2f
      else -> 15.8f
    }
    val rowSp = (baseRowSp * fontGuard).coerceIn(12.5f, 17f)

    return ExploreDDisplayProfile(
      widthTier = widthTier,
      isCompactHeight = isCompactHeight,
      isLandscape = isLandscape,
      showSideInspector = widthTier == WindowWidthTier.EXPANDED,
      availableWidthDp = safeWidth,
      availableHeightDp = safeHeight,
      density = density,
      systemFontScale = systemFontScale,
      horizontalPaddingDp = horizontalPaddingDp,
      titleFontSp = (rowSp * 1.18f).coerceIn(15.5f, 20f).sp,
      rowTitleFontSp = rowSp.sp,
      rowMetaFontSp = (rowSp * 0.78f).coerceIn(10.5f, 13f).sp,
      iconBoxSizeDp = if (widthTier == WindowWidthTier.COMPACT) 42.dp else 46.dp,
    )
  }
}
