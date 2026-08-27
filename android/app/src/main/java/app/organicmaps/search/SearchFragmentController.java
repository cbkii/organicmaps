package app.organicmaps.search;

import android.annotation.SuppressLint;
import android.graphics.Outline;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import app.organicmaps.BuildConfig;
import app.organicmaps.MwmActivity;
import app.organicmaps.R;
import app.organicmaps.maplayer.MapButtonsViewModel;
import app.organicmaps.sdk.Framework;
import app.organicmaps.sdk.SearchMarkerHitTest;
import app.organicmaps.sdk.bookmarks.data.MapObject;
import app.organicmaps.sdk.routing.RoutingController;
import app.organicmaps.util.InputUtils;
import app.organicmaps.util.ThemeUtils;
import app.organicmaps.widget.placepage.PlacePageUtils;
import app.organicmaps.widget.placepage.PlacePageViewModel;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.color.MaterialColors;

public class SearchFragmentController extends Fragment implements SearchFragment.SearchFragmentListener
{
  // Search-result marker selection is produced asynchronously by the native render thread. Keep the
  // one-shot Quick selection context briefly after an outside tap closes the list so that the marker
  // from that same tap can still be recognised, while avoiding a persistent special map mode.
  private static final long QUICK_MARKER_SELECTION_WINDOW_MS = 1000L;

  private BottomSheetBehavior<FrameLayout> mBottomSheetBehavior;
  private boolean mInCarQuickDestinationsSearch;
  private boolean mQuickOutsideTapPending;
  private boolean mQuickPreviousButtonsHidden;
  private final Runnable mFinishQuickOutsideTap = this::endInCarQuickDestinationsSearch;
  private final Observer<MapObject> mPlacePageMapObjectObserver = new Observer<>() {
    @Override
    public void onChanged(MapObject mapObject)
    {
      if (mapObject != null && BuildConfig.IS_IN_CAR && mInCarQuickDestinationsSearch)
      {
        // Quick map taps are pre-filtered against the currently rendered SEARCH mark group. Keep
        // this second guard for lifecycle/race safety so a stale or non-search selection can never
        // become the Quick destination.
        final boolean routeToSearchResult = mapObject.isSearch();
        endInCarQuickDestinationsSearch();
        if (routeToSearchResult && requireActivity() instanceof MwmActivity activity)
        {
          activity.startLocationToPoint(mapObject);
          return;
        }

        mPlacePageViewModel.setMapObject(null);
        Framework.nativeDeactivatePopup();
        mViewModel.setSearchEnabled(false, null);
        return;
      }

      if (mapObject != null)
      {
        if (mBottomSheetBehavior.getState() != BottomSheetBehavior.STATE_HIDDEN)
        {
          mViewModel.setHiddenByPlacePage(true);
          mViewModel.setSearchPageLastState(mBottomSheetBehavior.getState());
          hideSearchSheet();
        }
      }
      else
      {
        Boolean searchEnabled = mViewModel.getSearchEnabled().getValue();
        Integer lastState = mViewModel.getSearchPageLastState().getValue();
        if (searchEnabled != null && searchEnabled && lastState != null && lastState != BottomSheetBehavior.STATE_HIDDEN
            && mViewModel.isHiddenByPlacePage())
        {
          showSearchSheet(lastState);
          mViewModel.setHiddenByPlacePage(false);
        }
      }
    }
  };
  private FrameLayout mSearchPageContainer;
  private int mDistanceToTop;
  private int mViewportMinHeight;
  private SearchPageViewModel mViewModel;
  private final Observer<Boolean> mSearchPageEnabledObserver = new Observer<>() {
    @Override
    public void onChanged(Boolean enabled)
    {
      if (enabled == null)
        return;
      if (enabled)
      {
        if (mPlacePageViewModel.getMapObject().getValue() != null && !mViewModel.isHiddenByPlacePage())
          mPlacePageViewModel.setMapObject(null);
        if (mViewModel.isHiddenByPlacePage())
          return;
        Integer lastState = mViewModel.getSearchPageLastState().getValue();
        if (lastState != null && lastState != BottomSheetBehavior.STATE_HIDDEN)
          showSearchSheet(lastState);
        else if (mBottomSheetBehavior.getState() == BottomSheetBehavior.STATE_HIDDEN)
          showSearchSheet(BottomSheetBehavior.STATE_EXPANDED);
      }
      else
      {
        hideSearchSheet();
        if (mInCarQuickDestinationsSearch && !mQuickOutsideTapPending)
          endInCarQuickDestinationsSearch();
      }
    }
  };
  private final Observer<Boolean> mInCarMapModeObserver = mapMode ->
  {
    if (!BuildConfig.IS_IN_CAR || mBottomSheetBehavior == null)
      return;
    final boolean showMap = Boolean.TRUE.equals(mapMode);
    mBottomSheetBehavior.setDraggable(!showMap);
    if (showMap)
    {
      InputUtils.hideKeyboard(mSearchPageContainer);
      showSearchSheet(BottomSheetBehavior.STATE_COLLAPSED);
    }
    else if (Boolean.TRUE.equals(mViewModel.getSearchEnabled().getValue()))
      showSearchSheet(BottomSheetBehavior.STATE_HALF_EXPANDED);
  };
  private PlacePageViewModel mPlacePageViewModel;
  private MapButtonsViewModel mMapButtonsViewModel;
  private ViewGroup mCoordinator;
  private WindowInsetsCompat mCurrentWindowInsets;
  private int mTopHeaderHeight = 0;
  private View mMapView;
  private final BottomSheetBehavior.BottomSheetCallback mDefaultBottomSheetCallback =
      new BottomSheetBehavior.BottomSheetCallback() {
        @Override
        public void onStateChanged(@NonNull View bottomSheet, int newState)
        {
          updateMapTouchListener(newState);
          if (PlacePageUtils.isSettlingState(newState))
            return;
          if (PlacePageUtils.isDraggingState(newState))
          {
            InputUtils.hideKeyboard(bottomSheet);
            return;
          }

          if (!RoutingController.get().isNavigating() && !RoutingController.get().isPlanning())
            PlacePageUtils.updateMapViewport(mCoordinator, mDistanceToTop, mViewportMinHeight);

          if (PlacePageUtils.isHiddenState(newState))
          {
            if (!mViewModel.isHiddenByPlacePage() && mViewModel.getSearchEnabled().getValue() != null
                && mViewModel.getSearchEnabled().getValue())
              mViewModel.setSearchEnabled(false, null);
            if (mInCarQuickDestinationsSearch && !mQuickOutsideTapPending)
              endInCarQuickDestinationsSearch();
          }
          mViewModel.setSearchPageLastState(newState);
        }

        @Override
        public void onSlide(@NonNull View bottomSheet, float slideOffset)
        {
          mDistanceToTop = bottomSheet.getTop();
          mViewModel.setSearchPageDistanceToTop(mDistanceToTop);
        }
      };
  private float mInitialX = 0f;
  private float mInitialY = 0f;
  private int mTouchSlop = 0;
  private int mQuickMarkerTouchRadiusPx = 0;
  private boolean mMapGestureDragged;

