package app.organicmaps.sdk.sound;

import androidx.annotation.NonNull;

public final class TtsFallbackPolicy
{
  private TtsFallbackPolicy() {}

  public static boolean shouldPlayFallback(@NonNull TtsPlayer.State state, boolean fallbackEnabled)
  {
    return fallbackEnabled && state != TtsPlayer.State.READY_ON;
  }

  public static boolean shouldGenerateNotifications(boolean systemTtsEnabled, boolean fallbackCapable,
                                                    boolean fallbackEnabled)
  {
    return systemTtsEnabled || (fallbackCapable && fallbackEnabled);
  }
}
