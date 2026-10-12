package app.organicmaps.incar;

import static org.junit.Assert.*;
import org.junit.Test;

public class InCarNavigationCountdownPolicyTest
{
  @Test public void everyDelayDispatchesExactlyOnceAtDeadline()
  {
    for (int seconds : new int[] {2, 3, 5, 10, 15, 20, 30})
    {
      InCarNavigationCountdownPolicy p = new InCarNavigationCountdownPolicy();
      assertTrue(p.arm("GO_TO", 1, seconds, 100));
      assertFalse(p.dispatch(99 + seconds * 1000L, true));
      assertTrue(p.dispatch(100 + seconds * 1000L, true));
      assertFalse(p.dispatch(100 + seconds * 1000L, true));
      assertFalse(p.arm("GO_TO", 1, seconds, 99999));
    }
    assertEquals(10, InCarNavigationCountdownPolicy.normaliseDelaySeconds(0));
  }
  @Test public void cancellationAndEligibilityLossNeverRearmFromRedraw()
  {
    InCarNavigationCountdownPolicy p = new InCarNavigationCountdownPolicy();
    assertTrue(p.arm("GO_TO", 1, 10, 0));
    p.cancel();
    assertFalse(p.arm("GO_TO", 1, 10, 100));
    assertFalse(p.dispatch(10000, true));
    assertTrue(p.arm("GO_TO", 2, 10, 100));
    assertFalse(p.dispatch(10100, false));
    assertFalse(p.dispatch(10100, true));
    assertTrue(p.arm("START", 1, 2, 20000));
    assertTrue(p.dispatch(22000, true));
  }
  @Test public void staleGenerationCannotReplaceNewRoute()
  {
    InCarNavigationCountdownPolicy p = new InCarNavigationCountdownPolicy();
    assertTrue(p.arm("START", 2, 3, 0));
    assertFalse(p.arm("START", 1, 3, 0));
    assertEquals(2, p.remainingSeconds(1000));
  }
}
