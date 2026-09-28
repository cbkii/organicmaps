package app.organicmaps;

/** Decisions after navigation or recording ends; never start a stopped provider. */
final class LocationFeatureStopPolicy
{
  private LocationFeatureStopPolicy() {}

  static boolean shouldReassessBackground(boolean foreground)
  {
    return !foreground;
  }

  static boolean shouldAdjustForegroundRate(boolean foreground, boolean hasResumedActivity, boolean providerActive,
                                            boolean permissionGranted, boolean androidLocationEnabled,
                                            boolean explicitlyOff)
  {
    return foreground && hasResumedActivity && providerActive && permissionGranted && androidLocationEnabled
        && !explicitlyOff;
  }
}
