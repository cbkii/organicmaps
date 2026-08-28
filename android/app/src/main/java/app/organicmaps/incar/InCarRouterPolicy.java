package app.organicmaps.incar;

import androidx.annotation.NonNull;
import app.organicmaps.sdk.Router;

/**
 * Authoritative routing-mode policy for the InCar flavour.
 *
 * <p>Normal InCar destination routing always uses {@link Router#Vehicle}. {@link Router#Pedestrian}
 * is only used when the user has explicitly activated a walking last-mile session via
 * {@link InCarWalkingSessionPolicy}. No other router type (Bicycle, Transit, Ruler) is ever
 * selected through InCar presentation; any stale last-used value from a previous session is
 * ignored.
 */
public final class InCarRouterPolicy
{
  private InCarRouterPolicy() {}

  /**
   * Returns the router that should be used when building a new InCar route.
   *
   * @param walkingSessionActive {@code true} only when the user has explicitly activated the
   *                             Walking last-mile flow.
   * @return {@link Router#Pedestrian} during an explicit walking session, {@link Router#Vehicle}
   *         otherwise.
   */
  @NonNull
  public static Router routerForNewDestination(boolean walkingSessionActive)
  {
    return walkingSessionActive ? Router.Pedestrian : Router.Vehicle;
  }
}
