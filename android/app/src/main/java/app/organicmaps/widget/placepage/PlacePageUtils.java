package app.organicmaps.widget.placepage;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Build;
import android.graphics.Rect;
import android.view.Menu;
import android.view.View;
import android.widget.PopupMenu;
import androidx.annotation.NonNull;
import app.organicmaps.BuildConfig;
import app.organicmaps.MwmActivity;
import app.organicmaps.MwmApplication;
import app.organicmaps.R;
import app.organicmaps.incar.InCarOverlayCoordinator;
import app.organicmaps.routing.RoutingPlanViewModel;
import app.organicmaps.widget.placepage.PlacePageViewModel;
import app.organicmaps.search.SearchPageViewModel;
import androidx.lifecycle.ViewModelProvider;
import app.organicmaps.sdk.Map;
import app.organicmaps.sdk.MapView;
import app.organicmaps.sdk.routing.RoutingController;
import app.organicmaps.sdk.Framework;
import app.organicmaps.util.Utils;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import java.util.List;

public class PlacePageUtils
{
  public static void updateMapViewport(@NonNull View parent, int placePageDistanceToTop, int viewportMinHeight)
  {
    updateMapViewport(parent, placePageDistanceToTop, viewportMinHeight, 4);
  }

  private static void updateMapViewport(@NonNull View parent, int placePageDistanceToTop,
                                        int viewportMinHeight, int surfaceRetries)
  {
    parent.post(() -> {
      // Because of the post(), this lambda is called after the car.SurfaceRenderer.onStableAreaChanged() and breaks the
      // visibleRect configuration
      if (MwmApplication.from(parent.getContext()).getDisplayManager().isCarDisplayUsed())
        return;
      if (BuildConfig.IS_IN_CAR)
      {
        // InCar runs in a genuine Android 10 task window: screen pixels and
        // renderer-local pixels are not interchangeable. Keep native viewport
        // ownership here, but use measured panel/camera bounds and restore the
        // full renderer when the panel is off-screen. Never call before Drape.
        if (!parent.isAttachedToWindow() || !Map.isEngineCreated())
          return;
        final View map = parent.findViewById(R.id.map);
        if (!(map instanceof MapView mapView) || mapView.getWidth() <= 0 || mapView.getHeight() <= 0)
          return;
        // TS18 task changes can transiently leave the SurfaceHolder dimensions
        // behind the View. Pixel geometry is authoritative only after the native
        // map has actually accepted the current Surface frame.
        if (mapView.getSurfaceFrameWidth() != mapView.getWidth()
            || mapView.getSurfaceFrameHeight() != mapView.getHeight()
            || mapView.getLastAppliedSurfaceWidth() != mapView.getWidth()
            || mapView.getLastAppliedSurfaceHeight() != mapView.getHeight())
        {
          if (surfaceRetries > 0)
            parent.postDelayed(() -> updateMapViewport(parent, placePageDistanceToTop,
                                                       viewportMinHeight, surfaceRetries - 1), 75);
          return;
        }
        if (RoutingController.get().isNavigating())
        {
          // Follow/navigation continues to own map positioning and heading.
          Framework.nativeSetVisibleRect(0, 0, mapView.getWidth(), mapView.getHeight());
          return;
        }
        // Search, place page and routing all call this established method.
        // Resolve the currently active panel, not the caller's name: a hidden
        // fragment's stale bounds must never win over the frontmost sheet.
        // A fragment may inflate its root with an AppCompat theme wrapper
        // rather than the Activity directly; resolve the owning task without
        // assuming a particular Context implementation.
        Context context = parent.getContext();
        MwmActivity activity = null;
        for (int depth = 0; depth < 8 && context != null; ++depth)
        {
          if (context instanceof MwmActivity owner)
          {
            activity = owner;
            break;
          }
          context = context instanceof ContextWrapper wrapper ? wrapper.getBaseContext() : null;
        }
        if (activity == null)
          return;
        final ViewModelProvider models = new ViewModelProvider(activity);
        final boolean searching = Boolean.TRUE.equals(
            models.get(SearchPageViewModel.class).getSearchEnabled().getValue());
        final boolean selected = models.get(PlacePageViewModel.class).getMapObject().getValue() != null;
        final boolean routing = Boolean.TRUE.equals(
            models.get(RoutingPlanViewModel.class).getShowRoutingBottomSheet().getValue());
        final View routePanel = parent.findViewById(R.id.routing_sheet_frame);
        final View placePanel = parent.findViewById(R.id.placepage);
        final View searchPanel = parent.findViewById(R.id.search_page_container);
        final View activePanel = routing && InCarOverlayCoordinator.visiblePanel(routePanel) ? routePanel
                               : selected && InCarOverlayCoordinator.visiblePanel(placePanel) ? placePanel
                               : searching && InCarOverlayCoordinator.visiblePanel(searchPanel) ? searchPanel : null;
        final Rect rect = InCarOverlayCoordinator.placePageViewport(
            map, activePanel, parent.findViewById(R.id.zoom_buttons_container),
            viewportMinHeight, parent.getResources().getDimensionPixelSize(R.dimen.margin_half));
        if (rect.width() >= 100 && rect.height() >= 100)
          Framework.nativeSetVisibleRect(rect.left, rect.top, rect.right, rect.bottom);
        return;
      }
      final int screenWidth = parent.getWidth();
      if (placePageDistanceToTop >= viewportMinHeight)
        Framework.nativeSetVisibleRect(0, 0, screenWidth, placePageDistanceToTop);
    });
  }

