package app.organicmaps.incar;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class InCarQuickDestinationsLayoutPolicyTest
{
  @Test
  public void fullAndCompactWidthsFitPrimaryPlusAllEightActions()
  {
    assertFalse(InCarQuickDestinationsLayoutPolicy.requiresHorizontalScroll(1280, 8));
    assertFalse(InCarQuickDestinationsLayoutPolicy.requiresHorizontalScroll(640, 8));
  }

  @Test
  public void narrowWindowUsesDeterministicHorizontalOverflow()
  {
    assertTrue(InCarQuickDestinationsLayoutPolicy.requiresHorizontalScroll(480, 8));
  }

  @Test
  public void enabledSubsetFitsNarrowerWindow()
  {
    assertFalse(InCarQuickDestinationsLayoutPolicy.requiresHorizontalScroll(480, 4));
  }
}
