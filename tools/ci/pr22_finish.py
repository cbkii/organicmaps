#!/usr/bin/env python3
from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected one guarded replacement, found {count}")
    p.write_text(text.replace(old, new, 1))


mwm = "android/app/src/main/java/app/organicmaps/MwmActivity.java"
replace_once(
    mwm,
    "import app.organicmaps.incar.InCarRouterPolicy;\nimport app.organicmaps.incar.InCarSettingsStore;",
    "import app.organicmaps.incar.InCarBackPolicy;\nimport app.organicmaps.incar.InCarRouterPolicy;\nimport app.organicmaps.incar.InCarSettingsStore;",
)
replace_once(
    mwm,
    "    else if (RoutingController.get().hasSavedRoute())\n      RoutingController.get().restoreRoute();",
    """    else if (RoutingController.get().hasSavedRoute())
    {
      if (BuildConfig.IS_IN_CAR)
      {
        final Router restoreRouter =
            InCarRouterPolicy.routerForNewDestination(InCarSettingsStore.isWalkingSessionActive(this));
        RoutingController.get().restoreRoute(restoreRouter);
      }
      else
        RoutingController.get().restoreRoute();
    }
    else if (BuildConfig.IS_IN_CAR)
    {
      // A walking session only has authority while a walking route is active/restorable. Do not let a
      // process-death residue turn the next ordinary InCar destination into a pedestrian route.
      InCarSettingsStore.setWalkingSessionActive(this, false);
    }""",
)
replace_once(
    mwm,
    "    case trackRecordingStatus -> toggleTrackRecordingPP();",
    "    case trackRecordingStatus -> onTrackRecordingOptionSelected();",
)
replace_once(
    mwm,
    """  /** @return true if the back press was consumed by closing an open panel, menu, or route. */
  @Override
  public boolean handleBackPress()
  {
    final RoutingController routingController = RoutingController.get();
    return (closeBottomSheet(MAIN_MENU_ID) || closeBottomSheet(LAYERS_MENU_ID) || closeBottomSheet(ADVANCED_MENU_ID) || collapseNavMenu() || closePlacePage()
            || closePositionChooser() || closeSearchFragment() || routingController.resetToPlanningStateIfNavigating()
            || routingController.cancel());
  }""",
    """  /** @return true if the back press was consumed by closing an open panel, menu, or route. */
  @Override
  public boolean handleBackPress()
  {
    final RoutingController routingController = RoutingController.get();
    if (closeBottomSheet(MAIN_MENU_ID) || closeBottomSheet(LAYERS_MENU_ID) || closeBottomSheet(ADVANCED_MENU_ID)
        || collapseNavMenu() || closePlacePage() || closePositionChooser() || closeSearchFragment()
        || closeInCarRouteEditor())
      return true;

    // InCar active navigation has a dedicated visible End control. Once transient UI is closed,
    // Back is intentionally consumed here instead of falling through to reset/cancel routing.
    if (BuildConfig.IS_IN_CAR
        && InCarBackPolicy.shouldBlockBackFromCancellingNavigation(routingController.isNavigating()))
      return true;

    return routingController.resetToPlanningStateIfNavigating() || routingController.cancel();
  }""",
)
replace_once(
    mwm,
    """  /**
   * @return False if the position chooser was already closed, true otherwise
   */
  private boolean closePositionChooser()""",
    """  private boolean closeInCarRouteEditor()
  {
    if (!BuildConfig.IS_IN_CAR)
      return false;
    final Fragment fragment = getSupportFragmentManager().findFragmentByTag(RoutingPlanFragment.TAG);
    return fragment instanceof RoutingPlanFragment plan && plan.closeInCarRouteEditor();
  }

  /**
   * @return False if the position chooser was already closed, true otherwise
   */
  private boolean closePositionChooser()""",
)

