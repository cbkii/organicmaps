package app.organicmaps.search;

final class InCarSearchPresentationPolicy
{
  private InCarSearchPresentationPolicy() {}

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