  public static boolean isSettlingState(@BottomSheetBehavior.State int state)
  {
    return state == BottomSheetBehavior.STATE_SETTLING;
  }

  public static boolean isDraggingState(@BottomSheetBehavior.State int state)
  {
    return state == BottomSheetBehavior.STATE_DRAGGING;
  }

  static boolean isCollapsedState(@BottomSheetBehavior.State int state)
  {
    return state == BottomSheetBehavior.STATE_COLLAPSED;
  }

  static boolean isExpandedState(@BottomSheetBehavior.State int state)
  {
    return state == BottomSheetBehavior.STATE_EXPANDED;
  }

  public static boolean isHiddenState(@BottomSheetBehavior.State int state)
  {
    return state == BottomSheetBehavior.STATE_HIDDEN;
  }

  @NonNull
  static String toString(@BottomSheetBehavior.State int state)
  {
    return switch (state)
    {
      case BottomSheetBehavior.STATE_EXPANDED -> "EXPANDED";
      case BottomSheetBehavior.STATE_COLLAPSED -> "COLLAPSED";
      case BottomSheetBehavior.STATE_HALF_EXPANDED -> "HALF_EXPANDED";
      case BottomSheetBehavior.STATE_DRAGGING -> "DRAGGING";
      case BottomSheetBehavior.STATE_SETTLING -> "SETTLING";
      case BottomSheetBehavior.STATE_HIDDEN -> "HIDDEN";
      default -> throw new AssertionError("Unsupported state detected: " + state);
    };
  }

  public static void copyToClipboard(Context context, View frame, String text)
  {
    copyToClipboard(context, frame.getRootView().findViewById(R.id.pp_buttons_layout), frame, text);
  }

  public static void copyToClipboard(Context context, View snackbarContainer, View frame, String text)
  {
    Utils.copyTextToClipboard(context, text);

    KeyguardManager keyguardManager = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
    // Starting from API 33, the automatic system control that shows copied text is displayed.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || keyguardManager.isDeviceLocked())
    {
      Utils.showSnackbarAbove(snackbarContainer, frame, context.getString(R.string.copied_to_clipboard, text));
    }
  }

  public static void showCopyPopup(Context context, View popupAnchor, List<String> items)
  {
    final PopupMenu popup = new PopupMenu(context, popupAnchor);
    final Menu menu = popup.getMenu();
    final String copyText = context.getResources().getString(android.R.string.copy);
    // A menu item can be clicked after PlacePageButtons is removed from the Views hierarchy so
    // let's find a container for the snackbar outside of PlacePageButtons and in advance.
    final View snackbarTarget = (View) popupAnchor.getRootView().findViewById(R.id.pp_buttons_layout).getParent();

    for (int i = 0; i < items.size(); i++)
      menu.add(Menu.NONE, i, i, String.format("%s %s", copyText, items.get(i)));

    popup.setOnMenuItemClickListener(item -> {
      final String text = items.get(item.getItemId());
      copyToClipboard(context, snackbarTarget, popupAnchor, text);
      return true;
    });
    popup.show();
  }
}
