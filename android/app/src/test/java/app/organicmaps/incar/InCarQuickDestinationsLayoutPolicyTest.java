package app.organicmaps.incar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class InCarQuickDestinationsLayoutPolicyTest
{
  @Test
  public void representativeDefaultSetFitsCompactWidth()
  {
    assertFalse(InCarQuickDestinationsLayoutPolicy.requiresHorizontalScroll(1280, 7));
    assertFalse(InCarQuickDestinationsLayoutPolicy.requiresHorizontalScroll(640, 7));
  }

  @Test
  public void allNineActionsScrollAtCompactWidth()
  {
    assertFalse(InCarQuickDestinationsLayoutPolicy.requiresHorizontalScroll(1280, 9));
    assertTrue(InCarQuickDestinationsLayoutPolicy.requiresHorizontalScroll(640, 9));
  }

  @Test
  public void narrowWindowUsesDeterministicHorizontalOverflow()
  {
    assertTrue(InCarQuickDestinationsLayoutPolicy.requiresHorizontalScroll(480, 7));
  }

  @Test
  public void enabledSubsetFitsNarrowerWindow()
  {
    assertFalse(InCarQuickDestinationsLayoutPolicy.requiresHorizontalScroll(480, 4));
  }

  @Test
  public void requiredStripWidthIsStableForRepresentativeActionCounts()
  {
    assertEquals(128, InCarQuickDestinationsLayoutPolicy.requiredWidthDp(0));
    assertEquals(384, InCarQuickDestinationsLayoutPolicy.requiredWidthDp(4));
    assertEquals(640, InCarQuickDestinationsLayoutPolicy.requiredWidthDp(8));
  }
}
