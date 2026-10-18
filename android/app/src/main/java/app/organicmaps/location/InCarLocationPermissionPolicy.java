package app.organicmaps.location;

/** Keeps background access optional and isolates Android 10's combined runtime request. */
public final class InCarLocationPermissionPolicy
{
  private InCarLocationPermissionPolicy() {}
  public enum Access { DENIED, WHILE_USING, ALWAYS }

  public static boolean includeBackgroundInForegroundRequest(boolean inCar, int sdk)
  {
    return inCar && sdk == 29;
  }

  public static Access access(int sdk, boolean foreground, boolean background)
  {
    if (!foreground)
      return Access.DENIED;
    return sdk < 29 || background ? Access.ALWAYS : Access.WHILE_USING;
  }
}
