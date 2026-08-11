package app.organicmaps.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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
  public void invalidBoundsAreUnknown()
  {
    assertNull(InCarVisuals.classifyWindow(0, 0));
    assertNull(InCarVisuals.classifyWindow(0, InCarVisuals.COMPACT_HEIGHT_DP - 1));
    assertNull(InCarVisuals.classifyWindow(InCarVisuals.COMPACT_WIDTH_DP - 1, 0));
  }

  @Test
  public void invalidBoundsRetainLastValidProfile()
  {
    final InCarVisuals.WindowProfile compact = InCarVisuals.WindowProfile.COMPACT_BOTH;
    assertEquals(compact, InCarVisuals.resolveWindowProfile(compact, 0, 0));
    assertEquals(compact, InCarVisuals.resolveWindowProfile(compact, 0, InCarVisuals.COMPACT_HEIGHT_DP - 1));
    assertEquals(compact, InCarVisuals.resolveWindowProfile(compact, InCarVisuals.COMPACT_WIDTH_DP - 1, 0));
  }

  @Test
  public void invalidThenValidCompactResolvesCompact()
  {
    InCarVisuals.WindowProfile profile = InCarVisuals.resolveWindowProfile(null, 0, 0);
    assertNull(profile);
    profile =
        InCarVisuals.resolveWindowProfile(profile, InCarVisuals.COMPACT_WIDTH_DP - 1, InCarVisuals.COMPACT_HEIGHT_DP);
    assertEquals(InCarVisuals.WindowProfile.COMPACT_WIDTH, profile);
  }

  @Test
  public void compactThroughTransientInvalidToFull()
  {
    InCarVisuals.WindowProfile profile =
        InCarVisuals.resolveWindowProfile(null, InCarVisuals.COMPACT_WIDTH_DP - 1, InCarVisuals.COMPACT_HEIGHT_DP - 1);
    assertEquals(InCarVisuals.WindowProfile.COMPACT_BOTH, profile);
    profile = InCarVisuals.resolveWindowProfile(profile, 0, 0);
    assertEquals(InCarVisuals.WindowProfile.COMPACT_BOTH, profile);
    profile = InCarVisuals.resolveWindowProfile(profile, InCarVisuals.COMPACT_WIDTH_DP, InCarVisuals.COMPACT_HEIGHT_DP);
    assertEquals(InCarVisuals.WindowProfile.FULL, profile);
  }

  @Test
  public void fullThroughTransientInvalidToCompact()
  {
    InCarVisuals.WindowProfile profile =
        InCarVisuals.resolveWindowProfile(null, InCarVisuals.COMPACT_WIDTH_DP, InCarVisuals.COMPACT_HEIGHT_DP);
    assertEquals(InCarVisuals.WindowProfile.FULL, profile);
    profile = InCarVisuals.resolveWindowProfile(profile, InCarVisuals.COMPACT_WIDTH_DP, 0);
    assertEquals(InCarVisuals.WindowProfile.FULL, profile);
    profile =
        InCarVisuals.resolveWindowProfile(profile, InCarVisuals.COMPACT_WIDTH_DP, InCarVisuals.COMPACT_HEIGHT_DP - 1);
    assertEquals(InCarVisuals.WindowProfile.COMPACT_HEIGHT, profile);
  }

  @Test
  public void repeatedBoundsAreIdempotent()
  {
    assertFalse(InCarVisuals.hasMaterialBoundsChange(1280, 720, 1280, 720));
    assertTrue(InCarVisuals.hasMaterialBoundsChange(735, 387, 1280, 720));
  }

  @Test
  public void staleScheduledGenerationIsRejected()
  {
    assertTrue(InCarVisuals.isGenerationCurrent(7, 7));
    assertFalse(InCarVisuals.isGenerationCurrent(6, 7));
  }

  @Test
  public void pixelBoundsRequireBothDimensions()
  {
    assertTrue(InCarVisuals.hasValidBounds(1280, 720));
    assertFalse(InCarVisuals.hasValidBounds(0, 720));
    assertFalse(InCarVisuals.hasValidBounds(1280, 0));
  }
}
