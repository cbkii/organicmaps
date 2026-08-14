package app.organicmaps.incar;

import androidx.annotation.VisibleForTesting;

/** Small deterministic sizing policy for the Quick Destinations strip. */
public final class InCarQuickDestinationsLayoutPolicy
{
  public static final int PRIMARY_ACTION_WIDTH_DP = 64;
  public static final int ACTION_SIZE_DP = 64;
  public static final int ACTION_GAP_DP = 10;
  private static final int HORIZONTAL_PADDING_DP = 24;

  private InCarQuickDestinationsLayoutPolicy() {}

  @VisibleForTesting
  static int requiredWidthDp(int visibleDestinationActions)
  {
    if (visibleDestinationActions <= 0)
      return HORIZONTAL_PADDING_DP + PRIMARY_ACTION_WIDTH_DP + ACTION_GAP_DP;
    return HORIZONTAL_PADDING_DP + PRIMARY_ACTION_WIDTH_DP + ACTION_GAP_DP
  + visibleDestinationActions * (ACTION_SIZE_DP + ACTION_GAP_DP);
  }

  @VisibleForTesting
  static int maxVisibleDestinationActions(int availableWidthDp)
  {
    final int availableForActions = availableWidthDp - HORIZONTAL_PADDING_DP - PRIMARY_ACTION_WIDTH_DP - ACTION_GAP_DP;
    if (availableForActions <= 0)
      return 0;
    return availableForActions / (ACTION_SIZE_DP + ACTION_GAP_DP);
  }

  @VisibleForTesting
  static boolean requiresOverflow(int availableWidthDp, int visibleDestinationActions)
  {
    return availableWidthDp > 0 && visibleDestinationActions > maxVisibleDestinationActions(availableWidthDp);
  }
}
