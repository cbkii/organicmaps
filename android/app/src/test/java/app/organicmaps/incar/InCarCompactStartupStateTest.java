package app.organicmaps.incar;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class InCarCompactStartupStateTest
{
  @Test
  public void presentationAndGeometryRecoverIndependentlyAcrossPauseResizeAndReplacement()
  {
    final InCarCompactStartupState state = new InCarCompactStartupState();
    state.coreReady();
    state.created(1, 42);
    state.activity(1, 42, true);
    state.renderingCreated(1);
    state.nativeReady(1);
    state.surface(1, 1000, 500);
    state.presentationRequired(1);
    assertFalse(state.ready());
    state.presentation(1, true, 10);
    assertTrue(state.ready());
    state.activity(1, 42, false);
    assertFalse(state.ready());
    state.activity(1, 42, true);
    state.surface(1, 0, 0);
    assertFalse(state.ready());
    state.surface(1, 900, 450);
    assertTrue(state.ready());
    state.presentation(1, false, 12);
    state.presentation(1, true, 11);
    assertFalse(state.ready());
    state.presentation(1, true, 13);
    assertTrue(state.ready());
    // A new rendering owner can restart native generation numbering.
    state.presentationRequired(1);
    state.presentation(1, true, 1);
    assertTrue(state.ready());
    state.created(2, 42);
    state.presentation(1, true, 99);
    assertFalse(state.ready());
  }

  @Test
  public void taskAndNativeEngineAloneDoNotProveUsableRendering()
  {
    final InCarCompactStartupState state = new InCarCompactStartupState();
    state.splash();
    state.coreReady();
    state.created(1, 42);
    state.activity(1, 42, true);
    state.renderingCreated(1);
    state.nativeReady(1);
    assertFalse(state.ready());
    state.surface(1, 0, 300);
    assertFalse(state.ready());
    state.surface(1, 600, 300);
    assertTrue(state.ready());
    state.attached(1, false);
    state.attached(1, true);
    assertFalse(state.ready());
    state.surface(1, 600, 300);
    assertTrue(state.ready());
    state.surface(1, 0, 0);
    assertFalse(state.ready());
    state.surface(1, 600, 300);
    state.activity(1, 42, false);
    assertFalse(state.ready());
  }

  @Test
  public void staleCallbacksCannotInvalidateOrReadyReplacement()
  {
    final InCarCompactStartupState state = new InCarCompactStartupState();
    state.coreReady();
    state.created(1, 42);
    state.activity(1, 42, true);
    state.renderingCreated(1);
    state.nativeReady(1);
    state.surface(1, 600, 300);
    assertTrue(state.ready());
    state.created(2, 42);
    state.activity(2, 42, true);
    state.surface(2, 600, 300);
    assertFalse(state.ready());
    state.attached(1, true);
    assertFalse(state.ready());
    state.attached(2, true);
    assertTrue(state.ready());
    state.attached(1, false);
    state.surface(1, 0, 0);
    state.activity(1, 42, false);
    assertTrue(state.ready());
    state.renderingCreated(2);
    state.surface(2, 600, 300);
    assertFalse(state.ready());
    state.nativeReady(1);
    assertFalse(state.ready());
    state.nativeReady(2);
    assertTrue(state.ready());
    assertFalse(new InCarCompactStartupState().ready());
  }
}
