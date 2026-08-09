package app.organicmaps.base;

import android.app.Application;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;
import androidx.fragment.app.DialogFragment;
import app.organicmaps.BuildConfig;
import app.organicmaps.R;
import app.organicmaps.util.InCarVisuals;

public class BaseMwmDialogFragment extends DialogFragment
{
  @StyleRes
  protected final int getFullscreenTheme()
  {
    return R.style.MwmTheme_DialogFragment_Fullscreen;
  }

  protected int getStyle()
  {
    return STYLE_NORMAL;
  }

  @StyleRes
  protected int getCustomTheme()
  {
    return 0;
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);

    int style = getStyle();
    int theme = getCustomTheme();
    if (style != STYLE_NORMAL || theme != 0)
      // noinspection WrongConstant
      setStyle(style, theme);
  }

  @Override
  public void onStart()
  {
    super.onStart();
    if (!BuildConfig.IS_IN_CAR)
      return;

    final Dialog dialog = getDialog();
    if (dialog != null)
      InCarVisuals.fitDialog(requireActivity(), dialog);
  }

  @NonNull
  protected Application getAppContextOrThrow()
  {
    Context context = requireContext();
    if (context == null)
      throw new IllegalStateException("Before call this method make sure that the context exists");
    return (Application) context.getApplicationContext();
  }
}
