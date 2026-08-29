package app.organicmaps.incar;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class InCarBackPolicyTest
{
  @Test
  public void activeNavigationBackIsBlockedFromCancellation()
  {
    assertTrue(InCarBackPolicy.shouldBlockBackFromCancellingNavigation(true));
  }

  @Test
  public void nonNavigationBackFallsThrough()
  {
    assertFalse(InCarBackPolicy.shouldBlockBackFromCancellingNavigation(false));
  }
}
