package app.organicmaps.sdk.sound;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TtsFallbackPolicyTest
{
  @Test
  public void unavailableEngineUsesFallback()
  {
    assertTrue(TtsFallbackPolicy.shouldPlayFallback(TtsPlayer.State.UNAVAILABLE, false));
  }

  @Test
  public void missingLanguageUsesFallback()
  {
    assertTrue(TtsFallbackPolicy.shouldPlayFallback(TtsPlayer.State.NEEDS_LANGUAGE, false));
  }

  @Test
  public void initializingUsesSavedVoiceIntent()
  {
    assertTrue(TtsFallbackPolicy.shouldPlayFallback(TtsPlayer.State.INITIALIZING, true));
    assertFalse(TtsFallbackPolicy.shouldPlayFallback(TtsPlayer.State.INITIALIZING, false));
  }

  @Test
  public void readyStatesDoNotUseFallback()
  {
    assertFalse(TtsFallbackPolicy.shouldPlayFallback(TtsPlayer.State.READY_ON, true));
    assertFalse(TtsFallbackPolicy.shouldPlayFallback(TtsPlayer.State.READY_OFF, true));
  }
}
