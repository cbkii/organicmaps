package app.organicmaps.incar;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class InCarStartupHandoffTest
{
  @Test
  public void coldStartInitializesAndDispatchesOnce()
  {
    final InCarStartupHandoff startup = new InCarStartupHandoff();
    assertFalse(startup.beginInitialization());
    startup.onResume();
    assertTrue(startup.beginInitialization());
    assertFalse(startup.claimHandoff());
    startup.onCoreReady();
    assertTrue(startup.claimHandoff());
    startup.onCoreReady();
    assertFalse(startup.claimHandoff());
    assertFalse(startup.beginInitialization());
  }

  @Test
  public void asyncCompletionWhilePausedWaitsForResume()
  {
    final InCarStartupHandoff startup = new InCarStartupHandoff();
    startup.onResume();
    assertTrue(startup.beginInitialization());
    startup.onPause();
    startup.onCoreReady();
    assertTrue(startup.isCoreReady());
    assertFalse(startup.claimHandoff());
    startup.onResume();
    assertFalse(startup.beginInitialization());
    assertTrue(startup.claimHandoff());
  }

  @Test
  public void returningBeforeCoreCompletionDoesNotReinitialize()
  {
    final InCarStartupHandoff startup = new InCarStartupHandoff();
    startup.onResume();
    assertTrue(startup.beginInitialization());
    startup.onPause();
    startup.onResume();
    assertFalse(startup.beginInitialization());
    assertFalse(startup.claimHandoff());
    startup.onCoreReady();
    assertTrue(startup.claimHandoff());
  }

  @Test
  public void warmCoreReadyPathStillRequiresResumeAndDispatchesOnce()
  {
    final InCarStartupHandoff startup = new InCarStartupHandoff();
    startup.onCoreReady();
    assertFalse(startup.claimHandoff());
    startup.onResume();
    assertTrue(startup.claimHandoff());
    // API-result handoff leaves Splash alive: resume must not start another Activity.
    startup.onPause();
    startup.onResume();
    assertFalse(startup.claimHandoff());
  }

  @Test
  public void destroyedOrFatalStartupIgnoresLateCompletion()
  {
    final InCarStartupHandoff startup = new InCarStartupHandoff();
    startup.onResume();
    assertTrue(startup.beginInitialization());
    startup.close();
    startup.onCoreReady();
    startup.onResume();
    assertFalse(startup.isCoreReady());
    assertFalse(startup.claimHandoff());
    assertFalse(startup.beginInitialization());
  }

  @Test
  public void recreatedActivityUsesItsOwnHandoffAuthority()
  {
    final InCarStartupHandoff previous = new InCarStartupHandoff();
    previous.onResume();
    previous.beginInitialization();
    previous.close();
    final InCarStartupHandoff current = new InCarStartupHandoff();
    current.onResume();
    current.onCoreReady();
    previous.onCoreReady();
    assertFalse(previous.claimHandoff());
    assertTrue(current.claimHandoff());
  }
}
