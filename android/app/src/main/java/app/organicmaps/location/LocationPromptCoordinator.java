package app.organicmaps.location;

/**
 * Keeps Android location permission and provider-settings UI single-flight while callers re-check
 * the current platform state on every callback.
 *
 * <p>The coordinator deliberately does not cache permission or provider state. Those values can be
 * changed outside Organic Maps while the activity is backgrounded, so callers must supply fresh
 * Android state for every decision.</p>
 */
public final class LocationPromptCoordinator
{
  public enum ProviderAction
  {
    NONE,
    REQUEST_PERMISSION,
    SHOW_LOCATION_SETTINGS,
    IGNORE_STALE_CALLBACK
  }

  private boolean mPermissionRequestPending;
  private boolean mLocationSettingsTransitionPending;

  /**
   * Reserves the single outstanding runtime-permission request.
   *
   * @return true only when the caller should launch the Android permission request now.
   */
  public boolean beginPermissionRequest(boolean requiredPermissionGranted, boolean locationUiShowing)
  {
    if (requiredPermissionGranted || locationUiShowing || mPermissionRequestPending
        || mLocationSettingsTransitionPending)
      return false;

    mPermissionRequestPending = true;
    return true;
  }

  public void finishPermissionRequest()
  {
    mPermissionRequestPending = false;
  }

  /**
   * Classifies a provider-unavailable callback using current Android permission/provider state.
   */
  public ProviderAction onProviderUnavailable(boolean locationPermissionGranted, boolean locationServicesEnabled,
                                              boolean locationUiShowing)
  {
    if (!locationPermissionGranted)
      return beginPermissionRequest(false, locationUiShowing) ? ProviderAction.REQUEST_PERMISSION : ProviderAction.NONE;

    if (locationServicesEnabled)
      return ProviderAction.IGNORE_STALE_CALLBACK;

    if (locationUiShowing || mLocationSettingsTransitionPending)
      return ProviderAction.NONE;

    return ProviderAction.SHOW_LOCATION_SETTINGS;
  }

  /**
   * Marks the system location-settings activity as outstanding.
   *
   * @return true only for the first launch until {@link #finishLocationSettingsTransition()}.
   */
  public boolean beginLocationSettingsTransition()
  {
    if (mLocationSettingsTransitionPending || mPermissionRequestPending)
      return false;

    mLocationSettingsTransitionPending = true;
    return true;
  }

  public void finishLocationSettingsTransition()
  {
    mLocationSettingsTransitionPending = false;
  }

  public boolean isPermissionRequestPending()
  {
    return mPermissionRequestPending;
  }

  public boolean isLocationSettingsTransitionPending()
  {
    return mLocationSettingsTransitionPending;
  }
}
