package app.organicmaps.incar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class InCarQuickDestinationsLayoutPolicyTest
{
  @Test
  public void wideHeadUnitFitsPrimaryPlusAllEightActions()
  {
    assertFalse(InCarQuickDestinationsLayoutPolicy.requiresOverflow(1280, 8));
  }

  @Test
  public void compactWindowsReserveDeterministicActionCapacity()
  {
    assertEquals(7, InCarQuickDestinationsLayoutPolicy.maxVisibleDestinationActions(640));
    assertEquals(5, InCarQuickDestinationsLayoutPolicy.maxVisibleDestinationActions(480));
    assertTrue(InCarQuickDestinationsLayoutPolicy.requiresOverflow(640, 8));
    assertTrue(InCarQuickDestinationsLayoutPolicy.requiresOverflow(480, 8));
  }

  @Test
  public void enabledSubsetFitsNarrowerWindow()
  {
    assertFalse(InCarQuickDestinationsLayoutPolicy.requiresOverflow(480, 4));
  }

  @Test
  public void veryNarrowWindowDoesNotReturnNegativeCapacity()
  {
    assertEquals(0, InCarQuickDestinationsLayoutPolicy.maxVisibleDestinationActions(80));
  }
}
