package app.organicmaps.incar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class InCarRouteResumePolicyTest
{
  @Test
  public void sameBootSleepUsesElapsedTimeDespiteWallClockChanges()
  {
    assertFalse(InCarRouteResumePolicy.isExpired(1_000_000, 10_000, 7, 50, 309_999, 7, 5));
    assertTrue(InCarRouteResumePolicy.isExpired(1_000_000, 10_000, 7, 50, 310_000, 7, 5));
  }

  @Test
  public void rebootUsesWallTimeAndLegacyOrFutureTimestampsExpire()
  {
    assertFalse(InCarRouteResumePolicy.isExpired(1_000_000, 10_000, 7, 1_299_999, 10, 8, 5));
    assertTrue(InCarRouteResumePolicy.isExpired(1_000_000, 10_000, 7, 1_300_000, 10, 8, 5));
    assertTrue(InCarRouteResumePolicy.isExpired(0, -1, -1, 1_300_000, 10, 8, 30));
    assertTrue(InCarRouteResumePolicy.isExpired(1_000_000, 10_000, 7, 999_999, 10, 8, 30));
  }

  @Test
  public void freshActivityKeepsLongJourneyAliveAndSettingsStayBounded()
  {
    assertFalse(InCarRouteResumePolicy.isExpired(9_000_000, 8_000_000, 7, 9_010_000, 8_010_000, 7, 5));
    for (int minutes = 5; minutes <= 90; minutes += 5)
      assertEquals(minutes, InCarRouteResumePolicy.normaliseMinutes(minutes));
    for (int invalid : new int[] {0, 4, 6, 91, Integer.MAX_VALUE})
      assertEquals(30, InCarRouteResumePolicy.normaliseMinutes(invalid));
  }
}
