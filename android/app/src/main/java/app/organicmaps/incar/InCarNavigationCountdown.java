package app.organicmaps.incar;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import app.organicmaps.MwmActivity;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

/** Activity-owned idle actions; all dispatch returns through the existing button callback. */
public final class InCarNavigationCountdown
{
  public static final class State extends ViewModel
  {
    final InCarNavigationCountdownPolicy policy = new InCarNavigationCountdownPolicy();
  }
  private final MwmActivity mActivity;
  private final Handler mHandler = new Handler(Looper.getMainLooper());
  private final InCarNavigationCountdownPolicy mPolicy;
  private Runnable mTick;
  private IntConsumer mProgress;

  public InCarNavigationCountdown(MwmActivity activity)
  {
    mActivity = activity;
    mPolicy = new ViewModelProvider(activity).get(State.class).policy;
    mPolicy.cancel();
  }

  public void cancel()
  {
    mPolicy.cancel();
    if (mTick != null)
      mHandler.removeCallbacks(mTick);
    mTick = null;
    if (mProgress != null)
      mProgress.accept(-1);
    mProgress = null;
  }

  public boolean hasPendingAction()
  {
    return mTick != null;
  }

  public void arm(String stage, long generation, View button, BooleanSupplier eligible, IntConsumer progress)
  {
    if (!mPolicy.arm(stage, generation, InCarSettingsStore.getAutomaticNavigationActionDelaySeconds(mActivity),
                     SystemClock.elapsedRealtime()))
      return;
    if (mTick != null)
      mHandler.removeCallbacks(mTick);
    if (mProgress != null)
      mProgress.accept(-1);
    mProgress = progress;
    final long presentationDeadline = SystemClock.elapsedRealtime() + 3000;
    final boolean[] presented = {false};
    final int[] taskBounds = {0, 0};
    final android.graphics.Rect visibleBounds = new android.graphics.Rect();
    mTick = () ->
    {
      boolean ready = mActivity.getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
                   && mActivity.hasWindowFocus() && button.isShown() && button.isEnabled()
                   && ((android.os.PowerManager) mActivity.getSystemService(android.content.Context.POWER_SERVICE))
                          .isInteractive()
                   && button.getGlobalVisibleRect(visibleBounds) && visibleBounds.width() == button.getWidth()
                   && visibleBounds.height() == button.getHeight()
                   && !mActivity.getSupportFragmentManager().isStateSaved() && eligible.getAsBoolean();
      long now = SystemClock.elapsedRealtime();
      if (!presented[0])
      {
        // Reserve the generation once, but start elapsed time only after the selection gesture
        // and sheet presentation complete. Waiting is bounded and never survives cancellation.
        if ((!ready || mActivity.isInCarTouchActive()) && now < presentationDeadline)
        {
          mHandler.postDelayed(mTick, 100);
          return;
        }
        if (!ready || mActivity.isInCarTouchActive())
        {
          cancel();
          return;
        }
        presented[0] = true;
        taskBounds[0] = button.getRootView().getWidth();
        taskBounds[1] = button.getRootView().getHeight();
        mPolicy.beginCountdown(InCarSettingsStore.getAutomaticNavigationActionDelaySeconds(mActivity), now);
      }
      else if (!ready || button.getRootView().getWidth() != taskBounds[0]
               || button.getRootView().getHeight() != taskBounds[1])
      {
        cancel();
        return;
      }
      progress.accept(mPolicy.remainingSeconds(now));
      if (mPolicy.dispatch(now, true))
      {
        cancel();
        button.performClick();
      }
      else
        mHandler.postDelayed(mTick, 200);
    };
    // Selection and sheet placement finish before eligibility is examined.
    mHandler.post(mTick);
  }
}