  private int mMinCollapsedPeekHeight = 0;
  private final Observer<Integer> mTopHeaderHeightObserver = height ->
  {
    mTopHeaderHeight = height != null ? height : 0;
    updateExpandedOffset();
  };
  private final Observer<Integer> mToolbarHeightObserver = new Observer<>() {
    @Override
    public void onChanged(Integer height)
    {
      if (height != null && height > 0)
        mBottomSheetBehavior.setPeekHeight(Math.max(height, mMinCollapsedPeekHeight) + navBarHeight());
    }
  };
  private final View.OnTouchListener mMapTouchListener = new View.OnTouchListener() {
    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouch(View v, MotionEvent event)
    {
      final int action = event.getActionMasked();
      if (action == MotionEvent.ACTION_DOWN)
      {
        InputUtils.hideKeyboard(v);
        mMapGestureDragged = false;
      }
      else if (action == MotionEvent.ACTION_POINTER_DOWN)
      {
        // A multi-touch gesture is a map gesture, not a Quick destination tap. Let MapView retain
        // the full pinch/zoom sequence and do not close the Quick search when the fingers lift.
        mMapGestureDragged = true;
      }

      final boolean drag = isDrag(event);
      if (drag)
        mMapGestureDragged = true;

      if (action == MotionEvent.ACTION_CANCEL)
      {
        mMapGestureDragged = false;
        return false;
      }

      if (action == MotionEvent.ACTION_UP)
      {
        final boolean completedMapGesture = mMapGestureDragged;
        mMapGestureDragged = false;
        if (completedMapGesture)
          return false;

        if (BuildConfig.IS_IN_CAR && mInCarQuickDestinationsSearch)
        {
          final boolean searchMarkerTap =
              SearchMarkerHitTest.nativeHasSearchMarkerAt(event.getX(), event.getY(), mQuickMarkerTouchRadiusPx);

          // Preserve the existing immediate list close for a completed tap. Keep the one-shot Quick
          // state alive briefly so any asynchronous native callback from an admitted marker remains
          // under the SEARCH-only observer guard.
          mQuickOutsideTapPending = true;
          mViewModel.setSearchEnabled(false, null);
          v.removeCallbacks(mFinishQuickOutsideTap);
          v.postDelayed(mFinishQuickOutsideTap, QUICK_MARKER_SELECTION_WINDOW_MS);

          if (!searchMarkerTap)
          {
            // ACTION_DOWN has already reached MapView. Finish its gesture with CANCEL instead of UP,
            // so blank-map taps and non-SEARCH marks cannot enter the native place-page selection
            // pipeline. Pan and pinch events are never cancelled by this path.
            cancelMapTap(v, event);
            return true;
          }
        }

        v.performClick();
        return false;
      }

      if (!drag)
        return false;
      if (mBottomSheetBehavior.getState() == BottomSheetBehavior.STATE_SETTLING)
        return false;
      if (mBottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED
          || mBottomSheetBehavior.getState() == BottomSheetBehavior.STATE_HALF_EXPANDED)
        showSearchSheet(BottomSheetBehavior.STATE_COLLAPSED);
      return false;
    }
  };

