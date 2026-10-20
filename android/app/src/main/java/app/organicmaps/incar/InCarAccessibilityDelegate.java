package app.organicmaps.incar;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.function.BooleanSupplier;

/** Observes idle-action interruptions without replacing the original accessibility behaviour. */
public final class InCarAccessibilityDelegate extends View.AccessibilityDelegate
{
  private final View.AccessibilityDelegate mOriginal;
  private final Runnable mCancel;
  private final BooleanSupplier mPending;
  private final BooleanSupplier mDestroyed;

  public InCarAccessibilityDelegate(@NonNull View.AccessibilityDelegate original, @NonNull Runnable cancel,
                                    @NonNull BooleanSupplier pending, @NonNull BooleanSupplier destroyed)
  {
    mOriginal = original;
    mCancel = cancel;
    mPending = pending;
    mDestroyed = destroyed;
  }

  @Override
  public boolean onRequestSendAccessibilityEvent(@NonNull ViewGroup host, @NonNull View child,
                                                 @NonNull AccessibilityEvent event)
  {
    final int type = event.getEventType();
    final boolean pendingClick =
        (type == AccessibilityEvent.TYPE_VIEW_CLICKED || type == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED)
        && mPending.getAsBoolean();
    if (!mDestroyed.getAsBoolean()
        && (type == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED
            || type == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED
            || type == AccessibilityEvent.TYPE_VIEW_SCROLLED || pendingClick))
      mCancel.run();
    // Content updates are not interaction. Owned clicks already cancel before dispatch;
    // their later click event must not invalidate a newly posted GO TO/START handoff.
    return mOriginal.onRequestSendAccessibilityEvent(host, child, event);
  }

  @Override
  public void sendAccessibilityEvent(@NonNull View host, int type)
  {
    mOriginal.sendAccessibilityEvent(host, type);
  }

  @Override
  public void sendAccessibilityEventUnchecked(@NonNull View host, @NonNull AccessibilityEvent event)
  {
    mOriginal.sendAccessibilityEventUnchecked(host, event);
  }

  @Override
  public boolean dispatchPopulateAccessibilityEvent(@NonNull View host, @NonNull AccessibilityEvent event)
  {
    return mOriginal.dispatchPopulateAccessibilityEvent(host, event);
  }

  @Override
  public void onPopulateAccessibilityEvent(@NonNull View host, @NonNull AccessibilityEvent event)
  {
    mOriginal.onPopulateAccessibilityEvent(host, event);
  }

  @Override
  public void onInitializeAccessibilityEvent(@NonNull View host, @NonNull AccessibilityEvent event)
  {
    mOriginal.onInitializeAccessibilityEvent(host, event);
  }

  @Override
  public void onInitializeAccessibilityNodeInfo(@NonNull View host, @NonNull AccessibilityNodeInfo info)
  {
    mOriginal.onInitializeAccessibilityNodeInfo(host, info);
  }

  @Nullable
  @Override
  public AccessibilityNodeProvider getAccessibilityNodeProvider(@NonNull View host)
  {
    return mOriginal.getAccessibilityNodeProvider(host);
  }

  @Override
  public void addExtraDataToAccessibilityNodeInfo(@NonNull View host, @NonNull AccessibilityNodeInfo info,
                                                  @NonNull String extraDataKey, @NonNull Bundle arguments)
  {
    // The InCar decor wrapper is installed on API29+, but this shared source also builds for API21.
    if (android.os.Build.VERSION.SDK_INT >= 26)
      mOriginal.addExtraDataToAccessibilityNodeInfo(host, info, extraDataKey, arguments);
  }

  @Override
  public boolean performAccessibilityAction(@NonNull View host, int action, @Nullable Bundle arguments)
  {
    return mOriginal.performAccessibilityAction(host, action, arguments);
  }
}
