package app.organicmaps.sdk.routing;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;

/** A generated navigation notification plus its locale-independent event kind. */
@Keep
@SuppressWarnings("unused")
public final class NavigationNotification
{
  public enum Event
  {
    MANEUVER(1),
    ROUTE_RECALCULATION(2),
    SPEED_CAMERA(3);

    private final int mNativeValue;

    Event(int nativeValue)
    {
      mNativeValue = nativeValue;
    }

    @NonNull
    private static Event fromNativeValue(int value)
    {
      for (Event event : values())
        if (event.mNativeValue == value)
          return event;
      throw new IllegalArgumentException("Unknown native navigation notification event: " + value);
    }
  }

  public enum Stage
  {
    NONE(0),
    ADVANCE(1),
    IMMEDIATE(2);

    private final int mNativeValue;

    Stage(int nativeValue)
    {
      mNativeValue = nativeValue;
    }

    @NonNull
    private static Stage fromNativeValue(int value)
    {
      for (Stage stage : values())
        if (stage.mNativeValue == value)
          return stage;
      throw new IllegalArgumentException("Unknown native navigation notification stage: " + value);
    }
  }

  @NonNull
  private final String[] mTexts;
  @NonNull
  private final Event mEvent;
  @NonNull
  private final Stage mStage;

  NavigationNotification(@NonNull String[] texts, int nativeEvent, int nativeStage)
  {
    mTexts = texts;
    mEvent = Event.fromNativeValue(nativeEvent);
    mStage = Stage.fromNativeValue(nativeStage);
  }

  @NonNull
  public String[] getTexts()
  {
    return mTexts;
  }

  @NonNull
  public Event getEvent()
  {
    return mEvent;
  }

  @NonNull
  public Stage getStage()
  {
    return mStage;
  }
}
