package app.organicmaps.incar;

import androidx.annotation.NonNull;

/**
 * Pure-logic Back-press policy for the InCar flavour.
 *
 * <p>Back represents the user's <em>visible</em> interaction hierarchy, not every internal
 * bottom-sheet presentation state. The mapping is:
 *
 * <ul>
 *   <li>More panel open → close More
 *   <li>Place details open → return to compact Place card
 *   <li>Search open → close Search
 *   <li>Route Edit open → return to route preview
 *   <li>Clean active navigation → do nothing (Back must NOT cancel navigation)
 * </ul>
 *
 * <p>Navigation is ended only through the dedicated End Navigation control, never via Back.
 */
public final class InCarBackPolicy
{
  /** The surface that is currently visible and determines Back behaviour. */
  public enum ActiveSurface
  {
    /** No special surface is open; the map is the primary view. */
    MAP,
    /** A "More" overflow panel/sheet is open (route More, nav More, etc.). */
    MORE,
    /** The Place Page detail surface is showing full details. */
    PLACE_DETAILS,
    /** The Search panel is open. */
    SEARCH,
    /** The Route Edit surface is open. */
    ROUTE_EDIT,
    /** Active turn-by-turn navigation is in progress with no transient surface on top. */
    CLEAN_NAVIGATION
  }

  /** The action that the Back press should perform. */
  public enum BackAction
  {
    /** Close/dismiss the currently open More panel. */
    CLOSE_MORE,
    /** Return from Place details to the compact Place card. */
    RETURN_TO_PLACE_CARD,
    /** Close the Search panel. */
    CLOSE_SEARCH,
    /** Return from Route Edit to the route preview. */
    RETURN_TO_ROUTE_PREVIEW,
    /**
     * Do nothing. Back during clean active navigation is intentionally a no-op; the user must
     * use the dedicated End Navigation button to stop navigation.
     */
    DO_NOTHING,
    /** Default Android back behaviour (pop fragment stack / finish activity). */
    DEFAULT_BACK
  }

  private InCarBackPolicy() {}

  /**
   * Returns the {@link BackAction} that should be executed when the user presses Back.
   *
   * @param surface the currently visible surface that owns the Back action.
   */
  @NonNull
  public static BackAction actionFor(@NonNull ActiveSurface surface)
  {
    return switch (surface)
    {
      case MORE -> BackAction.CLOSE_MORE;
      case PLACE_DETAILS -> BackAction.RETURN_TO_PLACE_CARD;
      case SEARCH -> BackAction.CLOSE_SEARCH;
      case ROUTE_EDIT -> BackAction.RETURN_TO_ROUTE_PREVIEW;
      case CLEAN_NAVIGATION -> BackAction.DO_NOTHING;
      case MAP -> BackAction.DEFAULT_BACK;
    };
  }

  /**
   * Convenience: returns {@code true} if Back should NOT cancel active navigation.
   *
   * <p>This can be used as a guard before delegating to the default back stack logic to ensure
   * navigation is never silently terminated by the Back action.
   *
   * @param navigating whether active turn-by-turn navigation is in progress.
   * @param hasTransientSurface whether any transient surface (More, Search, Place details, Route
   *                            Edit) is currently visible on top of the navigation HUD.
   * @return {@code true} when Back must be blocked from cancelling navigation.
   */
  public static boolean shouldBlockBackFromCancellingNavigation(boolean navigating, boolean hasTransientSurface)
  {
    return navigating && !hasTransientSurface;
  }
}
