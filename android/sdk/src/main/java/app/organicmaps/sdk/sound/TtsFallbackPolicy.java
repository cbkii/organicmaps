package app.organicmaps.sdk.sound;

import androidx.annotation.NonNull;

public final class TtsFallbackPolicy
{
  private TtsFallbackPolicy() {}

  public static boolean shouldPlayFallback(@NonNull TtsPlayer.State state, boolean ttsWasEnabledBeforeReady)
  {
    return switch (state)
    {
      case INITIALIZING -> ttsWasEnabledBeforeReady;
      case UNAVAILABLE, NEEDS_LANGUAGE -> true;
      case READY_ON, READY_OFF -> false;
    };
  }
}
