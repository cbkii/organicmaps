package app.organicmaps.search;

final class InCarSearchPresentationPolicy
{
  private InCarSearchPresentationPolicy() {}

  /**
   * Returns {@code true} when the InCar search panel should be considered OPEN (visible to the
   * user for input and results). The panel has exactly two states: open or closed. There are no
   * intermediate drag/half-expanded/collapsed states.
   *
   * @param searchEnabled the logical "search is active" flag.
   */
  static boolean isOpen(boolean searchEnabled)
  {
    return searchEnabled;
  }

  /**
   * Returns {@code true} when the InCar search panel is CLOSED.
   *
   * @param searchEnabled the logical "search is active" flag.
   */
  static boolean isClosed(boolean searchEnabled)
  {
    return !searchEnabled;
  }

  static int panelWidthPx(int availableWidthPx, int minWidthPx, int maxWidthPx, float widthFraction)
  {
    final int available = Math.max(1, availableWidthPx);
    final int proportional = Math.round(available * widthFraction);
    return Math.min(available, Math.max(minWidthPx, Math.min(maxWidthPx, proportional)));
  }

  static boolean shouldSynchronizeNestedScrolling(Boolean previousHasQuery, Integer previousActiveTab,
                                                  Boolean previousMapMode, boolean hasQuery, int activeTab,
                                                  boolean mapMode)
  {
    return previousHasQuery == null || previousActiveTab == null || previousMapMode == null
 || previousHasQuery != hasQuery || previousActiveTab != activeTab || previousMapMode != mapMode;
  }

  static boolean resultsNestedScrollingEnabled(boolean hasQuery, boolean mapMode)
  {
    return hasQuery && !mapMode;
  }
}
