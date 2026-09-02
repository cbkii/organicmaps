package app.organicmaps.incar;

import android.os.Handler;
import android.os.Looper;
import app.organicmaps.sdk.Map;

/** Narrow JNI seam for the InCar-only one-shot launcher camera. */
final class InCarStartupCameraNative
{
  private static final long PENDING_TIMEOUT_MS = 10_000L;
  private static final Handler PENDING_TIMEOUT_HANDLER = new Handler(Looper.getMainLooper());
  private static final Runnable CANCEL_PENDING = () ->
  {
    if (Map.isEngineCreated())
      nativeCancelPending();
  };

  private InCarStartupCameraNative() {}

  static void showLocalArea(double latitude, double longitude, double radiusMeters)
  {
    if (Map.isEngineCreated())
      nativeShowLocalArea(latitude, longitude, radiusMeters);
  }

  static void requestFollowAndRotate(boolean forceDrivingArea, boolean keepDrivingViewEnabled, boolean autoReturn)
  {
    PENDING_TIMEOUT_HANDLER.removeCallbacks(CANCEL_PENDING);
    if (!Map.isEngineCreated())
      return;

    nativeRequestFollowAndRotate(forceDrivingArea, keepDrivingViewEnabled, autoReturn);
    PENDING_TIMEOUT_HANDLER.postDelayed(CANCEL_PENDING, PENDING_TIMEOUT_MS);
  }

  static void cancelPending()
  {
    PENDING_TIMEOUT_HANDLER.removeCallbacks(CANCEL_PENDING);
    if (Map.isEngineCreated())
      nativeCancelPending();
  }

  private static native void nativeShowLocalArea(double latitude, double longitude, double radiusMeters);
  private static native void nativeRequestFollowAndRotate(boolean forceDrivingArea, boolean keepDrivingViewEnabled,
                                                          boolean autoReturn);
  private static native void nativeCancelPending();
}
