package app.organicmaps.incar;

/** Pure guard used by the real map-screen Back path for fixed InCar navigation. */
public final class InCarBackPolicy
{
  private InCarBackPolicy() {}

  /**
   * Returns whether Back must be consumed after transient surfaces have already been closed.
   * Active InCar navigation is ended only by the dedicated visible End Navigation control.
   */
  public static boolean shouldBlockBackFromCancellingNavigation(boolean navigating)
  {
    return navigating;
  }
}
