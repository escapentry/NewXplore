package com.example

import com.example.display.DisplayAutoScaler
import com.example.display.WindowWidthTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayAutoScalerTest {

  @Test
  fun `compact phone computes balanced row font and hides side inspector`() {
    val profile = DisplayAutoScaler.computeProfile(
      availableWidthDp = 390f,
      availableHeightDp = 844f,
      density = 3f,
      systemFontScale = 1f,
    )
    assertEquals(WindowWidthTier.COMPACT, profile.widthTier)
    assertFalse(profile.showSideInspector)
    assertTrue(profile.rowTitleFontSp.value in 13.5f..16.0f)
    assertTrue(profile.rowMetaFontSp.value < profile.rowTitleFontSp.value)
  }

  @Test
  fun `expanded tablet enables side inspector and scales typography`() {
    val tablet = DisplayAutoScaler.computeProfile(
      availableWidthDp = 1024f,
      availableHeightDp = 768f,
      density = 2f,
      systemFontScale = 1f,
    )
    val phone = DisplayAutoScaler.computeProfile(
      availableWidthDp = 360f,
      availableHeightDp = 780f,
      density = 3f,
      systemFontScale = 1f,
    )
    assertEquals(WindowWidthTier.EXPANDED, tablet.widthTier)
    assertTrue(tablet.showSideInspector)
    assertTrue(tablet.rowTitleFontSp.value > phone.rowTitleFontSp.value)
  }

  @Test
  fun `extreme system font scale is clamped cleanly`() {
    val normal = DisplayAutoScaler.computeProfile(390f, 844f, 3f, 1.0f)
    val extreme = DisplayAutoScaler.computeProfile(390f, 844f, 3f, 2.0f)
    assertTrue(extreme.rowTitleFontSp.value < normal.rowTitleFontSp.value)
    assertTrue(extreme.rowTitleFontSp.value >= 12.5f)
  }
}
