package app.organicmaps.util;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.AbsListView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ScrollView;
import androidx.annotation.DimenRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.VisibleForTesting;
import androidx.core.view.ViewCompat;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import app.organicmaps.BuildConfig;
import app.organicmaps.R;
import app.organicmaps.sdk.MapView;
import app.organicmaps.sdk.util.Config;
import app.organicmaps.sdk.util.log.Logger;
import app.organicmaps.widget.placepage.PlacePageController;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Owns InCar app-side window convergence and applies the fixed-display control dimensions to the currently mounted UI.
 * The system/launcher remains the authority for task bounds; this class only reconciles Organic Maps inside those bounds.
 */
public final class InCarVisuals
{
  private static final String TAG = InCarVisuals.class.getSimpleName();
  private static final int MAX_INVALID_BOUNDS_RETRIES = 3;
  private static final int MAX_CONVERGENCE_VERIFICATION_FRAMES = 3;

  @VisibleForTesting
  static final int COMPACT_WIDTH_DP = 720;
  @VisibleForTesting
  static final int COMPACT_HEIGHT_DP = 480;

  private static final Map<FragmentActivity, Observation> OBSERVATIONS = new WeakHashMap<>();
  private static final Map<Dialog, DialogFitState> DIALOG_FITS = new WeakHashMap<>();

  public enum TransitionReason {
    CREATE,
    RESUME,
    NEW_INTENT,
    ROOT_LAYOUT,
    WINDOW_FOCUS,
    MULTI_WINDOW,
    PIP_MODE,
    CONFIGURATION,
    FRAGMENT_VIEW,
    RETRY
  }

  @VisibleForTesting
  enum WindowProfile {
    FULL,
    COMPACT_WIDTH,
    COMPACT_HEIGHT,
    COMPACT_BOTH
  }

  private static final class WindowSnapshot
  {
    final long generation;
    final int contentWidthPx;
    final int contentHeightPx;
    final int widthDp;
    final int heightDp;
    @NonNull
    final WindowProfile profile;
    final int taskId;
    final int activityInstanceId;
    final boolean multiWindow;
    final boolean pictureInPicture;
    final int configWidthDp;
    final int configHeightDp;
    final int orientation;
    final int mapWidthPx;
    final int mapHeightPx;
    final int surfaceWidthPx;
    final int surfaceHeightPx;
    final int nativeWidthPx;
    final int nativeHeightPx;

    WindowSnapshot(long generation, int contentWidthPx, int contentHeightPx, int widthDp, int heightDp,
                   @NonNull WindowProfile profile, int taskId, int activityInstanceId, boolean multiWindow,
                   boolean pictureInPicture, int configWidthDp, int configHeightDp, int orientation, int mapWidthPx,
                   int mapHeightPx, int surfaceWidthPx, int surfaceHeightPx, int nativeWidthPx, int nativeHeightPx)
    {
      this.generation = generation;
      this.contentWidthPx = contentWidthPx;
      this.contentHeightPx = contentHeightPx;
      this.widthDp = widthDp;
      this.heightDp = heightDp;
      this.profile = profile;
      this.taskId = taskId;
      this.activityInstanceId = activityInstanceId;
      this.multiWindow = multiWindow;
      this.pictureInPicture = pictureInPicture;
      this.configWidthDp = configWidthDp;
      this.configHeightDp = configHeightDp;
      this.orientation = orientation;
      this.mapWidthPx = mapWidthPx;
      this.mapHeightPx = mapHeightPx;
      this.surfaceWidthPx = surfaceWidthPx;
      this.surfaceHeightPx = surfaceHeightPx;
      this.nativeWidthPx = nativeWidthPx;
      this.nativeHeightPx = nativeHeightPx;
    }
  }

  private static final class Observation
  {
    @Nullable
    WindowSnapshot snapshot;
    boolean optimisedVisuals;
    long requestGeneration;
    long snapshotGeneration;
    @Nullable
    View content;
    @Nullable
    View.OnLayoutChangeListener layoutListener;
    @Nullable
    FragmentManager.FragmentLifecycleCallbacks fragmentCallbacks;
    @Nullable
    Runnable pendingReconcile;
    @Nullable
    Runnable pendingRetry;
    @Nullable
    Runnable pendingVerification;
  }

  private static final class DialogFitState
  {
    int targetWidth = -1;
    int availableHeight = -1;
    @Nullable
    View decor;
    @Nullable
    ViewTreeObserver.OnPreDrawListener pendingPreDraw;
  }

  private InCarVisuals() {}

