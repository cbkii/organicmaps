package app.organicmaps.incar;

import androidx.annotation.VisibleForTesting;

/** Small deterministic vertical sizing policy for the Quick Destinations stack. */
public final class InCarQuickDestinationsLayoutPolicy
{
  public static final int PRIMARY_ACTION_WIDTH_DP = 56;
  public static final int ACTION_SIZE_DP = 56;
  public static final int ACTION_ICON_SIZE_DP = 34;
  public static final int ACTION_CORNER_RADIUS_DP = 16;
  public static final int PREFERRED_ACTION_GAP_DP = 6;
  public static final int MIN_ACTION_GAP_DP = 4;
  public static final int SAFE_TOP_GAP_DP = 12;

  private InCarQuickDestinationsLayoutPolicy() {}

  @VisibleForTesting
  static int requiredHeightDp(int visibleDestinationActions, int gapDp)
  {
    final int actions = Math.max(0, visibleDestinationActions);
    final int gap = Math.max(0, gapDp);
    return ACTION_SIZE_DP + actions * ACTION_SIZE_DP + actions * gap;
  }

  @VisibleForTesting
  static int resolvedGapDp(int availableHeightDp, int visibleDestinationActions)
  {
    final int actions = Math.max(0, visibleDestinationActions);
    if (actions == 0)
      return 0;

    final int heightWithoutGaps = ACTION_SIZE_DP + actions * ACTION_SIZE_DP;
    final int availableForGaps = Math.max(0, availableHeightDp - heightWithoutGaps);
    final int fittedGap = availableForGaps / actions;
    return Math.max(MIN_ACTION_GAP_DP, Math.min(PREFERRED_ACTION_GAP_DP, fittedGap));
  }

  @VisibleForTesting
  static int maxVisibleDestinationActions(int availableHeightDp)
  {
    final int availableForActions = availableHeightDp - ACTION_SIZE_DP;
    if (availableForActions <= 0)
      return 0;
    return availableForActions / (ACTION_SIZE_DP + MIN_ACTION_GAP_DP);
  }

  @VisibleForTesting
  static boolean requiresOverflow(int availableHeightDp, int visibleDestinationActions)
  {
    return availableHeightDp > 0 && visibleDestinationActions > maxVisibleDestinationActions(availableHeightDp);
  }
}
