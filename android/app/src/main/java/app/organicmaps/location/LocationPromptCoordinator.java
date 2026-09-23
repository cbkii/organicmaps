package app.organicmaps.location;

/**
 * Keeps Android location permission and settings UI single-flight while callers re-check the
 * current platform state on every callback.
 *
 * <p>The coordinator deliberately does not cache permission or provider state. Those values can be
 * changed outside Organic Maps while the activity is backgrounded, so callers must supply fresh
 * Android state for every decision.</p>
 */
public final class LocationPromptCoordinator
{
  public enum PermissionAction
  {
    NONE,
    REQUEST_PERMISSION,
    SHOW_APP_SETTINGS
  }

  public enum ProviderAction
  {
    NONE,
    REQUEST_PERMISSION,
    SHOW_APP_SETTINGS,
    SHOW_LOCATION_SETTINGS,
    IGNORE_STALE_CALLBACK
  }

  private boolean mPermissionRequestPending;
  private boolean mLocationSettingsTransitionPending;
  private boolean mPermissionPermanentlyDenied;

  /**
   * Classifies a location operation which currently requires Android runtime permission.
   */
  public PermissionAction onPermissionRequired(boolean requiredPermissionGranted, boolean locationUiShowing)
  {
    if (requiredPermissionGranted)
    {
      mPermissionPermanentlyDenied = false;
      return PermissionAction.NONE;
    }

    if (locationUiShowing || mPermissionRequestPending || mLocationSettingsTransitionPending)
      return PermissionAction.NONE;

    if (mPermissionPermanentlyDenied)
      return PermissionAction.SHOW_APP_SETTINGS;

    mPermissionRequestPending = true;
    return PermissionAction.REQUEST_PERMISSION;
  }

  /**
   * Completes one Android permission request using the current permission and rationale state.
   */
  public void finishPermissionRequest(boolean permissionGranted, boolean canShowRationale)
  {
    mPermissionRequestPending = false;
    mPermissionPermanentlyDenied = !permissionGranted && !canShowRationale;
  }

  /**
   * Classifies a provider-unavailable callback using current Android permission/provider state.
   */
  public ProviderAction onProviderUnavailable(boolean locationPermissionGranted, boolean locationServicesEnabled,
                                              boolean locationUiShowing)
  {
    if (!locationPermissionGranted)
    {
      return switch (onPermissionRequired(false, locationUiShowing))
      {
        case REQUEST_PERMISSION -> ProviderAction.REQUEST_PERMISSION;
        case SHOW_APP_SETTINGS -> ProviderAction.SHOW_APP_SETTINGS;
        case NONE -> ProviderAction.NONE;
      };
    }

    mPermissionPermanentlyDenied = false;
    if (locationServicesEnabled)
      return ProviderAction.IGNORE_STALE_CALLBACK;

    if (locationUiShowing || mLocationSettingsTransitionPending)
      return ProviderAction.NONE;

    return ProviderAction.SHOW_LOCATION_SETTINGS;
  }

  /**
   * Marks a location-related Android settings activity as outstanding.
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

  public boolean isPermissionPermanentlyDenied()
  {
    return mPermissionPermanentlyDenied;
  }
}
