package app.organicmaps.settings;

import android.content.DialogInterface;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.ListPreference;
import androidx.preference.PreferenceDialogFragmentCompat;
import app.organicmaps.incar.InCarChoiceAdapter;
import app.organicmaps.incar.InCarDialogSizing;
import java.util.ArrayList;
import java.util.List;

/** The AndroidX preference dialog lifecycle with InCar choice rows, shared at every settings depth. */
public final class InCarListPreferenceDialogFragment extends PreferenceDialogFragmentCompat
{
  private CharSequence[] mEntries;
  private CharSequence[] mValues;
  private int mSelected;

  @NonNull
  public static InCarListPreferenceDialogFragment newInstance(@NonNull String key)
  {
    final InCarListPreferenceDialogFragment fragment = new InCarListPreferenceDialogFragment();
    final Bundle args = new Bundle();
    args.putString("key", key);
    fragment.setArguments(args);
    return fragment;
  }

  @Override
  public void onCreate(@Nullable Bundle state)
  {
    super.onCreate(state);
    final ListPreference preference = (ListPreference) getPreference();
    mEntries = state == null ? preference.getEntries() : state.getCharSequenceArray("entries");
    mValues = state == null ? preference.getEntryValues() : state.getCharSequenceArray("values");
    mSelected = state == null ? preference.findIndexOfValue(preference.getValue()) : state.getInt("selected", -1);
    if (mEntries == null || mValues == null || mEntries.length != mValues.length)
      throw new IllegalStateException("ListPreference requires matching entries and values");
  }

  @Override
  public void onSaveInstanceState(@NonNull Bundle state)
  {
    super.onSaveInstanceState(state);
    state.putCharSequenceArray("entries", mEntries);
    state.putCharSequenceArray("values", mValues);
    state.putInt("selected", mSelected);
  }

  @Override
  protected void onPrepareDialogBuilder(@NonNull AlertDialog.Builder builder)
  {
    super.onPrepareDialogBuilder(builder);
    final List<String> labels = new ArrayList<>(mEntries.length);
    for (CharSequence entry : mEntries)
      labels.add(entry.toString());
    builder.setSingleChoiceItems(InCarChoiceAdapter.singleChoice(builder.getContext(), labels), mSelected,
                                 (dialog, which) -> {
                                   mSelected = which;
                                   onClick(dialog, DialogInterface.BUTTON_POSITIVE);
                                   dialog.dismiss();
                                 });
    builder.setPositiveButton(null, null);
  }

  @Override
  public void onStart()
  {
    super.onStart();
    InCarDialogSizing.applyCompactWidth(requireActivity(), requireDialog());
  }

  @Override
  public void onDialogClosed(boolean positiveResult)
  {
    if (!positiveResult || mSelected < 0 || mSelected >= mValues.length)
      return;
    final ListPreference preference = (ListPreference) getPreference();
    final String value = mValues[mSelected].toString();
    if (preference.callChangeListener(value))
      preference.setValue(value);
  }
}
