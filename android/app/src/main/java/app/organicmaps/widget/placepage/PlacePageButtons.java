package app.organicmaps.widget.placepage;

import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import app.organicmaps.BuildConfig;
import app.organicmaps.R;
import app.organicmaps.util.Graphics;
import app.organicmaps.util.WindowInsetUtils.PaddingInsetsListener;
import app.organicmaps.util.bottomsheet.MenuBottomSheetFragment;
import java.util.ArrayList;
import java.util.List;

public final class PlacePageButtons extends Fragment implements Observer<List<PlacePageButtons.ButtonType>>
{
  public static final String PLACEPAGE_MORE_MENU_ID = "PLACEPAGE_MORE_MENU_BOTTOM_SHEET";
  private int mMaxButtons;

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
    // The actual system/cutout inset is added outside the InCar visual safe padding supplied by the flavour layout.
    ViewCompat.setOnApplyWindowInsetsListener(
        view, PaddingInsetsListener.onlyBottom(WindowInsetsCompat.Type.systemBars()
                                               | WindowInsetsCompat.Type.displayCutout()));
    mMaxButtons = getResources().getInteger(R.integer.pp_buttons_max);

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
    super.onStop();
    mViewModel.getCurrentButtons().removeObserver(this);
  }

  private @NonNull List<PlacePageButton> collectButtons(List<PlacePageButtons.ButtonType> items)
  {
    List<PlacePageButton> res = new ArrayList<>();
    int count = items.size();
    if (items.size() > mMaxButtons)
      count = mMaxButtons - 1;

    for (int i = 0; i < count; i++)
      res.add(PlacePageButtonFactory.createButton(items.get(i), requireContext()));

    if (items.size() > mMaxButtons)
      res.add(PlacePageButtonFactory.createButton(ButtonType.MORE, requireContext()));
    return res;
  }

  private void showMoreBottomSheet()
  {
    MenuBottomSheetFragment.newInstance(PLACEPAGE_MORE_MENU_ID)
        .show(getParentFragmentManager(), PLACEPAGE_MORE_MENU_ID);
  }

  private void createButtons(@Nullable List<ButtonType> buttons)
  {
    if (buttons == null)
      return;
    List<PlacePageButton> shownButtons = collectButtons(buttons);
    mButtonsContainer.removeAllViews();
    for (PlacePageButton button : shownButtons)
      mButtonsContainer.addView(createButton(button));
  }

  private View createButton(@NonNull final PlacePageButton current)
  {
    LayoutInflater inflater = LayoutInflater.from(requireContext());
    View parent = inflater.inflate(R.layout.place_page_button, mButtonsContainer, false);

    ImageView icon = parent.findViewById(R.id.icon);
    TextView title = parent.findViewById(R.id.title);

    if (BuildConfig.IS_IN_CAR)
      configureInCarButton(parent, icon, title, current);
    else
      configureStandardButton(icon, title, current);

    parent.setOnClickListener((view) -> {
      if (current.getType() == ButtonType.MORE)
        showMoreBottomSheet();
      else
        mItemListener.onPlacePageButtonClick(current.getType());
    });
    return parent;
  }

  private void configureStandardButton(@NonNull ImageView icon, @NonNull TextView title,
                                       @NonNull PlacePageButton current)
  {
    title.setText(current.getTitle());
    @AttrRes
    final int tint = current.getType() == ButtonType.BOOKMARK_DELETE ? R.attr.iconTintActive : R.attr.iconTint;
    icon.setImageDrawable(Graphics.tint(getContext(), current.getIcon(), tint));
  }

  private void configureInCarButton(@NonNull View parent, @NonNull ImageView icon, @NonNull TextView title,
                                    @NonNull PlacePageButton current)
  {
    parent.setMinimumHeight(getResources().getDimensionPixelSize(R.dimen.in_car_runtime_button_size));

    if (current.getType() == ButtonType.ROUTE_FROM || current.getType() == ButtonType.ROUTE_TO)
    {
      title.setText(current.getType() == ButtonType.ROUTE_FROM ? R.string.in_car_go_from : R.string.in_car_go_to);
      icon.setImageResource(current.getType() == ButtonType.ROUTE_FROM ? R.drawable.ic_in_car_go_from
                                                                       : R.drawable.ic_in_car_go_to);
      setButtonWidth(parent, R.dimen.in_car_place_page_route_width);
      setIconSize(icon, R.dimen.in_car_place_page_route_icon_size, R.dimen.in_car_place_page_route_text_gap);
      if (parent instanceof LinearLayout layout)
      {
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER);
      }
      title.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
      title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.0f);
      title.setTypeface(title.getTypeface(), Typeface.BOLD);
      title.setMaxLines(2);
      title.setAllCaps(false);
      return;
    }

    title.setText(current.getTitle());
    @AttrRes
    final int tint = current.getType() == ButtonType.BOOKMARK_DELETE ? R.attr.iconTintActive : R.attr.iconTint;
    icon.setImageDrawable(Graphics.tint(getContext(), current.getIcon(), tint));

    if (current.getType() == ButtonType.BOOKMARK_SAVE)
    {
      setButtonWidth(parent, R.dimen.in_car_place_page_save_width);
      setIconSize(icon, R.dimen.in_car_place_page_save_icon_size, 0);
      title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.0f);
    }
    else
    {
      setButtonWidth(parent, R.dimen.in_car_place_page_other_width);
      setIconSize(icon, R.dimen.in_car_place_page_other_icon_size, 0);
    }
  }

  private void setButtonWidth(@NonNull View parent, int widthRes)
  {
    final ViewGroup.LayoutParams params = parent.getLayoutParams();
    params.width = getResources().getDimensionPixelSize(widthRes);
    params.height = ViewGroup.LayoutParams.MATCH_PARENT;
    parent.setLayoutParams(params);
  }

  private void setIconSize(@NonNull ImageView icon, int sizeRes, int endMarginRes)
  {
    final ViewGroup.LayoutParams raw = icon.getLayoutParams();
    final int size = getResources().getDimensionPixelSize(sizeRes);
    raw.width = size;
    raw.height = size;
    if (raw instanceof ViewGroup.MarginLayoutParams margins)
      margins.setMarginEnd(endMarginRes == 0 ? 0 : getResources().getDimensionPixelSize(endMarginRes));
    icon.setLayoutParams(raw);
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
