package app.organicmaps.sdk.sound;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.organicmaps.sdk.routing.CarDirection;
import app.organicmaps.sdk.routing.NavigationNotification;
import app.organicmaps.sdk.routing.RoutingInfo;

/** Maps typed guidance events to the common six-tone InCar catalogue. No locale/string parsing. */
public final class NavigationTonePolicy
{
  public static final int TONE_COUNT = 6;
  public static final int ADVANCE = 0;
  public static final int LEFT = 1;
  public static final int RIGHT = 2;
  public static final int COMPLEX = 3;
  public static final int ROUTE = 4;
  public static final int GPS = 5;

  private NavigationTonePolicy() {}

  public static int selectToneIndex(@Nullable NavigationNotification notification,
                                    @Nullable RoutingInfo info,
                                    @NonNull OfflineNavigationVoicePack.GpsSignalEvent gps)
  {
    if (gps == OfflineNavigationVoicePack.GpsSignalEvent.LOST)
      return GPS;
    if (gps == OfflineNavigationVoicePack.GpsSignalEvent.RESTORED)
      return ROUTE;
    if (notification == null || notification.getEvent() == NavigationNotification.Event.SPEED_CAMERA)
      return -1; // Camera warnings have their own existing, verified sound path.
    if (notification.getEvent() == NavigationNotification.Event.ROUTE_RECALCULATION)
      return ROUTE;
    if (notification.getStage() == NavigationNotification.Stage.ADVANCE || info == null)
      return ADVANCE;
    return directionTone(info.carDirection);
  }

  public static int directionTone(@Nullable CarDirection direction)
  {
    if (direction == null)
      return ADVANCE;
    return switch (direction)
    {
      case TurnLeft, TurnSlightLeft, TurnSharpLeft -> LEFT;
      case TurnRight, TurnSlightRight, TurnSharpRight -> RIGHT;
      case UTurnLeft, UTurnRight, EnterRoundAbout, LeaveRoundAbout, StayOnRoundAbout,
           ExitHighwayToLeft, ExitHighwayToRight -> COMPLEX;
      case ReachedYourDestination -> ROUTE;
      default -> ADVANCE;
    };
  }
}
