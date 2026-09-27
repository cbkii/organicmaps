package app.organicmaps.location;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class LocationSettlePolicyTest
{
  @Test
  public void retriesStopAtTenSecondsWithoutOvershooting()
  {
    final long start = 2_000L;
    final long deadline = start + LocationSettlePolicy.WINDOW_MS;
    long now = start;
    int retries = 0;
    while (LocationSettlePolicy.nextDelay(deadline, now) > 0)
    {
      now += LocationSettlePolicy.nextDelay(deadline, now);
      ++retries;
    }
    assertEquals(deadline, now);
    assertEquals(7, retries);
    assertEquals(0L, LocationSettlePolicy.nextDelay(deadline, deadline + 10_000L));
  }
}
