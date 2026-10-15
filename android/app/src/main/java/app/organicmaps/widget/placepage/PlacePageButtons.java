package app.organicmaps.widget.placepage;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import app.organicmaps.R;
import app.organicmaps.BuildConfig;
import app.organicmaps.MwmActivity;
import app.organicmaps.util.Graphics;
import app.organicmaps.util.WindowInsetUtils.PaddingInsetsListener;
import app.organicmaps.util.bottomsheet.MenuBottomSheetFragment;
import java.util.ArrayList;
import java.util.List;

public final class PlacePageButtons extends Fragment implements Observer<List<PlacePageButtons.ButtonType>>
{
  public static final String PLACEPAGE_MORE_MENU_ID = "PLACEPAGE_MORE_MENU_BOTTOM_SHEET";
  private int mMaxButtons;
  private int mAvailableWidth;
  private int mActionWidth;
  private int mActionGap;
  private View.OnLayoutChangeListener mWidthListener;
  private List<ButtonType> mRenderedButtons;
  private long mRenderedSelectionGeneration = -1;

  private PlacePageButtonClickListener mItemListener;
  private ViewGroup mButtonsContainer;
  private PlacePageViewModel mViewModel;

  @Nullable
  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                           @Nullable Bundle savedInstanceState)
  {
    mViewModel = new ViewModelProvider(requireActivity()).get(PlacePageViewModel.class);
    return inflater.inflate(R.layout.pp_buttons_fragment, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState)
  {
    super.onViewCreated(view, savedInstanceState);
    mButtonsContainer = view.findViewById(R.id.container);
    // Only bottom padding is required for the place-page buttons row. InCar adds its visual baseline
    // spacing inside the container, so this listener remains responsible only for the actual window inset.
    ViewCompat.setOnApplyWindowInsetsListener(
        view, PaddingInsetsListener.onlyBottom(WindowInsetsCompat.Type.systemBars()
                                               | WindowInsetsCompat.Type.displayCutout()));
    mMaxButtons = getResources().getInteger(R.integer.pp_buttons_max);

    if (BuildConfig.IS_IN_CAR)
    {
      mWidthListener = (root, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
        final int available = right - left - root.getPaddingLeft() - root.getPaddingRight();
        if (available <= 0 || available == mAvailableWidth) return;
        final boolean resize = mAvailableWidth > 0;
        mAvailableWidth = available;
        final int preferred = getResources().getDimensionPixelSize(R.dimen.in_car_place_page_action_width);
        final int preferredGap = getResources().getDimensionPixelSize(R.dimen.in_car_place_page_action_gap_half);
        mMaxButtons = PlacePageActionProjection.slotsForWidth(available, preferred + 2 * preferredGap,
                                                              getResources().getInteger(R.integer.pp_buttons_max));
        mViewModel.setActionSlots(mMaxButtons);
        final int emergency = getResources().getDimensionPixelSize(R.dimen.in_car_touch_target_extra_compact);
        mActionGap = Math.min(preferredGap, Math.max(0, (available / mMaxButtons - emergency) / 2));
        mActionWidth = PlacePageActionProjection.cellWidth(available, mMaxButtons, preferred, mActionGap);
        if (resize) ((MwmActivity) requireActivity()).cancelInCarNavigationCountdown();
        mRenderedButtons = null;
        createButtons(mViewModel.getCurrentButtons().getValue(), !resize);
      };
      view.addOnLayoutChangeListener(mWidthListener);
    }

    Fragment parentFragment = getParentFragment();
    mItemListener = (PlacePageButtonClickListener) parentFragment;

    createButtons(mViewModel.getCurrentButtons().getValue());
  }

  @Override
  public void onStart()
  {
    super.onStart();
    mViewModel.getCurrentButtons().observe(requireActivity(), this);
  }

  @Override
  public void onStop()
  {
    if (BuildConfig.IS_IN_CAR) ((MwmActivity) requireActivity()).cancelInCarNavigationCountdown();
    super.onStop();
    mViewModel.getCurrentButtons().removeObserver(this);
  }

  @Override
  public void onDestroyView()
  {
    if (BuildConfig.IS_IN_CAR && getActivity() instanceof MwmActivity activity)
      activity.cancelInCarNavigationCountdown();
    if (getView() != null && mWidthListener != null)
      getView().removeOnLayoutChangeListener(mWidthListener);
    mWidthListener = null;
    mAvailableWidth = 0;
    mRenderedButtons = null;
    super.onDestroyView();
  }

  private @NonNull List<PlacePageButton> collectButtons(List<PlacePageButtons.ButtonType> items)
  {
    List<PlacePageButton> res = new ArrayList<>();
    for (ButtonType type : PlacePageActionProjection.project(items, mMaxButtons, BuildConfig.IS_IN_CAR).direct())
      res.add(PlacePageButtonFactory.createButton(type, requireContext()));
    return res;
  }

  private void showMoreBottomSheet()
  {
    MenuBottomSheetFragment.newInstance(PLACEPAGE_MORE_MENU_ID)
        .show(getParentFragmentManager(), PLACEPAGE_MORE_MENU_ID);
  }

  private void createButtons(@Nullable List<ButtonType> buttons)
  {
    createButtons(buttons, true);
  }

  private void createButtons(@Nullable List<ButtonType> buttons, boolean armCountdown)
  {
    if (buttons == null)
      return;
    final long selectionGeneration = mViewModel.getSelectionGeneration();
    if (buttons.equals(mRenderedButtons) && selectionGeneration == mRenderedSelectionGeneration
        && mButtonsContainer.getChildCount() > 0)
      return;
    mRenderedButtons = new ArrayList<>(buttons);
    mRenderedSelectionGeneration = selectionGeneration;
    List<PlacePageButton> shownButtons = collectButtons(buttons);
    mButtonsContainer.removeAllViews();
    for (PlacePageButton button : shownButtons)
    {
      View action = createButton(button);
      if (BuildConfig.IS_IN_CAR && mAvailableWidth > 0)
      {
        final android.widget.LinearLayout.LayoutParams params =
            (android.widget.LinearLayout.LayoutParams) action.getLayoutParams();
        params.width = mActionWidth;
        params.setMarginStart(mActionGap);
        params.setMarginEnd(mActionGap);
        action.setMinimumWidth(mActionWidth);
        action.setLayoutParams(params);
      }
      mButtonsContainer.addView(action);
      if (BuildConfig.IS_IN_CAR && armCountdown && mAvailableWidth > 0 && button.getType() == ButtonType.ROUTE_TO)
      {
        final long generation = mViewModel.getSelectionGeneration();
        final app.organicmaps.sdk.bookmarks.data.MapObject destination = mViewModel.getMapObject().getValue();
        final MwmActivity owner = (MwmActivity) requireActivity();
        final long interactionGeneration = owner.getInCarInteractionGeneration();
        action.post(() -> {
          if (!isAdded() || getView() == null
              || owner.getInCarInteractionGeneration() != interactionGeneration) return;
          TextView title = action.findViewById(R.id.title);
          ((MwmActivity) requireActivity()).getInCarNavigationCountdown().arm(
              "GO_TO", generation, action,
              () -> app.organicmaps.sdk.bookmarks.data.MapObject.same(mViewModel.getMapObject().getValue(), destination)
                  && generation == mViewModel.getSelectionGeneration()
                  && getView() != null && isResumed()
                  && mViewModel.getCurrentButtons().getValue() != null
                  && mViewModel.getCurrentButtons().getValue().contains(ButtonType.ROUTE_TO)
                  && !mViewModel.isAlertDialogShowing
                  && !app.organicmaps.sdk.routing.RoutingController.get().isNavigating()
                  && !app.organicmaps.sdk.routing.RoutingController.get().isWaitingPoiPick(),
              seconds -> title.setText(seconds < 0 ? getString(button.getTitle())
                                                  : countdownLabel(getString(button.getTitle()), seconds)));
        });
      }
    }
  }

  private View createButton(@NonNull final PlacePageButton current)
  {
    LayoutInflater inflater = LayoutInflater.from(requireContext());
    View parent = inflater.inflate(R.layout.place_page_button, mButtonsContainer, false);

    ImageView icon = parent.findViewById(R.id.icon);
    TextView title = parent.findViewById(R.id.title);

    title.setText(current.getTitle());
    @AttrRes
    final int tint = current.getType() == ButtonType.BOOKMARK_DELETE ? R.attr.iconTintActive : R.attr.iconTint;
    icon.setImageDrawable(Graphics.tint(getContext(), current.getIcon(), tint));

    if (BuildConfig.IS_IN_CAR && current.getType() == ButtonType.ROUTE_TO)
    {
      parent.setBackgroundResource(R.drawable.in_car_go_to_primary);
      title.setTextColor(android.graphics.Color.WHITE);
      icon.setColorFilter(android.graphics.Color.WHITE);
      title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
      title.setMaxLines(2);
      title.getLayoutParams().width = ViewGroup.LayoutParams.MATCH_PARENT;
      androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
          title, 16, 22, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
    }
    parent.setOnClickListener((view) -> {
      if (BuildConfig.IS_IN_CAR) ((MwmActivity) requireActivity()).cancelInCarNavigationCountdown();
      if (current.getType() == ButtonType.MORE)
        showMoreBottomSheet();
      else
        mItemListener.onPlacePageButtonClick(current.getType());
    });
    return parent;
  }

  private static CharSequence countdownLabel(String label, int seconds)
  {
    android.text.SpannableString text = new android.text.SpannableString(label + "\n" + seconds + "s");
    text.setSpan(new android.text.style.RelativeSizeSpan(0.65f), label.length() + 1, text.length(),
                 android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    return text;
  }

  @Override
  public void onChanged(List<ButtonType> buttonTypes)
  {
    createButtons(buttonTypes);
  }

  public enum ButtonType
  {
    BACK,
    BOOKMARK_SAVE,
    BOOKMARK_DELETE,
    TRACK_DELETE,
    ROUTE_FROM,
    ROUTE_TO,
    ROUTE_REPLACE,
    ROUTE_ADD,
    ROUTE_REMOVE,
    ROUTE_AVOID_TOLL,
    ROUTE_AVOID_FERRY,
    ROUTE_AVOID_UNPAVED,
    TRACK_RECORDING_SAVE,
    TRACK_RECORDING_DELETE,
    MORE,
    /** InCar only: build a Pedestrian route to this place (activates the walking last-mile session). */
    WALK_TO,
    /** InCar only: leave the walking last-mile session and rebuild this destination as Vehicle. */
    RETURN_TO_DRIVING
  }

  public interface PlacePageButtonClickListener
  {
    void onPlacePageButtonClick(ButtonType item);
  }
}