old_menu = """    if (id.equals(MAIN_MENU_ID))
    {
      ArrayList<MenuBottomSheetItem> items = new ArrayList<>();
      items.add(new MenuBottomSheetItem(R.string.placepage_add_place_button, R.drawable.ic_plus,
                                        this::onAddPlaceOptionSelected));
      items.add(new MenuBottomSheetItem(R.string.download_maps, R.drawable.ic_download, getDownloadMapsCounter(),
                                        this::onDownloadMapsOptionSelected));
      mDonatesUrl = Utils.getDonateUrl(getApplicationContext());
      // InCar: donate and other low-frequency items are in Advanced; non-InCar keeps them in primary menu.
      if (!BuildConfig.IS_IN_CAR && !TextUtils.isEmpty(mDonatesUrl))
        items.add(new MenuBottomSheetItem(R.string.donate, R.drawable.ic_donate, this::onDonateOptionSelected));
      items.add(new MenuBottomSheetItem(R.string.settings, R.drawable.ic_settings, this::onSettingsOptionSelected));
      items.add(new MenuBottomSheetItem(R.string.start_track_recording, R.drawable.ic_track_recording_off, -1,
                                        this::onTrackRecordingOptionSelected));
      items.add(new MenuBottomSheetItem(R.string.share_my_location, R.drawable.ic_share,
                                        this::onShareLocationOptionSelected));
      // InCar: Advanced submenu for low-frequency items (donate, etc.).
      if (BuildConfig.IS_IN_CAR)
        items.add(new MenuBottomSheetItem(R.string.in_car_advanced_menu_title, R.drawable.ic_settings,
                                          () -> showBottomSheet(ADVANCED_MENU_ID)));
      return items;
    }
    if (id.equals(ADVANCED_MENU_ID))
    {
      ArrayList<MenuBottomSheetItem> items = new ArrayList<>();
      mDonatesUrl = Utils.getDonateUrl(getApplicationContext());
      if (!TextUtils.isEmpty(mDonatesUrl))
        items.add(new MenuBottomSheetItem(R.string.donate, R.drawable.ic_donate, this::onDonateOptionSelected));
      return items;
    }
"""
new_menu = """    if (id.equals(MAIN_MENU_ID))
    {
      ArrayList<MenuBottomSheetItem> items = new ArrayList<>();
      items.add(new MenuBottomSheetItem(R.string.placepage_add_place_button, R.drawable.ic_plus,
                                        this::onAddPlaceOptionSelected));
      items.add(new MenuBottomSheetItem(R.string.download_maps, R.drawable.ic_download, getDownloadMapsCounter(),
                                        this::onDownloadMapsOptionSelected));

      if (BuildConfig.IS_IN_CAR)
      {
        // Keep the primary automotive menu task-oriented and stable. These entries reuse the existing
        // bookmark/layer/settings/share authorities rather than introducing InCar-specific copies.
        items.add(new MenuBottomSheetItem(R.string.bookmarks, R.drawable.ic_bookmarks_and_tracks, () -> {
          closeFloatingPanels();
          showBookmarks();
        }));
        items.add(new MenuBottomSheetItem(R.string.layers_title, R.drawable.ic_layers, () -> {
          closeFloatingPanels();
          showBottomSheet(LAYERS_MENU_ID);
        }));
        items.add(new MenuBottomSheetItem(R.string.settings, R.drawable.ic_settings, this::onSettingsOptionSelected));
        items.add(new MenuBottomSheetItem(R.string.share_my_location, R.drawable.ic_share,
                                          this::onShareLocationOptionSelected));
        items.add(new MenuBottomSheetItem(R.string.in_car_advanced_menu_title, R.drawable.ic_settings, () -> {
          closeBottomSheet(MAIN_MENU_ID);
          showBottomSheet(ADVANCED_MENU_ID);
        }));
        return items;
      }

      mDonatesUrl = Utils.getDonateUrl(getApplicationContext());
      if (!TextUtils.isEmpty(mDonatesUrl))
        items.add(new MenuBottomSheetItem(R.string.donate, R.drawable.ic_donate, this::onDonateOptionSelected));
      items.add(new MenuBottomSheetItem(R.string.settings, R.drawable.ic_settings, this::onSettingsOptionSelected));
      items.add(new MenuBottomSheetItem(R.string.start_track_recording, R.drawable.ic_track_recording_off, -1,
                                        this::onTrackRecordingOptionSelected));
      items.add(new MenuBottomSheetItem(R.string.share_my_location, R.drawable.ic_share,
                                        this::onShareLocationOptionSelected));
      return items;
    }
    if (id.equals(ADVANCED_MENU_ID))
    {
      ArrayList<MenuBottomSheetItem> items = new ArrayList<>();
      if (BuildConfig.IS_IN_CAR)
      {
        final int trackRecordingTitle = TrackRecorder.nativeIsTrackRecordingEnabled()
                                            ? R.string.track_recording_title
                                            : R.string.start_track_recording;
        items.add(new MenuBottomSheetItem(trackRecordingTitle, R.drawable.ic_track_recording_off, -1,
                                          this::onTrackRecordingOptionSelected));
      }
      mDonatesUrl = Utils.getDonateUrl(getApplicationContext());
      if (!TextUtils.isEmpty(mDonatesUrl))
        items.add(new MenuBottomSheetItem(R.string.donate, R.drawable.ic_donate, this::onDonateOptionSelected));
      return items;
    }
"""
replace_once(mwm, old_menu, new_menu)

