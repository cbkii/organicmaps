package app.organicmaps.settings;

final class InCarSettingsPlacementPolicy
{
  private InCarSettingsPlacementPolicy() {}

  static boolean showRootEntry(boolean isInCar)
  {
    return isInCar;
  }

  static boolean showDedicatedPreference(boolean isInCar, boolean isInCarSettingsPage)
  {
    return isInCar && isInCarSettingsPage;
  }
}
