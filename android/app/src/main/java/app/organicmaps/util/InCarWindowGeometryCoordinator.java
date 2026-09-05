package app.organicmaps.util;

import android.content.Intent;
import android.content.res.Configuration;
import android.view.SurfaceHolder;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.VisibleForTesting;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Lifecycle;
import app.organicmaps.BuildConfig;
import app.organicmaps.R;
import app.organicmaps.sdk.MapView;
import app.organicmaps.sdk.util.log.Logger;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Owns InCar map/surface/native geometry convergence inside the task bounds chosen by Android or the OEM launcher.
 *
 * <p>This class deliberately does not request task bounds, fullscreen, freeform, split-screen or PiP state. The
 * system/launcher owns the outer window. Standard Android multi-window/PiP flags are logged only as annotations;
 * actual laid-out bounds are the geometry authority.</p>
 */
public final class InCarWindowGeometryCoordinator
{
  private static final String TAG = InCarWindowGeometryCoordinator.class.getSimpleName();
  private static final int MAX_INVALID_BOUNDS_RETRIES = 3;
  private static final int MAX_CONVERGENCE_CHECKS = 8;

  private static final Map<FragmentActivity, Observation> OBSERVATIONS = new WeakHashMap<>();

  @VisibleForTesting
  enum RecoveryAction {
    NONE,
    REQUEST_LAYOUT,
    SURFACE_SIZE_FROM_LAYOUT,
    NATIVE_REAPPLY,
    SURFACE_REATTACH
  }

  @VisibleForTesting
  static final class GeometryStatus
  {
    final boolean expectedValid;
    final boolean mapMismatch;
    final boolean surfaceMismatch;
    final boolean nativeMismatch;

    GeometryStatus(boolean expectedValid, boolean mapMismatch, boolean surfaceMismatch, boolean nativeMismatch)
    {
      this.expectedValid = expectedValid;
      this.mapMismatch = mapMismatch;
      this.surfaceMismatch = surfaceMismatch;
      this.nativeMismatch = nativeMismatch;
    }

    boolean isConverged()
    {
      return expectedValid && !mapMismatch && !surfaceMismatch && !nativeMismatch;
    }

    boolean hasMismatch()
    {
      return mapMismatch || surfaceMismatch || nativeMismatch;
    }
  }

  private static final class Observation
  {
    long transitionGeneration;
    long geometryEpoch;
    int expectedWidth;
    int expectedHeight;
    int taskId = -1;
    int activityInstanceId;
    boolean layoutAttempted;
    boolean surfaceSizeAttempted;
    boolean nativeReapplyAttempted;
    boolean surfaceReattachAttempted;
    @Nullable
    View content;
    @Nullable
    View.OnLayoutChangeListener layoutListener;
    @Nullable
    MapView mapView;
    @Nullable
    SurfaceHolder.Callback surfaceCallback;
    @Nullable
    Runnable pendingGeometryWork;
  }

  private InCarWindowGeometryCoordinator() {}

  @UiThread
  public static void reconcile(@NonNull FragmentActivity activity, @NonNull InCarVisuals.TransitionReason reason)
  {
    if (!BuildConfig.IS_IN_CAR || !isActivityAlive(activity))
      return;

    // InCarVisuals remains the presentation owner for touch-target sizing/dialog fitting. Geometry recovery is
    // intentionally centralised here so no second code path resizes or reattaches the map surface.
    InCarVisuals.reconcile(activity, reason);

    final Observation observation = ensureObservation(activity);
    cancelPendingGeometryWork(observation);
    if (shouldResetRecoveryState(reason))
      resetRecoveryState(observation);
    final long generation = ++observation.transitionGeneration;
    reconcileGeometryNow(activity, observation, reason.name(), generation, MAX_INVALID_BOUNDS_RETRIES);
  }

