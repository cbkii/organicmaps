package app.organicmaps.sdk.sound;

import androidx.annotation.NonNull;

public final class TtsFallbackPolicy
{
  private TtsFallbackPolicy() {}

  /**
   * A selected InCar tone/clip mode is an output mode, not a last-resort fallback.
   * System TTS readiness must not silently suppress explicitly selected navigation sounds.
   */
  public static boolean shouldPlayFallback(@NonNull TtsPlayer.State state,
                                           @NonNull OfflineNavigationVoicePack.Mode mode)
  {
    return mode != OfflineNavigationVoicePack.Mode.OFF;
  }

  public static boolean shouldUseSystemTts(@NonNull TtsPlayer.State state,
                                           @NonNull OfflineNavigationVoicePack.Mode mode)
  {
    return mode == OfflineNavigationVoicePack.Mode.OFF && state == TtsPlayer.State.READY_ON;
  }

  public static boolean shouldGenerateNotifications(boolean systemTtsEnabled, boolean fallbackCapable,
                                                    boolean fallbackEnabled)
  {
    return systemTtsEnabled || (fallbackCapable && fallbackEnabled);
  }
}