plan = "android/app/src/main/java/app/organicmaps/routing/RoutingPlanFragment.java"
replace_once(
    plan,
    """  public void onBuildStarted()
  {
    if (mRoutingBottomMenuController != null)
      mRoutingBottomMenuController.resetBuildProgress();
  }""",
    """  public void onBuildStarted()
  {
    if (mRoutingBottomMenuController != null)
      mRoutingBottomMenuController.resetBuildProgress();
  }

  /** Close the explicit InCar route-edit surface without cancelling route planning. */
  public boolean closeInCarRouteEditor()
  {
    if (!BuildConfig.IS_IN_CAR || mRoutingBottomMenuController == null
        || !mRoutingBottomMenuController.isManageRouteEditing())
      return false;
    mRoutingBottomMenuController.setManageRouteEditing(false);
    updateSheetLayout();
    return true;
  }""",
)

router = "android/app/src/main/java/app/organicmaps/incar/InCarRouterPolicy.java"
replace_once(
    router,
    """ * is only used when the user has explicitly activated a walking last-mile session via
 * {@link InCarWalkingSessionPolicy}. No other router type (Bicycle, Transit, Ruler) is ever""",
    """ * is only used when the user has explicitly activated a walking last-mile session recorded by
 * {@link InCarSettingsStore}. No other router type (Bicycle, Transit, Ruler) is ever""",
)

Path("android/app/src/main/java/app/organicmaps/incar/InCarBackPolicy.java").write_text(
    """package app.organicmaps.incar;

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
"""
)

Path("android/app/src/test/java/app/organicmaps/incar/InCarBackPolicyTest.java").write_text(
    """package app.organicmaps.incar;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class InCarBackPolicyTest
{
  @Test
  public void activeNavigationBackIsBlockedFromCancellation()
  {
    assertTrue(InCarBackPolicy.shouldBlockBackFromCancellingNavigation(true));
  }

  @Test
  public void nonNavigationBackFallsThrough()
  {
    assertFalse(InCarBackPolicy.shouldBlockBackFromCancellingNavigation(false));
  }
}
"""
)

for obsolete in (
    "android/app/src/main/java/app/organicmaps/incar/InCarWalkingSessionPolicy.java",
    "android/app/src/main/java/app/organicmaps/incar/InCarQuickDestinationsOrderPolicy.java",
    "android/app/src/test/java/app/organicmaps/incar/InCarWalkingSessionPolicyTest.java",
    "android/app/src/test/java/app/organicmaps/incar/InCarQuickDestinationsOrderPolicyTest.java",
):
    p = Path(obsolete)
    if not p.exists():
        raise SystemExit(f"Expected obsolete PR scaffold is missing: {obsolete}")
    p.unlink()
