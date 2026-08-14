package app.organicmaps.incar;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class InCarQuickDestinationsLayoutPolicyTest
{
  @Test
  public void wideHeadUnitFitsPrimaryPlusAllEightActions()
  {
    assertFalse(InCarQuickDestinationsLayoutPolicy.requiresHorizontalScroll(1280, 8));
  }

  @Test
  public void compactAndNarrowWindowsUseDeterministicHorizontalOverflow()
  {
    assertTrue(InCarQuickDestinationsLayoutPolicy.requiresHorizontalScroll(640, 8));
    assertTrue(InCarQuickDestinationsLayoutPolicy.requiresHorizontalScroll(480, 8));
  }

  @Test
  public void enabledSubsetFitsNarrowerWindow()
  {
    assertFalse(InCarQuickDestinationsLayoutPolicy.requiresHorizontalScroll(480, 4));
  }
}
