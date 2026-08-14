package app.organicmaps.search;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class InCarSearchPresentationPolicyTest
{
  @Test
  public void panelWidthUsesFractionWithinBounds()
  {
    assertEquals(512, InCarSearchPresentationPolicy.panelWidthPx(1280, 360, 512, 0.4f));
    assertEquals(410, InCarSearchPresentationPolicy.panelWidthPx(1024, 360, 512, 0.4f));
    assertEquals(360, InCarSearchPresentationPolicy.panelWidthPx(800, 360, 512, 0.4f));
    assertEquals(300, InCarSearchPresentationPolicy.panelWidthPx(300, 360, 512, 0.4f));
    assertEquals(512, InCarSearchPresentationPolicy.panelWidthPx(1920, 360, 512, 0.4f));
  }

  @Test
  public void mapModeChangeInvalidatesNestedScrollingSnapshot()
  {
    assertFalse(InCarSearchPresentationPolicy.shouldSynchronizeNestedScrolling(true, 0, false, true, 0, false));
    assertTrue(InCarSearchPresentationPolicy.shouldSynchronizeNestedScrolling(true, 0, false, true, 0, true));
    assertTrue(InCarSearchPresentationPolicy.shouldSynchronizeNestedScrolling(true, 0, true, false, 0, true));
    assertTrue(InCarSearchPresentationPolicy.shouldSynchronizeNestedScrolling(true, 0, true, true, 1, true));
  }

  @Test
  public void resultsNestedScrollingFollowsListVisibility()
  {
    assertTrue(InCarSearchPresentationPolicy.resultsNestedScrollingEnabled(true, false));
    assertFalse(InCarSearchPresentationPolicy.resultsNestedScrollingEnabled(true, true));
    assertFalse(InCarSearchPresentationPolicy.resultsNestedScrollingEnabled(false, false));
  }
}
