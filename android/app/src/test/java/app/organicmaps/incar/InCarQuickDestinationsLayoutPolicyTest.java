package app.organicmaps.incar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class InCarQuickDestinationsLayoutPolicyTest
{
  @Test
  public void preferredTouchTargetUsesAutomotiveBaseline()
  {
    assertEquals(76, InCarQuickDestinationsLayoutPolicy.PREFERRED_ACTION_SIZE_DP);
    assertEquals(InCarQuickDestinationsLayoutPolicy.PREFERRED_ACTION_SIZE_DP,
                 InCarQuickDestinationsLayoutPolicy.ACTION_SIZE_DP);
    assertEquals(InCarQuickDestinationsLayoutPolicy.PREFERRED_ACTION_SIZE_DP,
                 InCarQuickDestinationsLayoutPolicy.PRIMARY_ACTION_WIDTH_DP);
  }

  @Test
  public void compactTouchFloorIsCeilingOfNinetyPercent()
  {
    final int preferred = InCarQuickDestinationsLayoutPolicy.PREFERRED_ACTION_SIZE_DP;
    final int minimum = InCarQuickDestinationsLayoutPolicy.MIN_ACTION_SIZE_DP;
    assertEquals(69, minimum);
    assertTrue(minimum * 10 >= preferred * 9);
    assertTrue((minimum - 1) * 10 < preferred * 9);
    assertTrue(InCarQuickDestinationsLayoutPolicy.ACTION_SIZE_DP >= minimum);
    assertTrue(InCarQuickDestinationsLayoutPolicy.MIN_ACTION_GAP_DP >= 0);
  }

  @Test
  public void emergencyTierIsApproximatelyEightyPercentOfCompactFloor()
  {
    assertEquals(55, InCarQuickDestinationsLayoutPolicy.EXTRA_COMPACT_ACTION_SIZE_DP);
    assertTrue(InCarQuickDestinationsLayoutPolicy.EXTRA_COMPACT_ACTION_SIZE_DP * 100
               >= InCarQuickDestinationsLayoutPolicy.MIN_ACTION_SIZE_DP * 79);
    assertTrue(InCarQuickDestinationsLayoutPolicy.EXTRA_COMPACT_ACTION_SIZE_DP * 100
               <= InCarQuickDestinationsLayoutPolicy.MIN_ACTION_SIZE_DP * 81);
  }

  @Test
  public void capacityAccountsForInterButtonGaps()
  {
    assertEquals(173, InCarQuickDestinationsLayoutPolicy.requiredHeightDp(3, 55, 4));
    assertEquals(3, InCarQuickDestinationsLayoutPolicy.maxVisibleActions(173, 55, 4));
    assertEquals(2, InCarQuickDestinationsLayoutPolicy.maxVisibleActions(172, 55, 4));
  }

  @Test
  public void constrainedHeightKeepsHomeAndWorkBeforeOverflow()
  {
    final int directActions = 4;
    final int existingOverflowActions = 2;
    final int visibleDirect =
        InCarQuickDestinationsLayoutPolicy.directActionCountForCapacity(3, directActions, existingOverflowActions);
    assertEquals(2, visibleDirect);
    assertTrue(InCarQuickDestinationsLayoutPolicy.shouldShowMore(3, directActions, visibleDirect,
                                                                 existingOverflowActions));
  }

  @Test
  public void fullCapacityKeepsAllDirectActionsAndMoreWhenOverflowExists()
  {
    final int directActions = 4;
    final int visibleDirect = InCarQuickDestinationsLayoutPolicy.directActionCountForCapacity(5, directActions, 2);
    assertEquals(4, visibleDirect);
    assertTrue(InCarQuickDestinationsLayoutPolicy.shouldShowMore(5, directActions, visibleDirect, 2));
  }

  @Test
  public void fullCapacityDoesNotCreateEmptyMoreMenu()
  {
    final int directActions = 4;
    final int visibleDirect = InCarQuickDestinationsLayoutPolicy.directActionCountForCapacity(4, directActions, 0);
    assertEquals(4, visibleDirect);
    assertFalse(InCarQuickDestinationsLayoutPolicy.shouldShowMore(4, directActions, visibleDirect, 0));
  }
}
