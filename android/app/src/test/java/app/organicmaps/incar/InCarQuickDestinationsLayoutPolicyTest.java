package app.organicmaps.incar;

import static org.junit.Assert.assertEquals;
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
}
