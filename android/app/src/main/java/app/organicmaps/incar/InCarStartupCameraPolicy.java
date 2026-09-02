package app.organicmaps.incar;

import android.content.Intent;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.organicmaps.MwmActivity;

/** Pure decisions for the one-shot InCar launcher camera. */
public final class InCarStartupCameraPolicy
{
  public static final String EXTRA_STARTUP_CAMERA_PENDING = "app.organicmaps.incar.STARTUP_CAMERA_PENDING";

  private InCarStartupCameraPolicy() {}

  /**
   * A launcher entry is deliberately narrower than an Activity resume. Recents/history and every known explicit map
   * target retain their existing viewport authority.
   */
  public static boolean isPlainLauncherIntent(@Nullable Intent intent)
  {
    if (intent == null || !Intent.ACTION_MAIN.equals(intent.getAction()) || !intent.hasCategory(Intent.CATEGORY_LAUNCHER))
      return false;
    if ((intent.getFlags() & Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0 || intent.getData() != null
        || intent.getClipData() != null)
      return false;
    return !hasExplicitMapTarget(intent);
  }

  public static boolean hasRoutingCameraAuthority(boolean planning, boolean building, boolean navigating,
                                                  boolean hasSavedRoute, boolean nativeRoutingActive)
  {
    return planning || building || navigating || hasSavedRoute || nativeRoutingActive;
  }

  public static boolean shouldShowDrivingArea(boolean autoFollowOnLaunch,
                                              @NonNull InCarStartupCameraStore.StartupMapView startupMapView,
                                              boolean hasCameraAnchor, boolean routingCameraAuthority)
  {
    return autoFollowOnLaunch && startupMapView == InCarStartupCameraStore.StartupMapView.DRIVING_AREA
        && hasCameraAnchor && !routingCameraAuthority;
  }

  public static boolean shouldRequestFollowAndRotate(boolean autoFollowOnLaunch, boolean routingCameraAuthority)
  {
    return autoFollowOnLaunch && !routingCameraAuthority;
  }

  private static boolean hasExplicitMapTarget(@NonNull Intent intent)
  {
    return intent.hasExtra(MwmActivity.EXTRA_COUNTRY_ID) || intent.hasExtra(MwmActivity.EXTRA_CATEGORY_ID)
        || intent.hasExtra(MwmActivity.EXTRA_BOOKMARK_ID) || intent.hasExtra(MwmActivity.EXTRA_TRACK_ID);
  }
}
