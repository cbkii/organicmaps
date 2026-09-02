package app.organicmaps.incar;

import app.organicmaps.sdk.Map;

/** Narrow JNI seam for the InCar-only one-shot launcher camera. */
final class InCarStartupCameraNative
{
  private InCarStartupCameraNative() {}

  static void showLocalArea(double latitude, double longitude, double radiusMeters)
  {
    if (Map.isEngineCreated())
      nativeShowLocalArea(latitude, longitude, radiusMeters);
  }

  static void requestFollowAndRotate(boolean forceDrivingArea, boolean keepDrivingViewEnabled, boolean autoReturn)
  {
    if (Map.isEngineCreated())
      nativeRequestFollowAndRotate(forceDrivingArea, keepDrivingViewEnabled, autoReturn);
  }

  private static native void nativeShowLocalArea(double latitude, double longitude, double radiusMeters);
  private static native void nativeRequestFollowAndRotate(boolean forceDrivingArea, boolean keepDrivingViewEnabled,
                                                          boolean autoReturn);
}