  @UiThread
  public static void applyAndObserve(@NonNull FragmentActivity activity)
  {
    reconcile(activity, TransitionReason.RESUME);
  }

  @UiThread
  public static void reconcile(@NonNull FragmentActivity activity, @NonNull TransitionReason reason)
  {
    if (!BuildConfig.IS_IN_CAR || !isActivityAlive(activity))
      return;

    final Observation observation = ensureObservation(activity);
    cancelPendingWork(observation);
    final long requestGeneration = ++observation.requestGeneration;
    reconcileNow(activity, observation, reason, requestGeneration, MAX_INVALID_BOUNDS_RETRIES);
  }

  @UiThread
  public static void release(@NonNull FragmentActivity activity)
  {
    final Observation observation = OBSERVATIONS.remove(activity);
    if (observation == null)
      return;

    cancelPendingWork(observation);
    if (observation.content != null && observation.layoutListener != null)
      observation.content.removeOnLayoutChangeListener(observation.layoutListener);
    if (observation.fragmentCallbacks != null)
      activity.getSupportFragmentManager().unregisterFragmentLifecycleCallbacks(observation.fragmentCallbacks);
    observation.content = null;
    observation.layoutListener = null;
    observation.fragmentCallbacks = null;
  }

  @NonNull
  private static Observation ensureObservation(@NonNull FragmentActivity activity)
  {
    Observation observation = OBSERVATIONS.get(activity);
    if (observation == null)
    {
      observation = new Observation();
      OBSERVATIONS.put(activity, observation);
    }

    if (observation.content == null)
      installObservation(activity, observation);
    return observation;
  }

  private static void installObservation(@NonNull FragmentActivity activity, @NonNull Observation observation)
  {
    final View content = activity.findViewById(android.R.id.content);
    if (content != null)
    {
      observation.content = content;
      final Observation state = observation;
      observation.layoutListener = (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
        if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop)
          scheduleReconcile(activity, state, TransitionReason.ROOT_LAYOUT);
      };
      content.addOnLayoutChangeListener(observation.layoutListener);
    }