  @Nullable
  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                           @Nullable Bundle savedInstanceState)
  {
    return inflater.inflate(R.layout.search_fragment_container, container, false);
  }

  @SuppressLint("ClickableViewAccessibility")
  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState)
  {
    super.onViewCreated(view, savedInstanceState);
    mViewModel = new ViewModelProvider(requireActivity()).get(SearchPageViewModel.class);
    mPlacePageViewModel = new ViewModelProvider(requireActivity()).get(PlacePageViewModel.class);
    mMapButtonsViewModel = new ViewModelProvider(requireActivity()).get(MapButtonsViewModel.class);

    mCoordinator = requireActivity().findViewById(R.id.coordinator);
    mViewportMinHeight = requireActivity().getResources().getDimensionPixelSize(R.dimen.viewport_min_height);

    mSearchPageContainer = view.findViewById(R.id.search_page_container);
    mTouchSlop = ViewConfiguration.get(requireContext()).getScaledTouchSlop();
    mQuickMarkerTouchRadiusPx = getResources().getDimensionPixelSize(R.dimen.in_car_quick_marker_touch_radius);
    applyInCarSearchPanelWidth();

    mMinCollapsedPeekHeight = (int) getResources().getDimension(
        ThemeUtils.getResource(requireContext(), androidx.appcompat.R.attr.actionBarSize));

    float topRadius = getResources().getDimension(R.dimen.bottom_sheet_corner_radius);
    int surface = MaterialColors.getColor(mSearchPageContainer, com.google.android.material.R.attr.colorSurface);
    mSearchPageContainer.setBackgroundColor(surface);
    mSearchPageContainer.setOutlineProvider(new ViewOutlineProvider() {
      @Override
      public void getOutline(@NonNull View view, @NonNull Outline outline)
      {
        outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), topRadius);
      }
    });
    mSearchPageContainer.setClipToOutline(true);

    mBottomSheetBehavior = BottomSheetBehavior.from(mSearchPageContainer);
    mBottomSheetBehavior.setFitToContents(false);
    mBottomSheetBehavior.setHalfExpandedRatio(0.5f);
    mBottomSheetBehavior.setHideable(true);
    mBottomSheetBehavior.setDraggable(true);
    mBottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);

    mSearchPageContainer.post(this::updateExpandedOffset);
    mSearchPageContainer.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight,
                                                    oldBottom) -> mViewModel.setSearchPageWidth(right - left));

    final View searchBottomContainer = view.findViewById(R.id.search_bottom_container);

    ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
      mCurrentWindowInsets = insets;
      int navH = navBarHeight();
      Integer toolbarH = mViewModel.getToolbarHeight().getValue();
      if (toolbarH != null && toolbarH > 0)
        mBottomSheetBehavior.setPeekHeight(Math.max(toolbarH, mMinCollapsedPeekHeight) + navH);
      boolean imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime());
      applyInCarSearchPanelWidth();
      updateExpandedOffset();
      if (imeVisible && mViewModel.getSearchEnabled().getValue() != null && mViewModel.getSearchEnabled().getValue()
          && !mViewModel.isHiddenByPlacePage() && !mViewModel.isInCarMapMode())
        showSearchSheet(BottomSheetBehavior.STATE_EXPANDED);
      Insets horizontalInsets =
          insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
      searchBottomContainer.setPadding(horizontalInsets.left, searchBottomContainer.getPaddingTop(),
                                       horizontalInsets.right, searchBottomContainer.getPaddingBottom());
      ViewCompat.dispatchApplyWindowInsets(mSearchPageContainer, insets);
      return insets;
    });

    mMapView = requireActivity().findViewById(R.id.map);
    if (mMapView != null)
      updateMapTouchListener(mBottomSheetBehavior.getState());
  }

  public void beginInCarQuickDestinationsSearch()
  {
    if (!BuildConfig.IS_IN_CAR)
      return;
    if (mMapView != null)
      mMapView.removeCallbacks(mFinishQuickOutsideTap);
    if (!mInCarQuickDestinationsSearch && !mQuickOutsideTapPending)
      mQuickPreviousButtonsHidden = Boolean.TRUE.equals(mMapButtonsViewModel.getButtonsHidden().getValue());
    mQuickOutsideTapPending = false;
    mInCarQuickDestinationsSearch = true;
    mMapGestureDragged = false;
    // Search-result markers remain part of MapView, and the map itself stays fully pannable/pinch-zoomable.
    // Regular map controls are removed from the Quick interaction surface and restored when it ends.
    mMapButtonsViewModel.setButtonsHidden(true);
  }

  private void endInCarQuickDestinationsSearch()
  {
    final boolean wasQuickSearch = mInCarQuickDestinationsSearch || mQuickOutsideTapPending;
    if (mMapView != null)
      mMapView.removeCallbacks(mFinishQuickOutsideTap);
    mQuickOutsideTapPending = false;
    mInCarQuickDestinationsSearch = false;
    mMapGestureDragged = false;
    if (wasQuickSearch && mMapButtonsViewModel != null)
      mMapButtonsViewModel.setButtonsHidden(mQuickPreviousButtonsHidden);
  }

  private void applyInCarSearchPanelWidth()
  {
    if (!BuildConfig.IS_IN_CAR || mSearchPageContainer == null)
      return;
    int available = getResources().getDisplayMetrics().widthPixels;
    if (mCurrentWindowInsets != null)
    {
      final Insets bars = mCurrentWindowInsets.getInsets(WindowInsetsCompat.Type.systemBars()
                                                         | WindowInsetsCompat.Type.displayCutout());
      available -= bars.left + bars.right;
    }
    final float fraction = getResources().getFraction(R.fraction.in_car_search_panel_width_fraction, 1, 1);
    final int min = getResources().getDimensionPixelSize(R.dimen.in_car_search_panel_min_width);
    final int max = getResources().getDimensionPixelSize(R.dimen.in_car_search_panel_max_width);
    final int target = InCarSearchPresentationPolicy.panelWidthPx(available, min, max, fraction);
    final ViewGroup.LayoutParams raw = mSearchPageContainer.getLayoutParams();
    raw.width = target;
    if (raw instanceof androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams params)
      params.gravity = Gravity.BOTTOM | Gravity.START;
    mSearchPageContainer.setLayoutParams(raw);
  }

  @SuppressLint("ClickableViewAccessibility")
  @Override
  public void onDestroyView()
  {
    endInCarQuickDestinationsSearch();
    super.onDestroyView();
    if (mMapView != null)
    {
      mMapView.setOnTouchListener(null);
      mMapView = null;
    }
  }

  @Override
  public void onStart()
  {
    super.onStart();
    mPlacePageViewModel.getMapObject().observe(getViewLifecycleOwner(), mPlacePageMapObjectObserver);
    mBottomSheetBehavior.addBottomSheetCallback(mDefaultBottomSheetCallback);
    mViewModel.getSearchEnabled().observe(getViewLifecycleOwner(), mSearchPageEnabledObserver);
    mViewModel.getInCarMapMode().observe(getViewLifecycleOwner(), mInCarMapModeObserver);
    mViewModel.getToolbarHeight().observe(getViewLifecycleOwner(), mToolbarHeightObserver);
    mMapButtonsViewModel.getTopHeaderHeight().observe(getViewLifecycleOwner(), mTopHeaderHeightObserver);
  }

  @Override
  public void onStop()
  {
    super.onStop();
    mBottomSheetBehavior.removeBottomSheetCallback(mDefaultBottomSheetCallback);
  }

  private int navBarHeight()
  {
    if (mCurrentWindowInsets == null)
      return 0;
    return mCurrentWindowInsets.isVisible(WindowInsetsCompat.Type.navigationBars())
      ? mCurrentWindowInsets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
      : 0;
  }

  private void updateExpandedOffset()
  {
    if (mBottomSheetBehavior == null || mSearchPageContainer == null)
      return;
    int topInset = 0;
    if (mCurrentWindowInsets != null)
    {
      int systemBarsTop = mCurrentWindowInsets.getInsets(WindowInsetsCompat.Type.systemBars()).top;
      int cutoutTop = mCurrentWindowInsets.getInsets(WindowInsetsCompat.Type.displayCutout()).top;
      topInset = Math.max(systemBarsTop, cutoutTop);
    }
    int expandedOffset = topInset + mTopHeaderHeight;
    mBottomSheetBehavior.setExpandedOffset(expandedOffset);
    mViewModel.setExpandedOffset(expandedOffset);
    mSearchPageContainer.requestLayout();
  }

  private void updateMapTouchListener(int state)
  {
    if (mMapView == null)
      return;
    if (state == BottomSheetBehavior.STATE_HIDDEN)
      mMapView.setOnTouchListener(null);
    else
      mMapView.setOnTouchListener(mMapTouchListener);
  }

  private void cancelMapTap(@NonNull View mapView, @NonNull MotionEvent source)
  {
    final MotionEvent cancel = MotionEvent.obtain(source);
    try
    {
      cancel.setAction(MotionEvent.ACTION_CANCEL);
      mapView.onTouchEvent(cancel);
    }
    finally
    {
      cancel.recycle();
    }
  }

  boolean isDrag(MotionEvent event)
  {
    return switch (event.getActionMasked())
    {
      case MotionEvent.ACTION_DOWN ->
      {
        mInitialX = event.getX();
        mInitialY = event.getY();
        yield false;
      }
      case MotionEvent.ACTION_MOVE ->
      {
        float dx = Math.abs(event.getX() - mInitialX);
        float dy = Math.abs(event.getY() - mInitialY);
        yield dx >= mTouchSlop || dy >= mTouchSlop;
      }
      default -> false;
    };
  }

  public boolean onBackPressed()
  {
    Fragment fragment = getChildFragmentManager().findFragmentById(R.id.search_fragment);
    if (fragment instanceof SearchFragment searchFragment && searchFragment.onBackPressed())
      return true;

    if (mBottomSheetBehavior.getState() != BottomSheetBehavior.STATE_HIDDEN)
    {
      endInCarQuickDestinationsSearch();
      hideSearchSheet();
      return true;
    }
    return false;
  }

  @Override
  public void onSearchClicked()
  {
    if (BuildConfig.IS_IN_CAR && mViewModel.isInCarMapMode())
      showSearchSheet(BottomSheetBehavior.STATE_COLLAPSED);
    else
      showSearchSheet(BottomSheetBehavior.STATE_HALF_EXPANDED);
  }

  @Override
  public void closeSearch()
  {
    endInCarQuickDestinationsSearch();
    hideSearchSheet();
  }

  private void hideSearchSheet()
  {
    InputUtils.hideKeyboard(mSearchPageContainer);
    mBottomSheetBehavior.setHideable(true);
    mBottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
  }

  private void showSearchSheet(@BottomSheetBehavior.State int state)
  {
    mBottomSheetBehavior.setState(state);
    mBottomSheetBehavior.setHideable(false);
  }
}
