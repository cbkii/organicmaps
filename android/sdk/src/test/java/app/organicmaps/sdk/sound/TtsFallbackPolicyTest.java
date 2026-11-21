package app.organicmaps.sdk.sound;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TtsFallbackPolicyTest
{
  @Test
  public void selectedToneOrVoiceModeWinsOverEverySystemTtsState()
  {
    for (TtsPlayer.State state : TtsPlayer.State.values())
      for (OfflineNavigationVoicePack.Mode mode : new OfflineNavigationVoicePack.Mode[] {
               OfflineNavigationVoicePack.Mode.TONE_ALL, OfflineNavigationVoicePack.Mode.TONE_ALERTS,
               OfflineNavigationVoicePack.Mode.VOICE})
      {
        assertTrue(TtsFallbackPolicy.shouldPlayFallback(state, mode));
        assertFalse(TtsFallbackPolicy.shouldUseSystemTts(state, mode));
      }
  }

  @Test
  public void systemTtsOnlyIsUnchangedByFallbackModes()
  {
    for (TtsPlayer.State state : TtsPlayer.State.values())
    {
      assertFalse(TtsFallbackPolicy.shouldPlayFallback(state, OfflineNavigationVoicePack.Mode.OFF));
      assertTrue(TtsFallbackPolicy.shouldUseSystemTts(state, OfflineNavigationVoicePack.Mode.OFF)
                     == (state == TtsPlayer.State.READY_ON));
    }
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