    if (observation.fragmentCallbacks == null)
    {
      final Observation state = observation;
      observation.fragmentCallbacks = new FragmentManager.FragmentLifecycleCallbacks() {
        @Override
        public void onFragmentViewCreated(@NonNull FragmentManager fm, @NonNull Fragment fragment, @NonNull View view,
                                          @Nullable Bundle savedInstanceState)
        {
          applyToFragmentView(activity, state, view);
        }

        @Override
        public void onFragmentStarted(@NonNull FragmentManager fm, @NonNull Fragment fragment)
        {
          if (fragment instanceof DialogFragment dialogFragment)
          {
            final Dialog dialog = dialogFragment.getDialog();
            if (dialog != null)
              fitDialog(activity, dialog);
          }
        }

        @Override
        public void onFragmentStopped(@NonNull FragmentManager fm, @NonNull Fragment fragment)
        {
          if (fragment instanceof DialogFragment dialogFragment)
          {
            final Dialog dialog = dialogFragment.getDialog();
            if (dialog != null)
              releaseDialog(dialog);
          }
        }
      };
      activity.getSupportFragmentManager().registerFragmentLifecycleCallbacks(observation.fragmentCallbacks, true);
    }
  }

  private static void scheduleReconcile(@NonNull FragmentActivity activity, @NonNull Observation observation,
                                        @NonNull TransitionReason reason)
  {
    if (!BuildConfig.IS_IN_CAR || !isActivityAlive(activity))
      return;

    final View content = observation.content;
    if (content == null)
    {
      reconcile(activity, reason);
      return;
    }

    if (observation.pendingReconcile != null)
      content.removeCallbacks(observation.pendingReconcile);
    if (observation.pendingRetry != null)
      content.removeCallbacks(observation.pendingRetry);
    if (observation.pendingVerification != null)
      content.removeCallbacks(observation.pendingVerification);

    observation.pendingRetry = null;
    observation.pendingVerification = null;
    final long requestGeneration = ++observation.requestGeneration;
    final Runnable reconcileRunnable = () -> {
      observation.pendingReconcile = null;
      if (!isGenerationCurrent(requestGeneration, observation.requestGeneration) || !isActivityAlive(activity))
        return;
      reconcileNow(activity, observation, reason, requestGeneration, MAX_INVALID_BOUNDS_RETRIES);
    };
    observation.pendingReconcile = reconcileRunnable;
    content.postOnAnimation(reconcileRunnable);
  }

  private static void reconcileNow(@NonNull FragmentActivity activity, @NonNull Observation observation,
                                   @NonNull TransitionReason reason, long requestGeneration, int retriesRemaining)
  {
    if (!isGenerationCurrent(requestGeneration, observation.requestGeneration) || !isActivityAlive(activity))
      return;

    final View content = observation.content != null ? observation.content : activity.findViewById(android.R.id.content);
    if (content == null)
    {
      scheduleInvalidBoundsRetry(activity, observation, reason, requestGeneration, retriesRemaining, 0, 0);
      return;
    }
    observation.content = content;

    final int width = content.getWidth();
    final int height = content.getHeight();
    if (!hasValidBounds(width, height))
    {
      scheduleInvalidBoundsRetry(activity, observation, reason, requestGeneration, retriesRemaining, width, height);
      return;
    }

    final float density = activity.getResources().getDisplayMetrics().density;
    final int widthDp = Math.round(width / density);
    final int heightDp = Math.round(height / density);
    final WindowProfile profile = classifyWindow(widthDp, heightDp);
    if (profile == null)
    {
      scheduleInvalidBoundsRetry(activity, observation, reason, requestGeneration, retriesRemaining, width, height);
      return;
    }

    final WindowSnapshot previous = observation.snapshot;
    final boolean boundsChanged =
        previous == null || hasMaterialBoundsChange(previous.contentWidthPx, previous.contentHeightPx, width, height);
    final boolean optimisedVisuals = Config.isInCarOptimisedVisualsEnabled();
    final boolean controlsChanged = previous == null || profile != previous.profile
                                 || optimisedVisuals != observation.optimisedVisuals;

    final WindowSnapshot snapshot = createSnapshot(activity, ++observation.snapshotGeneration, width, height, widthDp,
                                                   heightDp, profile);
    observation.snapshot = snapshot;
    observation.optimisedVisuals = optimisedVisuals;

    final boolean explicitConvergenceTrigger = isExplicitConvergenceTrigger(reason);
    if (controlsChanged || explicitConvergenceTrigger)
      apply(activity, content, optimisedVisuals, profile);

    if (boundsChanged || controlsChanged || explicitConvergenceTrigger)
    {
      final View coordinator = activity.findViewById(R.id.coordinator);
      ViewCompat.requestApplyInsets(coordinator != null ? coordinator : content);
      content.requestLayout();
      fitVisibleDialogs(activity.getSupportFragmentManager(), activity);
      notifyPlacePageWindowChanged(activity.getSupportFragmentManager());
      scheduleConvergenceVerification(activity, observation, requestGeneration,
                                      MAX_CONVERGENCE_VERIFICATION_FRAMES);
    }

    if (boundsChanged || controlsChanged || explicitConvergenceTrigger)
      logSnapshot(activity, reason, snapshot);
  }

  private static void scheduleInvalidBoundsRetry(@NonNull FragmentActivity activity, @NonNull Observation observation,
                                                 @NonNull TransitionReason reason, long requestGeneration,
                                                 int retriesRemaining, int width, int height)
  {
    if (retriesRemaining <= 0)
    {
      Logger.w(TAG, "Window bounds remain unmeasured after bounded retry: reason=" + reason + " request="
                        + requestGeneration + " content=" + width + "x" + height + " previousProfile="
                        + (observation.snapshot == null ? "UNKNOWN" : observation.snapshot.profile));
      return;
    }

    final View content = observation.content;
    if (content == null)
      return;

    final Runnable retry = () -> {
      observation.pendingRetry = null;
      if (!isGenerationCurrent(requestGeneration, observation.requestGeneration) || !isActivityAlive(activity))
        return;
      reconcileNow(activity, observation, TransitionReason.RETRY, requestGeneration, retriesRemaining - 1);
    };
    observation.pendingRetry = retry;
    content.postOnAnimation(retry);
  }

  private static void scheduleConvergenceVerification(@NonNull FragmentActivity activity,
                                                      @NonNull Observation observation, long requestGeneration,
                                                      int framesRemaining)
  {
    final View content = observation.content;
    if (content == null || framesRemaining <= 0)
      return;

    final Runnable verification = () -> {
      observation.pendingVerification = null;
      if (!isGenerationCurrent(requestGeneration, observation.requestGeneration) || !isActivityAlive(activity))
        return;

      final WindowSnapshot expected = observation.snapshot;
      final View mapContainer = activity.findViewById(R.id.map_container);
      final MapView mapView = activity.findViewById(R.id.map);
      if (expected == null || mapView == null)
        return;

      final int expectedWidth = mapContainer != null && mapContainer.getWidth() > 0 ? mapContainer.getWidth()
                                                                                   : expected.contentWidthPx;
      final int expectedHeight = mapContainer != null && mapContainer.getHeight() > 0 ? mapContainer.getHeight()
                                                                                       : expected.contentHeightPx;
      final int mapWidth = mapView.getWidth();
      final int mapHeight = mapView.getHeight();
      final int surfaceWidth = mapView.getSurfaceFrameWidth();
      final int surfaceHeight = mapView.getSurfaceFrameHeight();
      final int nativeWidth = mapView.getLastAppliedSurfaceWidth();
      final int nativeHeight = mapView.getLastAppliedSurfaceHeight();

      final boolean mapMismatch =
          hasValidBounds(expectedWidth, expectedHeight) && (mapWidth != expectedWidth || mapHeight != expectedHeight);
      final boolean surfaceMismatch =
          hasValidBounds(surfaceWidth, surfaceHeight) && (surfaceWidth != mapWidth || surfaceHeight != mapHeight);
      final boolean nativeMismatch = hasValidBounds(surfaceWidth, surfaceHeight) && hasValidBounds(nativeWidth, nativeHeight)
                                  && (nativeWidth != surfaceWidth || nativeHeight != surfaceHeight);

      if (!mapMismatch && !surfaceMismatch && !nativeMismatch)
      {
        Logger.d(TAG, "Window convergence verified: gen=" + expected.generation + " map=" + mapWidth + "x" + mapHeight
                          + " surface=" + surfaceWidth + "x" + surfaceHeight + " native=" + nativeWidth + "x"
                          + nativeHeight);
        return;
      }

      if (mapMismatch)
        mapView.requestLayout();

      if (framesRemaining > 1)
      {
        scheduleConvergenceVerification(activity, observation, requestGeneration, framesRemaining - 1);
        return;
      }

      Logger.w(TAG, "Window convergence still pending: gen=" + expected.generation + " expected=" + expectedWidth + "x"
                        + expectedHeight + " map=" + mapWidth + "x" + mapHeight + " surface=" + surfaceWidth + "x"
                        + surfaceHeight + " native=" + nativeWidth + "x" + nativeHeight + " mapMismatch=" + mapMismatch
                        + " surfaceMismatch=" + surfaceMismatch + " nativeMismatch=" + nativeMismatch);
    };
    observation.pendingVerification = verification;
    content.postOnAnimation(verification);
  }

  private static void cancelPendingWork(@NonNull Observation observation)
  {
    final View content = observation.content;
    if (content != null)
    {
      if (observation.pendingReconcile != null)
        content.removeCallbacks(observation.pendingReconcile);
      if (observation.pendingRetry != null)
        content.removeCallbacks(observation.pendingRetry);
      if (observation.pendingVerification != null)
        content.removeCallbacks(observation.pendingVerification);
    }
    observation.pendingReconcile = null;
    observation.pendingRetry = null;
    observation.pendingVerification = null;
  }

  private static boolean isActivityAlive(@NonNull FragmentActivity activity)
  {
    return !activity.isFinishing() && !activity.isDestroyed();
  }

  private static boolean isExplicitConvergenceTrigger(@NonNull TransitionReason reason)
  {
    return switch (reason)
    {
      case CREATE, RESUME, NEW_INTENT, WINDOW_FOCUS, MULTI_WINDOW, PIP_MODE, CONFIGURATION -> true;
      case ROOT_LAYOUT, FRAGMENT_VIEW, RETRY -> false;
    };
  }

  @VisibleForTesting
  static boolean hasValidBounds(int width, int height)
  {
    return width > 0 && height > 0;
  }

  @VisibleForTesting
  static boolean hasMaterialBoundsChange(int oldWidth, int oldHeight, int newWidth, int newHeight)
  {
    return oldWidth != newWidth || oldHeight != newHeight;
  }

  @VisibleForTesting
  static boolean isGenerationCurrent(long scheduledGeneration, long currentGeneration)
  {
    return scheduledGeneration == currentGeneration;
  }

  @Nullable
  @VisibleForTesting
  static WindowProfile resolveWindowProfile(@Nullable WindowProfile previous, int widthDp, int heightDp)
  {
    final WindowProfile resolved = classifyWindow(widthDp, heightDp);
    return resolved != null ? resolved : previous;
  }

  @Nullable
  @VisibleForTesting
  static WindowProfile classifyWindow(int widthDp, int heightDp)
  {
    if (widthDp <= 0 || heightDp <= 0)
      return null;

    final boolean compactWidth = widthDp < COMPACT_WIDTH_DP;
    final boolean compactHeight = heightDp < COMPACT_HEIGHT_DP;
    if (compactWidth && compactHeight)
      return WindowProfile.COMPACT_BOTH;
    if (compactWidth)
      return WindowProfile.COMPACT_WIDTH;
    if (compactHeight)
      return WindowProfile.COMPACT_HEIGHT;
    return WindowProfile.FULL;
  }

  @NonNull
  private static WindowSnapshot createSnapshot(@NonNull FragmentActivity activity, long generation, int width, int height,
                                               int widthDp, int heightDp, @NonNull WindowProfile profile)
  {
    final Configuration config = activity.getResources().getConfiguration();
    final boolean supportsWindowModes = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N;
    final boolean multiWindow = supportsWindowModes && activity.isInMultiWindowMode();
    final boolean pictureInPicture = supportsWindowModes && activity.isInPictureInPictureMode();
    final MapView mapView = activity.findViewById(R.id.map);
    final int mapWidth = mapView == null ? 0 : mapView.getWidth();
    final int mapHeight = mapView == null ? 0 : mapView.getHeight();
    final int surfaceWidth = mapView == null ? 0 : mapView.getSurfaceFrameWidth();
    final int surfaceHeight = mapView == null ? 0 : mapView.getSurfaceFrameHeight();
    final int nativeWidth = mapView == null ? 0 : mapView.getLastAppliedSurfaceWidth();
    final int nativeHeight = mapView == null ? 0 : mapView.getLastAppliedSurfaceHeight();

    return new WindowSnapshot(generation, width, height, widthDp, heightDp, profile, activity.getTaskId(),
                              System.identityHashCode(activity), multiWindow, pictureInPicture, config.screenWidthDp,
                              config.screenHeightDp, config.orientation, mapWidth, mapHeight, surfaceWidth, surfaceHeight,
                              nativeWidth, nativeHeight);
  }

  private static void logSnapshot(@NonNull FragmentActivity activity, @NonNull TransitionReason reason,
                                  @NonNull WindowSnapshot snapshot)
  {
    Logger.i(TAG, "reason=" + reason + " gen=" + snapshot.generation + " task=" + snapshot.taskId + " activity="
                      + snapshot.activityInstanceId + " lifecycle=" + activity.getLifecycle().getCurrentState() + " multi="
                      + snapshot.multiWindow + " pip=" + snapshot.pictureInPicture + " config=" + snapshot.configWidthDp
                      + "x" + snapshot.configHeightDp + "/" + snapshot.orientation + " content=" + snapshot.contentWidthPx
                      + "x" + snapshot.contentHeightPx + " dp=" + snapshot.widthDp + "x" + snapshot.heightDp + " map="
                      + snapshot.mapWidthPx + "x" + snapshot.mapHeightPx + " surface=" + snapshot.surfaceWidthPx + "x"
                      + snapshot.surfaceHeightPx + " native=" + snapshot.nativeWidthPx + "x" + snapshot.nativeHeightPx
                      + " profile=" + snapshot.profile + " intent=" + describeIntent(activity.getIntent()));
  }

  @NonNull
  private static String describeIntent(@Nullable Intent intent)
  {
    if (intent == null)
      return "null";
    final Set<String> categories = intent.getCategories();
    return "{action=" + intent.getAction() + ",component=" + intent.getComponent() + ",categories="
         + (categories == null ? "[]" : categories) + ",flags=0x" + Integer.toHexString(intent.getFlags()) + "}";
  }

  private static void notifyPlacePageWindowChanged(@NonNull FragmentManager fragmentManager)
  {
    for (Fragment fragment : fragmentManager.getFragments())
    {
      if (fragment instanceof PlacePageController placePage && fragment.isAdded() && fragment.getView() != null)
        placePage.onHostWindowBoundsChanged();
      if (fragment.isAdded())
        notifyPlacePageWindowChanged(fragment.getChildFragmentManager());
    }
  }

  @UiThread
  public static void fitDialog(@NonNull FragmentActivity activity, @NonNull Dialog dialog)
  {
    final Window window = dialog.getWindow();
    if (window == null)
      return;

    final WindowManager.LayoutParams attributes = window.getAttributes();
    if (attributes.width == ViewGroup.LayoutParams.MATCH_PARENT
        && attributes.height == ViewGroup.LayoutParams.MATCH_PARENT)
    {
      releaseDialog(dialog);
      return;
    }

    final View content = activity.findViewById(android.R.id.content);
    if (content == null || content.getWidth() <= 0 || content.getHeight() <= 0)
      return;

    final int margin = dimen(activity, R.dimen.in_car_dialog_window_margin);
    final int availableWidth = content.getWidth() - 2 * margin;
    final int availableHeight = content.getHeight() - 2 * margin;
    if (availableWidth <= 0 || availableHeight <= 0)
      return;

    final int targetWidth = Math.min(availableWidth, dimen(activity, R.dimen.in_car_dialog_max_width));
    DialogFitState fitState = DIALOG_FITS.get(dialog);
    if (fitState == null)
    {
      fitState = new DialogFitState();
      DIALOG_FITS.put(dialog, fitState);
    }

    if (fitState.targetWidth == targetWidth && fitState.availableHeight == availableHeight)
      return;

    clearPendingDialogFit(fitState);
    fitState.targetWidth = targetWidth;
    fitState.availableHeight = availableHeight;

    // First measure the dialog at its target width and natural height. Height is only clamped when the
    // resulting layout contains a vertical scroll surface; otherwise fail open rather than silently clipping.
    window.setLayout(targetWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
    final View decor = window.getDecorView();
    fitState.decor = decor;
    final DialogFitState state = fitState;
    final ViewTreeObserver.OnPreDrawListener measureListener = new ViewTreeObserver.OnPreDrawListener() {
      @Override
      public boolean onPreDraw()
      {
        clearPendingDialogFit(state);
        if (!dialog.isShowing() || state.targetWidth != targetWidth || state.availableHeight != availableHeight)
          return true;

        if (decor.getHeight() <= availableHeight || !hasPotentialVerticalScroll(decor))
          return true;

        window.setLayout(targetWidth, availableHeight);
        verifyClampedDialog(dialog, window, decor, state, targetWidth, availableHeight);
        return false;
      }
    };
    state.pendingPreDraw = measureListener;
    decor.getViewTreeObserver().addOnPreDrawListener(measureListener);
  }

  @UiThread
  public static void releaseDialog(@NonNull Dialog dialog)
  {
    final DialogFitState state = DIALOG_FITS.remove(dialog);
    if (state != null)
      clearPendingDialogFit(state);
  }

  private static void verifyClampedDialog(@NonNull Dialog dialog, @NonNull Window window, @NonNull View decor,
                                          @NonNull DialogFitState state, int targetWidth, int availableHeight)
  {
    final ViewTreeObserver.OnPreDrawListener verificationListener = new ViewTreeObserver.OnPreDrawListener() {
      @Override
      public boolean onPreDraw()
      {
        clearPendingDialogFit(state);
        if (!dialog.isShowing() || state.targetWidth != targetWidth || state.availableHeight != availableHeight)
          return true;

        if (hasActiveVerticalScroll(decor))
          return true;

        // The fixed-height layout did not expose a working vertical scroll path. Revert to the natural height
        // rather than leave any content or action row silently unreachable in a reduced vendor window.
        window.setLayout(targetWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        return false;
      }
    };
    state.decor = decor;
    state.pendingPreDraw = verificationListener;
    decor.getViewTreeObserver().addOnPreDrawListener(verificationListener);
  }

  private static void clearPendingDialogFit(@NonNull DialogFitState state)
  {
    if (state.decor != null && state.pendingPreDraw != null)
    {
      final ViewTreeObserver observer = state.decor.getViewTreeObserver();
      if (observer.isAlive())
        observer.removeOnPreDrawListener(state.pendingPreDraw);
    }
    state.decor = null;
    state.pendingPreDraw = null;
  }

  private static boolean hasPotentialVerticalScroll(@NonNull View view)
  {
    if (view instanceof ScrollView || view instanceof NestedScrollView || view instanceof AbsListView
        || view instanceof WebView || view.isScrollContainer())
      return true;

    if (!(view instanceof ViewGroup group))
      return false;
    for (int i = 0; i < group.getChildCount(); ++i)
    {
      if (hasPotentialVerticalScroll(group.getChildAt(i)))
        return true;
    }
    return false;
  }

  private static boolean hasActiveVerticalScroll(@NonNull View view)
  {
    if (view.canScrollVertically(-1) || view.canScrollVertically(1))
      return true;

    if (!(view instanceof ViewGroup group))
      return false;
    for (int i = 0; i < group.getChildCount(); ++i)
    {
      if (hasActiveVerticalScroll(group.getChildAt(i)))
        return true;
    }
    return false;
  }

  private static void applyToFragmentView(@NonNull FragmentActivity activity, @NonNull Observation observation,
                                          @NonNull View view)
  {
    final WindowSnapshot snapshot = observation.snapshot;
    if (snapshot == null)
    {
      scheduleReconcile(activity, observation, TransitionReason.FRAGMENT_VIEW);
      return;
    }

    apply(activity, view, Config.isInCarOptimisedVisualsEnabled(), snapshot.profile);
  }

  private static boolean isCompact(@NonNull WindowProfile profile)
  {
    return profile != WindowProfile.FULL;
  }

  private static void apply(@NonNull Activity activity, @NonNull View scope, boolean enabled,
                            @NonNull WindowProfile profile)
  {
    applyMapButtons(activity, scope, enabled, profile);
    applyRoutingControls(activity, scope, enabled, profile);
    applyNavigationControls(activity, scope, enabled, profile);
    applyPlacePageControls(activity, scope, profile);
  }

  private static void applyMapButtons(@NonNull Activity activity, @NonNull View scope, boolean enabled,
                                      @NonNull WindowProfile profile)
  {
    final View mapButtonsContainer = scope.findViewById(R.id.map_buttons);
    final View root = mapButtonsContainer != null ? mapButtonsContainer : scope;
    // MapButtonsController replaces the FragmentContainerView child at runtime. Its fragment root does not carry
    // the map_buttons id, so recognise that subtree by the inner-right frame shared by both map-button layouts.
    if (root.findViewById(R.id.map_buttons_inner_right) == null)
      return;

    final boolean compact = isCompact(profile);
    final int buttonSize = selectDimen(activity, enabled, compact, R.dimen.map_button_size,
                                       R.dimen.in_car_map_button_size, R.dimen.in_car_compact_map_button_size);
    final int iconSize = selectDimen(activity, enabled, compact, R.dimen.map_button_icon_size,
                                     R.dimen.in_car_map_button_icon_size, R.dimen.in_car_compact_map_button_icon_size);
    final int zoomIconSize =
        selectDimen(activity, enabled, compact, R.dimen.map_button_icon_size, R.dimen.in_car_zoom_button_icon_size,
                    R.dimen.in_car_compact_zoom_button_icon_size);
    final int minTouchTarget =
        selectDimen(activity, enabled, compact, R.dimen.map_button_size, R.dimen.in_car_button_min_touch_target,
                    R.dimen.in_car_compact_button_min_touch_target);

    for (int id : new int[] {R.id.btn_search, R.id.btn_bookmarks, R.id.my_position, R.id.layers_button,
                             R.id.menu_button, R.id.help_button, R.id.track_recording_status})
      resizeFab(root.findViewById(id), buttonSize, iconSize, minTouchTarget);

    resizeFab(root.findViewById(R.id.nav_zoom_in), buttonSize, zoomIconSize, minTouchTarget);
    resizeFab(root.findViewById(R.id.nav_zoom_out), buttonSize, zoomIconSize, minTouchTarget);
  }

  private static void applyRoutingControls(@NonNull Activity activity, @NonNull View scope, boolean enabled,
                                           @NonNull WindowProfile profile)
  {
    final View root = scope.findViewById(R.id.routing_root);
    if (root == null)
      return;

    final boolean compact = isCompact(profile);
    final int actionButtonSize =
        selectDimen(activity, enabled, compact, R.dimen.routing_action_button_size,
                    R.dimen.in_car_routing_action_button_size, R.dimen.in_car_compact_routing_action_button_size);
    final int actionIconSize = selectDimen(activity, enabled, compact, R.dimen.routing_action_button_icon_size,
                                           R.dimen.in_car_routing_action_button_icon_size,
                                           R.dimen.in_car_compact_routing_action_button_icon_size);
    final int minTouchTarget =
        selectDimen(activity, enabled, compact, R.dimen.routing_action_button_size,
                    R.dimen.in_car_button_min_touch_target, R.dimen.in_car_compact_button_min_touch_target);

    for (int id : new int[] {R.id.routing_btn_search, R.id.routing_btn_bookmarks, R.id.btn__save})
      resizeFab(root.findViewById(id), actionButtonSize, actionIconSize, minTouchTarget);

    final int routerHeight =
        selectDimen(activity, enabled, compact, R.dimen.routing_toolbar_cell_height,
                    R.dimen.in_car_routing_toolbar_cell_height, R.dimen.in_car_compact_routing_toolbar_cell_height);
    for (int id : new int[] {R.id.vehicle, R.id.pedestrian, R.id.transit, R.id.bicycle, R.id.ruler})
      setViewHeight(root.findViewById(id), routerHeight);

    final int closeSize =
        dimen(activity, compact ? R.dimen.in_car_compact_close_button_size : R.dimen.in_car_routing_close_button_size);
    setViewSize(root.findViewById(R.id.back), closeSize, closeSize);
    root.requestLayout();
  }

  private static void applyNavigationControls(@NonNull Activity activity, @NonNull View scope, boolean enabled,
                                              @NonNull WindowProfile profile)
  {
    final View root = scope.findViewById(R.id.nav_bottom_frame);
    if (root == null)
      return;

    final boolean compact = isCompact(profile);
    final int contentHeight =
        selectDimen(activity, enabled, compact, R.dimen.nav_menu_content_height, R.dimen.in_car_nav_menu_content_height,
                    R.dimen.in_car_compact_nav_menu_content_height);
    setViewHeight(root.findViewById(R.id.content_frame), contentHeight);

    final int iconHeight = selectDimen(activity, enabled, compact, R.dimen.nav_icon_size, R.dimen.in_car_nav_icon_size,
                                       R.dimen.in_car_compact_nav_icon_size);
    final ImageView tts = root.findViewById(R.id.tts_volume);
    final ImageView settings = root.findViewById(R.id.settings);
    setViewHeight(tts, iconHeight);
    setViewHeight(settings, iconHeight);
    if (tts != null)
      tts.setScaleType(enabled ? ImageView.ScaleType.FIT_CENTER : ImageView.ScaleType.CENTER);
    if (settings != null)
      settings.setScaleType(enabled ? ImageView.ScaleType.FIT_CENTER : ImageView.ScaleType.CENTER);

    final Button stop = root.findViewById(R.id.stop);
    if (stop == null)
      return;
    final int buttonHeight = selectDimen(activity, enabled, compact, R.dimen.nav_button_height,
                                         R.dimen.in_car_nav_button_height, R.dimen.in_car_compact_nav_button_height);
    final int stopMinWidth = selectDimen(activity, enabled, compact, R.dimen.start_button_width,
                                         R.dimen.in_car_nav_stop_min_width, R.dimen.in_car_compact_nav_stop_min_width);
    setViewHeight(stop, buttonHeight);
    stop.setMinHeight(buttonHeight);
    stop.setMinWidth(stopMinWidth);
    root.requestLayout();
  }

  private static void applyPlacePageControls(@NonNull Activity activity, @NonNull View scope,
                                             @NonNull WindowProfile profile)
  {
    final MaterialButton close = scope.findViewById(R.id.close_button);
    if (close == null)
      return;

    final boolean compact = isCompact(profile);
    final int controlSize = dimen(
        activity, compact ? R.dimen.in_car_compact_close_button_size : R.dimen.in_car_place_page_close_button_size);
    final int iconSize =
        dimen(activity, compact ? R.dimen.in_car_compact_close_icon_size : R.dimen.in_car_close_icon_size);
    setViewSize(close, controlSize, controlSize);
    close.setMinimumWidth(controlSize);
    close.setMinimumHeight(controlSize);
    close.setIconSize(iconSize);
  }

  private static void fitVisibleDialogs(@NonNull FragmentManager fragmentManager, @NonNull FragmentActivity activity)
  {
    for (Fragment fragment : fragmentManager.getFragments())
    {
      if (fragment instanceof DialogFragment dialogFragment)
      {
        final Dialog dialog = dialogFragment.getDialog();
        if (dialog != null && dialog.isShowing())
          fitDialog(activity, dialog);
      }

      if (fragment.isAdded())
        fitVisibleDialogs(fragment.getChildFragmentManager(), activity);
    }
  }

  private static int selectDimen(@NonNull Activity activity, boolean enabled, boolean compact, @DimenRes int normal,
                                 @DimenRes int inCar, @DimenRes int inCarCompact)
  {
    return dimen(activity, enabled ? (compact ? inCarCompact : inCar) : normal);
  }

  private static int dimen(@NonNull Activity activity, @DimenRes int resId)
  {
    return activity.getResources().getDimensionPixelSize(resId);
  }

  private static void resizeFab(@Nullable View view, int buttonSize, int iconSize, int minTouchTarget)
  {
    if (!(view instanceof FloatingActionButton button))
      return;
    button.setCustomSize(buttonSize);
    button.setMaxImageSize(iconSize);
    button.setMinimumWidth(minTouchTarget);
    button.setMinimumHeight(minTouchTarget);
  }

  private static void setViewHeight(@Nullable View view, int height)
  {
    if (view == null)
      return;
    final ViewGroup.LayoutParams params = view.getLayoutParams();
    if (params == null || params.height == height)
      return;
    params.height = height;
    view.setLayoutParams(params);
  }

  private static void setViewSize(@Nullable View view, int width, int height)
  {
    if (view == null)
      return;
    final ViewGroup.LayoutParams params = view.getLayoutParams();
    if (params == null || (params.width == width && params.height == height))
      return;
    params.width = width;
    params.height = height;
    view.setLayoutParams(params);
  }
}
