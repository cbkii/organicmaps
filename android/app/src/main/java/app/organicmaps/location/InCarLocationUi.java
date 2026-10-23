package app.organicmaps.location;

import android.app.Activity;
import android.location.Location;
import app.organicmaps.MwmApplication;
import app.organicmaps.R;
import app.organicmaps.sdk.Framework;
import app.organicmaps.sdk.Map;
import app.organicmaps.sdk.location.LocationHelper;
import app.organicmaps.sdk.routing.RoutingController;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Read-only presentation of the application's one location owner; no independent subscription. */
public final class InCarLocationUi
{
  private InCarLocationUi() {}

  public static int statusText(LocationHelper helper)
  {
    return switch (helper.getAcquisitionStateSnapshot())
    {
      case STOPPED -> R.string.in_car_location_stopped;
      case WAITING_FOR_PERMISSION -> R.string.in_car_location_permission_required;
      case PROVIDER_UNAVAILABLE -> R.string.in_car_location_unavailable;
      case ACQUIRING -> R.string.in_car_location_acquiring;
      case APPROXIMATE -> R.string.in_car_location_approximate;
      case FIX_RECEIVED -> R.string.in_car_location_confirmed;
      case FIX_STALE -> R.string.in_car_location_stale;
      case RECOVERING -> R.string.in_car_location_recovering;
    };
  }

  public static void showDetails(Activity activity)
  {
    final LocationHelper helper = MwmApplication.from(activity).getLocationHelper();
    final MaterialAlertDialogBuilder builder =
        new MaterialAlertDialogBuilder(activity)
            .setTitle(statusText(helper))
            .setMessage(helper.getAcquisitionDiagnostics().replace(", ", "\n"))
            .setNegativeButton(R.string.close, null)
            .setPositiveButton(R.string.in_car_location_retry, (dialog, which) -> { helper.retryAcquisition(); });
    final Location cached = helper.getCachedLocation();
    if (cached != null && Map.isEngineCreated() && !RoutingController.get().isNavigating()
        && !RoutingController.get().isPlanning())
      builder.setNeutralButton(R.string.in_car_location_show_cache, (dialog, which) -> {
        // Explicit map preview only: never fed to nativeLocationUpdated or used as a route origin.
        if (helper.getCachedLocation() != null && Map.isEngineCreated() && !RoutingController.get().isNavigating()
            && !RoutingController.get().isPlanning())
          Framework.nativeSetViewportCenter(cached.getLatitude(), cached.getLongitude(), 14);
      });
    builder.show();
  }
}
