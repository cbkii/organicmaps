package app.organicmaps.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class InCarVisualsTest
{
  @Test
  public void fullWindowAtThresholds()
  {
    assertEquals(InCarVisuals.WindowProfile.FULL,
                 InCarVisuals.classifyWindow(InCarVisuals.COMPACT_WIDTH_DP, InCarVisuals.COMPACT_HEIGHT_DP));
  }

  @Test
  public void compactWidthBelowThreshold()
  {
    assertEquals(InCarVisuals.WindowProfile.COMPACT_WIDTH,
                 InCarVisuals.classifyWindow(InCarVisuals.COMPACT_WIDTH_DP - 1, InCarVisuals.COMPACT_HEIGHT_DP));
  }

  @Test
  public void compactHeightBelowThreshold()
  {
    assertEquals(InCarVisuals.WindowProfile.COMPACT_HEIGHT,
                 InCarVisuals.classifyWindow(InCarVisuals.COMPACT_WIDTH_DP, InCarVisuals.COMPACT_HEIGHT_DP - 1));
  }

  @Test
  public void compactBothBelowThresholds()
  {
    assertEquals(InCarVisuals.WindowProfile.COMPACT_BOTH,
                 InCarVisuals.classifyWindow(InCarVisuals.COMPACT_WIDTH_DP - 1, InCarVisuals.COMPACT_HEIGHT_DP - 1));
  }

  @Test
  public void invalidUnmeasuredBoundsFallBackToFull()
  {
    assertEquals(InCarVisuals.WindowProfile.FULL, InCarVisuals.classifyWindow(0, 0));
  }
}
