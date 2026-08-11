package app.organicmaps.incar;

import androidx.annotation.VisibleForTesting;

/** Small deterministic sizing policy for the horizontally scrollable Quick Destinations strip. */
public final class InCarQuickDestinationsLayoutPolicy
{
  public static final int ACTION_SIZE_DP = 56;
  public static final int ACTION_GAP_DP = 8;
  private static final int HORIZONTAL_PADDING_DP = 24;

  private InCarQuickDestinationsLayoutPolicy() {}

  @VisibleForTesting
  static int requiredWidthDp(int visibleActions)
  {
    if (visibleActions <= 0)
      return 0;
    return HORIZONTAL_PADDING_DP + visibleActions * (ACTION_SIZE_DP + ACTION_GAP_DP);
  }

  @VisibleForTesting
  static boolean requiresHorizontalScroll(int availableWidthDp, int visibleActions)
  {
    return availableWidthDp > 0 && requiredWidthDp(visibleActions) > availableWidthDp;
  }
}
