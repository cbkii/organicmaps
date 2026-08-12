package app.organicmaps.incar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.organicmaps.sdk.Router;

/** Pure one-shot eligibility rules for exact InCar Quick Destination navigation. */
final class InCarQuickNavigationPolicy
{
  enum Decision
  {
    WAIT,
    CLEAR,
    START
  }

  private InCarQuickNavigationPolicy() {}

  @NonNull
  static Router exactRouter()
  {
    return Router.Vehicle;
  }

  @NonNull
  static Decision evaluate(boolean planning, boolean navigating, boolean buildError, boolean built,
                           boolean successfulBuild, boolean vehicleRouter, @Nullable InCarQuickDestination pending,
                           @Nullable InCarQuickDestination builtDestination)
  {
    if (pending == null || navigating || buildError)
      return Decision.CLEAR;

    // RoutingController can report BUILT for NEED_MORE_MAPS. That is not a successful route and must remain
    // under the normal missing-maps/error UI rather than auto-starting navigation.
    if (built && !successfulBuild)
      return Decision.CLEAR;

    if (!built)
      return planning ? Decision.WAIT : Decision.CLEAR;

    if (!vehicleRouter || !pending.samePlace(builtDestination))
      return Decision.CLEAR;

    return Decision.START;
  }
}
