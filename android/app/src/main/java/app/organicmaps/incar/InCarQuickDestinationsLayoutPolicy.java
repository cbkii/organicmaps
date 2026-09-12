package app.organicmaps.incar;

import androidx.annotation.VisibleForTesting;

/** Deterministic direct-display sizing and capacity policy for the Quick Destinations group. */
public final class InCarQuickDestinationsLayoutPolicy
{
  public static final int PREFERRED_ACTION_SIZE_DP = 76;
  public static final int MIN_ACTION_SIZE_DP = 69;
  public static final int EXTRA_COMPACT_ACTION_SIZE_DP = 55;
  public static final int PRIMARY_ACTION_WIDTH_DP = PREFERRED_ACTION_SIZE_DP;
  public static final int ACTION_SIZE_DP = PREFERRED_ACTION_SIZE_DP;
  public static final int ACTION_ICON_SIZE_DP = 34;
  public static final int ACTION_CORNER_RADIUS_DP = 16;
  public static final int PREFERRED_ACTION_GAP_DP = 6;
  public static final int MIN_ACTION_GAP_DP = 4;
  public static final int SAFE_TOP_GAP_DP = 12;

  private InCarQuickDestinationsLayoutPolicy() {}

  @VisibleForTesting
  static int requiredHeightDp(int visibleActions, int actionSizeDp, int gapDp)
  {
    final int actions = Math.max(0, visibleActions);
    if (actions == 0)
      return 0;
    final int size = Math.max(1, actionSizeDp);
    final int gap = Math.max(0, gapDp);
    return actions * size + (actions - 1) * gap;
  }

  @VisibleForTesting
  static int maxVisibleActions(int availableHeightDp, int actionSizeDp, int gapDp)
  {
    final int size = Math.max(1, actionSizeDp);
    final int gap = Math.max(0, gapDp);
    if (availableHeightDp < size)
      return 0;
    return 1 + Math.max(0, availableHeightDp - size) / (size + gap);
  }

  @VisibleForTesting
  static int resolvedGapDp(int availableHeightDp, int actionSizeDp, int visibleActions)
  {
    final int actions = Math.max(0, visibleActions);
    if (actions <= 1)
      return 0;

    final int size = Math.max(1, actionSizeDp);
    final int availableForGaps = Math.max(0, availableHeightDp - actions * size);
    final int fittedGap = availableForGaps / (actions - 1);
    return Math.max(MIN_ACTION_GAP_DP, Math.min(PREFERRED_ACTION_GAP_DP, fittedGap));
  }

  @VisibleForTesting
  static int directActionCountForCapacity(int capacity, int directActionCount, int existingOverflowCount)
  {
    final int slots = Math.max(0, capacity);
    final int direct = Math.max(0, directActionCount);
    final int overflow = Math.max(0, existingOverflowCount);
    if (slots == 0 || direct == 0)
      return 0;

    final boolean needsMore = overflow > 0 || direct > slots;
    if (!needsMore)
      return Math.min(direct, slots);
    return Math.min(direct, Math.max(0, slots - 1));
  }

  @VisibleForTesting
  static boolean shouldShowMore(int capacity, int directActionCount, int visibleDirectCount, int overflowCount)
  {
    return capacity > 0 && (Math.max(0, overflowCount) > 0 || Math.max(0, directActionCount) > visibleDirectCount);
  }
}
