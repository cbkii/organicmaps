package app.organicmaps.sdk.sound;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TtsFallbackPolicyTest
{
  @Test
  public void enabledFallbackCoversEveryNonSpeakingState()
  {
    assertTrue(TtsFallbackPolicy.shouldPlayFallback(TtsPlayer.State.INITIALIZING, true));
    assertTrue(TtsFallbackPolicy.shouldPlayFallback(TtsPlayer.State.UNAVAILABLE, true));
    assertTrue(TtsFallbackPolicy.shouldPlayFallback(TtsPlayer.State.NEEDS_LANGUAGE, true));
    assertTrue(TtsFallbackPolicy.shouldPlayFallback(TtsPlayer.State.READY_OFF, true));
  }

  @Test
  public void readySystemTtsRemainsPreferred()
  {
    assertFalse(TtsFallbackPolicy.shouldPlayFallback(TtsPlayer.State.READY_ON, true));
  }

  @Test
  public void disabledFallbackNeverPlays()
  {
    for (TtsPlayer.State state : TtsPlayer.State.values())
      assertFalse(TtsFallbackPolicy.shouldPlayFallback(state, false));
  }

  @Test
  public void nativeEventsRemainEnabledForInCarFallbackWithoutConsultingVoiceAssets()
  {
    assertTrue(TtsFallbackPolicy.shouldGenerateNotifications(false, true, true));
    assertFalse(TtsFallbackPolicy.shouldGenerateNotifications(false, true, false));
    assertFalse(TtsFallbackPolicy.shouldGenerateNotifications(false, false, true));
    assertTrue(TtsFallbackPolicy.shouldGenerateNotifications(true, false, false));
  }
}
