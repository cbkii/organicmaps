package app.organicmaps.sdk.location;

import android.app.PendingIntent;
import android.location.Location;
import androidx.annotation.NonNull;

public interface LocationListener
{
  void onLocationUpdated(@NonNull Location location);

  /** Called after this provider observation has reached native routing/map metadata. Cached replays may be stale. */
  default void onLocationUpdatedNative(@NonNull Location location) {}

  /** The pre-native observation was delivered, but native completion cannot be claimed. */
  default void onLocationNativeUpdateSkipped(@NonNull Location location) {}

  default void onLocationUpdateTimeout()
  {
    // No op.
  }

  default void onLocationDisabled()
  {
    // No op.
  }

  default void onLocationResolutionRequired(@NonNull PendingIntent pendingIntent)
  {
    // No op.
  }
}
