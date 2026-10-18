package app.organicmaps.incar;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class InCarCompactStartupStateTest
{
  @Test public void taskAndNativeEngineAloneDoNotProveUsableRendering()
  {
    final InCarCompactStartupState state = new InCarCompactStartupState();
    state.splash();
    state.coreReady();
    state.activity(1, 42, true);
    state.nativeReady();
    assertFalse(state.ready());
    state.attached(true);
    state.surface(0, 300);
    assertFalse(state.ready());
    state.surface(600, 300);
    assertTrue(state.ready());
    state.attached(false);
    assertFalse(state.ready());
    state.attached(true);
    assertFalse(state.ready());
    state.surface(600, 300);
    assertTrue(state.ready());
    state.activity(1, 42, false);
    assertFalse(state.ready());
  }

  @Test public void staleActivityPauseDoesNotInvalidateReplacementAndNewProcessIsCold()
  {
    final InCarCompactStartupState state = new InCarCompactStartupState();
    state.coreReady();
    state.nativeReady();
    state.activity(1, 42, true);
    state.attached(true);
    state.surface(600, 300);
    state.activity(2, 42, true);
    assertFalse(state.ready());
    state.surface(600, 300);
    state.activity(1, 42, false);
    assertTrue(state.ready());
    assertFalse(new InCarCompactStartupState().ready());
  }
}
