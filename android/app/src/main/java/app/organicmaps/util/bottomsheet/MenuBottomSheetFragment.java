package app.organicmaps.util.bottomsheet;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import app.organicmaps.BuildConfig;
import app.organicmaps.MwmActivity;
import app.organicmaps.R;
import app.organicmaps.maplayer.MapButtonsViewModel;
import app.organicmaps.sdk.Map;
import app.organicmaps.sdk.location.TrackRecorder;
import app.organicmaps.util.ThemeUtils;
import app.organicmaps.util.UiUtils;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import java.util.ArrayList;
import java.util.Objects;

public class MenuBottomSheetFragment extends BottomSheetDialogFragment
{
  private static final String MAIN_MENU_ID = "MAIN_MENU_BOTTOM_SHEET";
  private static final String ADVANCED_MENU_ID = "ADVANCED_MENU_BOTTOM_SHEET";

  @Nullable
  private ArrayList<MenuBottomSheetItem> mMenuBottomSheetItems;
  @Nullable
  private Fragment mHeaderFragment;
  @Nullable
  private MenuAdapter mMenuAdapter;
  private boolean mMenuActionClosing;

  public static MenuBottomSheetFragment newInstance(String id)
  {
    Bundle args = new Bundle();
    args.putString("id", id);
    MenuBottomSheetFragment f = new MenuBottomSheetFragment();
    f.setArguments(args);
    return f;
  }

  public static MenuBottomSheetFragment newInstance(String id, String title)
  {
    Bundle args = new Bundle();
    args.putString("id", id);
    args.putString("title", title);
    MenuBottomSheetFragment f = new MenuBottomSheetFragment();
    f.setArguments(args);
    return f;
  }

  @NonNull
  @Override
  public Dialog onCreateDialog(@Nullable Bundle savedInstanceState)
  {
    return new BottomSheetDialog(requireContext(), getTheme()) {
      @Override
      public void onAttachedToWindow()
      {
        super.onAttachedToWindow();
        Window window = Objects.requireNonNull(getWindow());
        WindowInsetsControllerCompat insetsController = WindowCompat.getInsetsController(window, window.getDecorView());
        // Fully-expanded sheet covers the nav-bar region with ?cardBackground, so nav-bar
        // icons must contrast with the card background — dark in light mode, light in dark.
        insetsController.setAppearanceLightNavigationBars(!ThemeUtils.isDarkTheme(requireContext()));
      }
    };
  }

