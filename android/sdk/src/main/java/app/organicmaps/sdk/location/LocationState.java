package app.organicmaps.sdk.location;

import androidx.annotation.IntDef;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import app.organicmaps.sdk.Map;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public final class LocationState
{
  public static final String LOCATION_TAG = LocationState.class.getSimpleName();

  public interface ModeChangeListener
  {
    // Used by JNI.
    @Keep
    @SuppressWarnings("unused")
    void onMyPositionModeChanged(int newMode);
  }

  @Retention(RetentionPolicy.SOURCE)
  @IntDef({PENDING_POSITION, NOT_FOLLOW_NO_POSITION, NOT_FOLLOW, FOLLOW, FOLLOW_AND_ROTATE})
  @interface Value
  {}

  // These values should correspond to location::EMyPositionMode enum (from platform/location.hpp)
  public static final int PENDING_POSITION = 0;
  public static final int NOT_FOLLOW_NO_POSITION = 1;
  public static final int NOT_FOLLOW = 2;
  public static final int FOLLOW = 3;
  public static final int FOLLOW_AND_ROTATE = 4;

  // Must correspond to routing::free_driving_snap::SnapMode.
  public static final int FREE_DRIVING_SNAP_OFF = 0;
  public static final int FREE_DRIVING_SNAP_AUTO = 1;
  public static final int FREE_DRIVING_SNAP_STRONG = 2;

  // Must correspond to routing::free_driving_snap::MatchState.
  public static final int FREE_DRIVING_MATCH_DISABLED = 0;
  public static final int FREE_DRIVING_MATCH_RAW = 1;
  public static final int FREE_DRIVING_MATCH_MATCHED = 2;
  public static final int FREE_DRIVING_MATCH_OFF_ROAD_SUSPECTED = 3;

  // These constants should correspond to values defined in platform/location.hpp
  // Leave 0-value as no any error.
  // private static final int ERROR_UNKNOWN = 0;
  // private static final int ERROR_NOT_SUPPORTED = 1;
  public static final int ERROR_DENIED = 2;
  public static final int ERROR_GPS_OFF = 3;
  // public static final int ERROR_TIMEOUT = 4; // Unused on Android (only used on Qt)

  public static native void nativeSwitchToNextMode();
  @Value
  private static native int nativeGetMode();

  public static native void nativeSetListener(@NonNull ModeChangeListener listener);
  public static native void nativeRemoveListener();

  public static native void nativeOnLocationError(int errorCode);

  static native void nativeLocationUpdated(long time, double lat, double lon, float accuracyH, double altitude,
                                           float accuracyV, float speed, float bearing);

  private static native void nativeSetDrivingViewEnabled(boolean enabled, boolean autoReturn, boolean recenter);
  private static native void nativeSetFreeDrivingTracking(boolean enabled, int snapMode, boolean offRoadOverride);
  private static native int nativeGetFreeDrivingMatchState();

  private LocationState() {}

  public static void setDrivingViewEnabled(boolean enabled, boolean autoReturn, boolean recenter)
  {
    if (!Map.isEngineCreated())
      return;
    nativeSetDrivingViewEnabled(enabled, autoReturn, recenter);
  }

  public static void setFreeDrivingTracking(boolean enabled, int snapMode, boolean offRoadOverride)
  {
    if (!Map.isEngineCreated())
      return;
    nativeSetFreeDrivingTracking(enabled, snapMode, offRoadOverride);
  }

  public static int getFreeDrivingMatchState()
  {
    if (!Map.isEngineCreated())
      return FREE_DRIVING_MATCH_DISABLED;
    return nativeGetFreeDrivingMatchState();
  }

  @Value
  public static int getMode()
  {
    if (!Map.isEngineCreated())
      throw new IllegalStateException("Location mode is undefined until engine is created");
    return nativeGetMode();
  }

  public static String nameOf(@Value int mode)
  {
    return switch (mode)
    {
      case PENDING_POSITION -> "PENDING_POSITION";
      case NOT_FOLLOW_NO_POSITION -> "NOT_FOLLOW_NO_POSITION";
      case NOT_FOLLOW -> "NOT_FOLLOW";
      case FOLLOW -> "FOLLOW";
      case FOLLOW_AND_ROTATE -> "FOLLOW_AND_ROTATE";
      default -> "Unknown: " + mode;
    };
  }
}