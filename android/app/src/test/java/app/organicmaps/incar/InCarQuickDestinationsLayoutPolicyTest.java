package app.organicmaps.incar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class InCarQuickDestinationsLayoutPolicyTest
{
  @Test
  public void touchTargetIsNeverShrunkBySizingPolicy()
  {
    assertEquals(56, InCarQuickDestinationsLayoutPolicy.ACTION_SIZE_DP);
    assertTrue(InCarQuickDestinationsLayoutPolicy.MIN_ACTION_GAP_DP >= 0);
  }
}
