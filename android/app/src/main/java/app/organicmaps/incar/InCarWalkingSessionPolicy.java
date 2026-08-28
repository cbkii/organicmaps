package app.organicmaps.incar;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import app.organicmaps.sdk.Router;

/**
 * Pure-logic walking last-mile session policy for the InCar flavour.
 *
 * <p>Instances are not thread-safe; they must be used on the UI thread only. Persist and restore
 * the session active state via {@link InCarSettingsStore#isWalkingSessionActive(android.content.Context)}
 * / {@link InCarSettingsStore#setWalkingSessionActive(android.content.Context, boolean)}.
 */
public final class InCarWalkingSessionPolicy
{
  private boolean mActive;

  /** Create a policy with no walking session active. */
  public InCarWalkingSessionPolicy()
  {
    this(false);
  }

  /** Restore a policy from persisted state. */
  public InCarWalkingSessionPolicy(boolean restoredActive)
  {
    mActive = restoredActive;
  }

  /** Activate the walking last-mile session. */
  public void startWalking()
  {
    mActive = true;
  }

  /** Deactivate the walking session and return to normal vehicle routing. */
  public void endWalking()
  {
    mActive = false;
  }

  /** @return {@code true} if a walking last-mile session is currently active. */
  public boolean isActive()
  {
    return mActive;
  }

  /**
   * Returns the router that should be used given the current session state.
   *
   * @return {@link Router#Pedestrian} when a walking session is active,
   *         {@link Router#Vehicle} otherwise.
   */
  @NonNull
  public Router routerForSession()
  {
    return InCarRouterPolicy.routerForNewDestination(mActive);
  }

  @VisibleForTesting
  boolean isWalkingActive()
  {
    return mActive;
  }
}