  @Nullable
  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                           @Nullable Bundle savedInstanceState)
  {
    return inflater.inflate(R.layout.bottom_sheet, container);
  }

  @Override
  public void onStart()
  {
    super.onStart();
    BottomSheetBehavior<View> behavior = BottomSheetBehavior.from((View) requireView().getParent());
    // By default sheets in landscape start at their peek height.
    // We fix this by forcing the expanded state and disabling the collapsed one
    behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
    behavior.setSkipCollapsed(true);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState)
  {
    super.onViewCreated(view, savedInstanceState);
    mMenuActionClosing = false;
    attachToNearestContext();
    TextView titleView = view.findViewById(R.id.bottomSheetTitle);
    RecyclerView recyclerView = view.findViewById(R.id.bottomSheetMenuContainer);
    if (getArguments() != null)
    {
      String title = getArguments().getString("title");
      if (title != null && !title.isEmpty())
      {
        titleView.setVisibility(View.VISIBLE);
        titleView.setText(title);
      }
      else
        titleView.setVisibility(View.GONE);
    }
    else
      titleView.setVisibility(View.GONE);

    if (mMenuBottomSheetItems != null)
    {
      mMenuAdapter = new MenuAdapter(mMenuBottomSheetItems, () -> {
        mMenuActionClosing = true;
        dismiss();
      });
      recyclerView.setAdapter(mMenuAdapter);
      if (BuildConfig.IS_IN_CAR && requireActivity() instanceof MwmActivity)
        new ViewModelProvider(requireActivity())
            .get(MapButtonsViewModel.class)
            .getTrackRecorderState()
            .observe(getViewLifecycleOwner(), ignored -> refreshInCarTrackRecording());
      recyclerView.setLayoutManager(new LinearLayoutManager(requireActivity()));
    }
    if (mHeaderFragment != null)
      getChildFragmentManager().beginTransaction().add(R.id.bottom_sheet_menu_header, mHeaderFragment).commit();

    // If there is nothing to show, hide the sheet
    if (!UiUtils.isVisible(titleView) && mMenuBottomSheetItems == null && mHeaderFragment == null)
      dismiss();
  }

  @Override
  public void onResume()
  {
    super.onResume();
    refreshInCarTrackRecording();
  }

  @Override
  public void onDestroyView()
  {
    mMenuActionClosing = true;
    mMenuAdapter = null;
    super.onDestroyView();
  }

  private void refreshInCarTrackRecording()
  {
    if (!BuildConfig.IS_IN_CAR || mMenuActionClosing || mMenuAdapter == null || mMenuBottomSheetItems == null)
      return;

    final boolean recording = Map.isEngineCreated() && TrackRecorder.nativeIsTrackRecordingEnabled();
    for (int i = 0; i < mMenuBottomSheetItems.size(); ++i)
    {
      MenuBottomSheetItem item = mMenuBottomSheetItems.get(i);
      if (item.checkable && item.titleRes == R.string.track_recording_title && !item.isActionDispatched()
          && item.checked != recording)
      {
        mMenuBottomSheetItems.set(
            i, MenuBottomSheetItem.checkable(R.string.track_recording_title, R.drawable.ic_track_recording_off,
                                             recording, () -> setInCarTrackRecording(!recording)));
        mMenuAdapter.notifyItemChanged(i);
      }
    }
  }

  private void attachToNearestContext()
  {
    // Try to attach to the parent fragment if any
    // In other cases, attach to the activity
    MenuBottomSheetInterface bottomSheetInterface = null;
    MenuBottomSheetInterfaceWithHeader bottomSheetInterfaceWithHeader = null;

    Fragment parentFragment = getParentFragment();
    if (parentFragment instanceof MenuBottomSheetInterfaceWithHeader)
      bottomSheetInterfaceWithHeader = (MenuBottomSheetInterfaceWithHeader) parentFragment;
    else if (parentFragment instanceof MenuBottomSheetInterface)
      bottomSheetInterface = (MenuBottomSheetInterface) parentFragment;
    else
    {
      Activity parentActivity = requireActivity();
      if (parentActivity instanceof MenuBottomSheetInterfaceWithHeader)
        bottomSheetInterfaceWithHeader = (MenuBottomSheetInterfaceWithHeader) parentActivity;
      else if (parentActivity instanceof MenuBottomSheetInterface)
        bottomSheetInterface = (MenuBottomSheetInterface) parentActivity;
    }

    String id = null;
    if (getArguments() != null)
      id = getArguments().getString("id");

    if (id != null && !id.isEmpty())
    {
      if (bottomSheetInterface != null)
        mMenuBottomSheetItems = bottomSheetInterface.getMenuBottomSheetItems(id);
      else if (bottomSheetInterfaceWithHeader != null)
      {
        mMenuBottomSheetItems = bottomSheetInterfaceWithHeader.getMenuBottomSheetItems(id);
        mHeaderFragment = bottomSheetInterfaceWithHeader.getMenuBottomSheetFragment(id);
      }
      adaptInCarMenu(id);
    }
  }

  private void adaptInCarMenu(@NonNull String id)
  {
    if (!BuildConfig.IS_IN_CAR || mMenuBottomSheetItems == null)
      return;

    if (ADVANCED_MENU_ID.equals(id))
    {
      mMenuBottomSheetItems.removeIf(item
                                     -> item.iconRes == R.drawable.ic_track_recording_off
                                            || item.iconRes == R.drawable.ic_track_recording_on);
      if (mMenuBottomSheetItems.isEmpty())
        mMenuBottomSheetItems = null;
      return;
    }

    if (!MAIN_MENU_ID.equals(id))
      return;

    // The Activity still builds the normal/shared menu model. InCar owns only this presentation
    // adaptation: one top-level binary recording command, never a second map-facing control.
    mMenuBottomSheetItems.removeIf(
        item -> item.iconRes == R.drawable.ic_track_recording_off || item.iconRes == R.drawable.ic_track_recording_on);
    final boolean recording = Map.isEngineCreated() && TrackRecorder.nativeIsTrackRecordingEnabled();
    final MenuBottomSheetItem trackRecording =
        MenuBottomSheetItem.checkable(R.string.track_recording_title, R.drawable.ic_track_recording_off, recording,
                                      () -> setInCarTrackRecording(!recording));

    int insertAt = mMenuBottomSheetItems.size();
    for (int i = 0; i < mMenuBottomSheetItems.size(); ++i)
    {
      if (mMenuBottomSheetItems.get(i).titleRes == R.string.settings)
      {
        insertAt = i;
        break;
      }
    }
    mMenuBottomSheetItems.add(insertAt, trackRecording);

    for (MenuBottomSheetItem item : mMenuBottomSheetItems)
    {
      if (item.iconRes == 0)
        throw new IllegalStateException("Every top-level InCar menu row requires an icon");
    }
  }

  private void setInCarTrackRecording(boolean enabled)
  {
    if (requireActivity() instanceof MwmActivity activity)
      activity.onTrackRecordingSwitchChanged(enabled);
  }

  public interface MenuBottomSheetInterfaceWithHeader
  {
    @Nullable
    Fragment getMenuBottomSheetFragment(String id);
    @Nullable
    ArrayList<MenuBottomSheetItem> getMenuBottomSheetItems(String id);
  }

  public interface MenuBottomSheetInterface
  {
    @Nullable
    ArrayList<MenuBottomSheetItem> getMenuBottomSheetItems(String id);
  }
}