  @UiThread
  public static void release(@NonNull FragmentActivity activity)
  {
    final Observation observation = OBSERVATIONS.remove(activity);
    if (observation != null)
    {
      cancelPendingGeometryWork(observation);
      if (observation.content != null && observation.layoutListener != null)
        observation.content.removeOnLayoutChangeListener(observation.layoutListener);
      unbindMapView(observation);
    }
    InCarVisuals.release(activity);
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

    final View content = activity.findViewById(android.R.id.content);
    if (content != observation.content)
    {
      if (observation.content != null && observation.layoutListener != null)
        observation.content.removeOnLayoutChangeListener(observation.layoutListener);
      observation.content = content;
      if (content != null)
      {
        final Observation state = observation;
        observation.layoutListener = (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
        {
          if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop)
            scheduleGeometryOnly(activity, state, "ROOT_LAYOUT");
        };
        content.addOnLayoutChangeListener(observation.layoutListener);
      }
    }

    bindMapView(activity, observation);
    return observation;
  }

  private static void bindMapView(@NonNull FragmentActivity activity, @NonNull Observation observation)
  {
    final MapView mapView = activity.findViewById(R.id.map);
    if (mapView == observation.mapView)
      return;

    unbindMapView(observation);
    observation.mapView = mapView;
    if (mapView == null)
      return;

    final Observation state = observation;
    observation.surfaceCallback = new SurfaceHolder.Callback() {
      @Override
      public void surfaceCreated(@NonNull SurfaceHolder holder)
      {
        scheduleGeometryOnly(activity, state, "SURFACE_CREATED");
      }

      @Override
      public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height)
      {
        scheduleGeometryOnly(activity, state, "SURFACE_CHANGED");
      }

      @Override
      public void surfaceDestroyed(@NonNull SurfaceHolder holder)
      {
        scheduleGeometryOnly(activity, state, "SURFACE_DESTROYED");
      }
    };
    mapView.getHolder().addCallback(observation.surfaceCallback);
  }

  private static void unbindMapView(@NonNull Observation observation)
  {
    if (observation.mapView != null && observation.surfaceCallback != null)
      observation.mapView.getHolder().removeCallback(observation.surfaceCallback);
    observation.mapView = null;
    observation.surfaceCallback = null;
  }

  private static void scheduleGeometryOnly(@NonNull FragmentActivity activity, @NonNull Observation observation,
                                           @NonNull String reason)
  {
    if (!BuildConfig.IS_IN_CAR || !isActivityAlive(activity))
      return;

    final View content = observation.content;
    if (content == null)
      return;

    cancelPendingGeometryWork(observation);
    final long generation = ++observation.transitionGeneration;
    final Runnable work = () ->
    {
      observation.pendingGeometryWork = null;
      if (!isGenerationCurrent(generation, observation.transitionGeneration) || !isActivityAlive(activity))
        return;
      reconcileGeometryNow(activity, observation, reason, generation, MAX_INVALID_BOUNDS_RETRIES);
    };
    observation.pendingGeometryWork = work;
    content.postOnAnimation(work);
  }

  private static void reconcileGeometryNow(@NonNull FragmentActivity activity, @NonNull Observation observation,
                                           @NonNull String reason, long generation, int retriesRemaining)
  {
    if (!isGenerationCurrent(generation, observation.transitionGeneration) || !isActivityAlive(activity))
      return;

    ensureObservation(activity);
    final View content = observation.content;
    if (content == null)
      return;

    final View mapContainer = activity.findViewById(R.id.map_container);
    final int expectedWidth =
        mapContainer != null && mapContainer.getWidth() > 0 ? mapContainer.getWidth() : content.getWidth();
    final int expectedHeight =
        mapContainer != null && mapContainer.getHeight() > 0 ? mapContainer.getHeight() : content.getHeight();
    if (!hasValidBounds(expectedWidth, expectedHeight))
    {
      scheduleInvalidBoundsRetry(activity, observation, reason, generation, retriesRemaining);
      return;
    }

    final int taskId = activity.getTaskId();
    final int activityInstanceId = System.identityHashCode(activity);
    if (shouldStartNewGeometryEpoch(observation.expectedWidth, observation.expectedHeight, observation.taskId,
                                    observation.activityInstanceId, expectedWidth, expectedHeight, taskId,
                                    activityInstanceId))
    {
      observation.geometryEpoch++;
      observation.expectedWidth = expectedWidth;
      observation.expectedHeight = expectedHeight;
      observation.taskId = taskId;
      observation.activityInstanceId = activityInstanceId;
      resetRecoveryState(observation);
    }

    logSnapshot(activity, observation, reason);
    if ("SURFACE_DESTROYED".equals(reason))
      return;
    scheduleConvergenceCheck(activity, observation, generation, MAX_CONVERGENCE_CHECKS);
  }

  private static void scheduleInvalidBoundsRetry(@NonNull FragmentActivity activity, @NonNull Observation observation,
                                                 @NonNull String reason, long generation, int retriesRemaining)
  {
    if (retriesRemaining <= 0 || observation.content == null)
    {
      Logger.w(TAG, "Window geometry remains unmeasured: reason=" + reason + " generation=" + generation);
      return;
    }

    final Runnable retry = () ->
    {
      observation.pendingGeometryWork = null;
      if (!isGenerationCurrent(generation, observation.transitionGeneration) || !isActivityAlive(activity))
        return;
      reconcileGeometryNow(activity, observation, reason + "/RETRY", generation, retriesRemaining - 1);
    };
    observation.pendingGeometryWork = retry;
    observation.content.postOnAnimation(retry);
  }

  private static void scheduleConvergenceCheck(@NonNull FragmentActivity activity, @NonNull Observation observation,
                                               long generation, int checksRemaining)
  {
    final View content = observation.content;
    if (content == null || checksRemaining <= 0)
      return;

    final Runnable verification = () ->
    {
      observation.pendingGeometryWork = null;
      if (!isGenerationCurrent(generation, observation.transitionGeneration) || !isActivityAlive(activity))
        return;

      bindMapView(activity, observation);
      final MapView mapView = observation.mapView;
      if (mapView == null)
        return;
      if (!isRecoveryHostReady(activity, mapView))
      {
        Logger.d(TAG, "Window geometry recovery deferred until resumed host: generation=" + generation);
        return;
      }

      final GeometryStatus status =
          evaluateGeometry(observation.expectedWidth, observation.expectedHeight, mapView.getWidth(),
                           mapView.getHeight(), mapView.getSurfaceFrameWidth(), mapView.getSurfaceFrameHeight(),
                           mapView.getLastAppliedSurfaceWidth(), mapView.getLastAppliedSurfaceHeight());
      if (status.isConverged())
      {
        Logger.d(TAG, "Window geometry converged: epoch=" + observation.geometryEpoch + " generation=" + generation
                          + " size=" + observation.expectedWidth + "x" + observation.expectedHeight);
        return;
      }

      final boolean canReattach = canReattachSurface(activity, mapView);
      final RecoveryAction action =
          chooseRecoveryAction(status, observation.layoutAttempted, observation.surfaceSizeAttempted,
                               observation.nativeReapplyAttempted, observation.surfaceReattachAttempted, canReattach);
      performRecovery(action, mapView, content, observation);

      if (checksRemaining > 1)
      {
        scheduleConvergenceCheck(activity, observation, generation, checksRemaining - 1);
        return;
      }

      Logger.w(TAG,
               "Window geometry failed to converge: epoch=" + observation.geometryEpoch + " generation=" + generation
                   + " expected=" + observation.expectedWidth + "x" + observation.expectedHeight + " map="
                   + mapView.getWidth() + "x" + mapView.getHeight() + " surface=" + mapView.getSurfaceFrameWidth() + "x"
                   + mapView.getSurfaceFrameHeight() + " native=" + mapView.getLastAppliedSurfaceWidth() + "x"
                   + mapView.getLastAppliedSurfaceHeight() + " action=" + action + " attempts={layout="
                   + observation.layoutAttempted + ",surface=" + observation.surfaceSizeAttempted + ",native="
                   + observation.nativeReapplyAttempted + ",reattach=" + observation.surfaceReattachAttempted + "}");
    };
    observation.pendingGeometryWork = verification;
    content.postOnAnimation(verification);
  }

  private static void performRecovery(@NonNull RecoveryAction action, @NonNull MapView mapView, @NonNull View content,
                                      @NonNull Observation observation)
  {
    switch (action)
    {
    case REQUEST_LAYOUT:
      observation.layoutAttempted = true;
      mapView.requestLayout();
      content.requestLayout();
      break;
    case SURFACE_SIZE_FROM_LAYOUT:
      observation.surfaceSizeAttempted = true;
      mapView.requestSurfaceSizeFromLayout();
      break;
    case NATIVE_REAPPLY:
      observation.nativeReapplyAttempted = true;
      mapView.reapplyCurrentSurfaceSize();
      break;
    case SURFACE_REATTACH:
      observation.surfaceReattachAttempted = true;
      mapView.recoverSurfaceAttachment();
      break;
    case NONE: break;
    }
  }

  private static boolean isRecoveryHostReady(@NonNull FragmentActivity activity, @NonNull MapView mapView)
  {
    return activity.getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED) && mapView.isAttachedToWindow();
  }

  private static boolean canReattachSurface(@NonNull FragmentActivity activity, @NonNull MapView mapView)
  {
    if (!isRecoveryHostReady(activity, mapView) || !mapView.isSurfaceAttachmentRecoveryAllowed())
      return false;
    final android.view.Surface surface = mapView.getHolder().getSurface();
    return surface != null && surface.isValid()
 && hasValidBounds(mapView.getSurfaceFrameWidth(), mapView.getSurfaceFrameHeight());
  }

  private static void resetRecoveryState(@NonNull Observation observation)
  {
    observation.layoutAttempted = false;
    observation.surfaceSizeAttempted = false;
    observation.nativeReapplyAttempted = false;
    observation.surfaceReattachAttempted = false;
  }

  private static void cancelPendingGeometryWork(@NonNull Observation observation)
  {
    if (observation.content != null && observation.pendingGeometryWork != null)
      observation.content.removeCallbacks(observation.pendingGeometryWork);
    observation.pendingGeometryWork = null;
  }

  @VisibleForTesting
  static boolean hasValidBounds(int width, int height)
  {
    return width > 0 && height > 0;
  }

  @VisibleForTesting
  static boolean isGenerationCurrent(long scheduledGeneration, long currentGeneration)
  {
    return scheduledGeneration == currentGeneration;
  }

  @VisibleForTesting
  static boolean shouldResetRecoveryState(@NonNull InCarVisuals.TransitionReason reason)
  {
    return reason == InCarVisuals.TransitionReason.RESUME;
  }

  @VisibleForTesting
  static boolean shouldStartNewGeometryEpoch(int oldWidth, int oldHeight, int oldTaskId, int oldActivityInstanceId,
                                             int newWidth, int newHeight, int newTaskId, int newActivityInstanceId)
  {
    return oldWidth != newWidth || oldHeight != newHeight || oldTaskId != newTaskId
 || oldActivityInstanceId != newActivityInstanceId;
  }

  @NonNull
  @VisibleForTesting
  static GeometryStatus evaluateGeometry(int expectedWidth, int expectedHeight, int mapWidth, int mapHeight,
                                         int surfaceWidth, int surfaceHeight, int nativeWidth, int nativeHeight)
  {
    final boolean expectedValid = hasValidBounds(expectedWidth, expectedHeight);
    final boolean mapMismatch =
        expectedValid
        && (!hasValidBounds(mapWidth, mapHeight) || mapWidth != expectedWidth || mapHeight != expectedHeight);
    final boolean surfaceMismatch =
        hasValidBounds(mapWidth, mapHeight)
        && (!hasValidBounds(surfaceWidth, surfaceHeight) || surfaceWidth != mapWidth || surfaceHeight != mapHeight);
    final boolean nativeMismatch =
        hasValidBounds(surfaceWidth, surfaceHeight)
        && (!hasValidBounds(nativeWidth, nativeHeight) || nativeWidth != surfaceWidth || nativeHeight != surfaceHeight);
    return new GeometryStatus(expectedValid, mapMismatch, surfaceMismatch, nativeMismatch);
  }

  @NonNull
  @VisibleForTesting
  static RecoveryAction chooseRecoveryAction(@NonNull GeometryStatus status, boolean layoutAttempted,
                                             boolean surfaceSizeAttempted, boolean nativeReapplyAttempted,
                                             boolean surfaceReattachAttempted, boolean canReattach)
  {
    if (!status.expectedValid || !status.hasMismatch())
      return RecoveryAction.NONE;
    if (status.mapMismatch)
      return layoutAttempted ? RecoveryAction.NONE : RecoveryAction.REQUEST_LAYOUT;
    if (status.surfaceMismatch)
      return surfaceSizeAttempted ? RecoveryAction.NONE : RecoveryAction.SURFACE_SIZE_FROM_LAYOUT;
    if (status.nativeMismatch && !nativeReapplyAttempted)
      return RecoveryAction.NATIVE_REAPPLY;
    if (status.nativeMismatch && !surfaceReattachAttempted && canReattach)
      return RecoveryAction.SURFACE_REATTACH;
    return RecoveryAction.NONE;
  }

  private static boolean isActivityAlive(@NonNull FragmentActivity activity)
  {
    return !activity.isFinishing() && !activity.isDestroyed();
  }

  private static void logSnapshot(@NonNull FragmentActivity activity, @NonNull Observation observation,
                                  @NonNull String reason)
  {
    final MapView mapView = observation.mapView;
    final Configuration config = activity.getResources().getConfiguration();
    final WindowInsetsCompat windowInsets =
        observation.content == null ? null : ViewCompat.getRootWindowInsets(observation.content);
    final Insets systemBars =
        windowInsets == null ? Insets.NONE : windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
    final boolean statusVisible = windowInsets != null && windowInsets.isVisible(WindowInsetsCompat.Type.statusBars());
    final boolean navVisible = windowInsets != null && windowInsets.isVisible(WindowInsetsCompat.Type.navigationBars());
    final Intent intent = activity.getIntent();
    final Set<String> categories = intent == null ? null : intent.getCategories();
    final boolean multi =
        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N && activity.isInMultiWindowMode();
    final boolean pip =
        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N && activity.isInPictureInPictureMode();

    Logger.i(
        TAG,
        "reason=" + reason + " transitionGen=" + observation.transitionGeneration
            + " epoch=" + observation.geometryEpoch + " task=" + activity.getTaskId() + " activity="
            + System.identityHashCode(activity) + " lifecycle=" + activity.getLifecycle().getCurrentState()
            + " multi=" + multi + " pip=" + pip + " config=" + config.screenWidthDp + "x" + config.screenHeightDp
            + "/" + config.orientation + " expected=" + observation.expectedWidth + "x" + observation.expectedHeight
            + " map=" + (mapView == null ? "0x0" : mapView.getWidth() + "x" + mapView.getHeight()) + " surface="
            + (mapView == null ? "0x0" : mapView.getSurfaceFrameWidth() + "x" + mapView.getSurfaceFrameHeight())
            + " native="
            + (mapView == null ? "0x0"
                               : mapView.getLastAppliedSurfaceWidth() + "x" + mapView.getLastAppliedSurfaceHeight())
            + " bars={status=" + statusVisible + ",nav=" + navVisible + ",insets=" + systemBars.left + ","
            + systemBars.top + "," + systemBars.right + "," + systemBars.bottom
            + "} intent={action=" + (intent == null ? null : intent.getAction())
            + ",component=" + (intent == null ? null : intent.getComponent())
            + ",categories=" + (categories == null ? "[]" : categories) + ",flags=0x"
            + (intent == null ? "0" : Integer.toHexString(intent.getFlags())) + "}");
  }
}
