package app.organicmaps.incar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class InCarQuickDestinationsLayoutPolicyTest
{
  @Test
  public void tallWindowFitsPrimaryPlusAllEightActions()
  {
    assertFalse(InCarQuickDestinationsLayoutPolicy.requiresOverflow(656, 8));
    assertEquals(10, InCarQuickDestinationsLayoutPolicy.resolvedGapDp(656, 8));
  }

  @Test
  public void constrainedHeightReducesGapBeforeOverflow()
  {
    assertFalse(InCarQuickDestinationsLayoutPolicy.requiresOverflow(616, 8));
    assertEquals(5, InCarQuickDestinationsLayoutPolicy.resolvedGapDp(616, 8));
  }

  @Test
  public void compactHeightUsesDeterministicOverflowCapacity()
  {
    assertEquals(5, InCarQuickDestinationsLayoutPolicy.maxVisibleDestinationActions(420));
    assertEquals(3, InCarQuickDestinationsLayoutPolicy.maxVisibleDestinationActions(300));
    assertTrue(InCarQuickDestinationsLayoutPolicy.requiresOverflow(420, 8));
    assertTrue(InCarQuickDestinationsLayoutPolicy.requiresOverflow(300, 8));
  }

  @Test
  public void enabledSubsetFitsCompactHeight()
  {
    assertFalse(InCarQuickDestinationsLayoutPolicy.requiresOverflow(350, 4));
  }

  @Test
  public void tinyHeightDoesNotReturnNegativeCapacity()
  {
    assertEquals(0, InCarQuickDestinationsLayoutPolicy.maxVisibleDestinationActions(64));
  }

  @Test
  public void touchTargetIsNeverShrunkBySizingPolicy()
  {
    assertEquals(64, InCarQuickDestinationsLayoutPolicy.ACTION_SIZE_DP);
    assertTrue(InCarQuickDestinationsLayoutPolicy.MIN_ACTION_GAP_DP >= 0);
  }
}
