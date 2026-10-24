package app.organicmaps.incar;

import static org.junit.Assert.*;

import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import androidx.core.graphics.Insets;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import app.organicmaps.R;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class InCarSpeedWarningViewTest
{
  @Test
  public void bannerIsHalfTransparentInsetAwareAndPassiveAtEverySize()
  {
    try (ActivityScenario<InCarUiTestActivity> scenario = ActivityScenario.launch(InCarUiTestActivity.class))
    {
      scenario.onActivity(activity -> {
        final FrameLayout frame = new FrameLayout(activity);
        final InCarSpeedWarningView banner = new InCarSpeedWarningView(activity, null);
        frame.addView(banner, new FrameLayout.LayoutParams(1, FrameLayout.LayoutParams.MATCH_PARENT));
        activity.setContentView(frame);
        assertEquals(0.5f, banner.getAlpha(), 0.001f);
        assertFalse(banner.isClickable());
        assertFalse(banner.isFocusable());
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO, banner.getImportantForAccessibility());
        for (boolean controlsLeft : new boolean[] {true, false})
        {
          for (int circle : new int[] {76, 69, 55})
          {
            banner.updateGeometry(circle, controlsLeft, Insets.of(3, 5, 7, 11));
            final FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) banner.getLayoutParams();
            assertEquals((controlsLeft ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, params.gravity);
            assertEquals(
                circle + activity.getResources().getDimensionPixelSize(R.dimen.in_car_speed_edge_warning_extra_width),
                params.width);
            frame.measure(View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY),
                          View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY));
            banner.render(true, InCarSpeedDisplayPolicy.Severity.WARNING);
            frame.measure(View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY),
                          View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY));
            frame.layout(0, 0, 500, 300);
            assertEquals(284, banner.getHeight());
            assertEquals(controlsLeft ? 493 - params.width : 3, banner.getLeft());
            assertEquals(Color.rgb(255, 152, 0), ((ColorDrawable) banner.getBackground()).getColor());
            banner.render(true, InCarSpeedDisplayPolicy.Severity.SEVERE);
            assertEquals(Color.rgb(244, 67, 54), ((ColorDrawable) banner.getBackground()).getColor());
            banner.render(false, InCarSpeedDisplayPolicy.Severity.SEVERE);
            assertEquals(View.GONE, banner.getVisibility());
            banner.render(true, InCarSpeedDisplayPolicy.Severity.NORMAL);
            assertEquals(View.GONE, banner.getVisibility());
          }
        }
      });
    }
  }

  @Test
  public void bannerPassesRealTouchesToTheUnderlyingViewAndSettingDefaultsOn()
  {
    final java.util.concurrent.atomic.AtomicInteger taps = new java.util.concurrent.atomic.AtomicInteger();
    final int[] point = new int[2];
    try (ActivityScenario<InCarUiTestActivity> scenario = ActivityScenario.launch(InCarUiTestActivity.class))
    {
      scenario.onActivity(activity -> {
        final android.content.SharedPreferences prefs = app.organicmaps.MwmApplication.prefs(activity);
        final boolean hadKey = prefs.contains("InCarSpeedEdgeWarning");
        final boolean saved = InCarSettingsStore.speedEdgeWarningEnabled(activity);
        try
        {
          prefs.edit().remove("InCarSpeedEdgeWarning").commit();
          assertTrue(InCarSettingsStore.speedEdgeWarningEnabled(activity));
          InCarSettingsStore.setSpeedEdgeWarningEnabled(activity, false);
          assertFalse(InCarSettingsStore.speedEdgeWarningEnabled(activity));
          InCarSettingsStore.setSpeedEdgeWarningEnabled(activity, true);
          assertTrue(InCarSettingsStore.speedEdgeWarningEnabled(activity));
        }
        finally
        {
          if (hadKey)
            prefs.edit().putBoolean("InCarSpeedEdgeWarning", saved).commit();
          else
            prefs.edit().remove("InCarSpeedEdgeWarning").commit();
        }
        final FrameLayout frame = new FrameLayout(activity);
        final View map = new View(activity);
        map.setOnClickListener(view -> taps.incrementAndGet());
        frame.addView(map, new FrameLayout.LayoutParams(-1, -1));
        final InCarSpeedWarningView banner = new InCarSpeedWarningView(activity, null);
        frame.addView(banner, new FrameLayout.LayoutParams(1, -1));
        banner.updateGeometry(76, true, Insets.NONE);
        banner.render(true, InCarSpeedDisplayPolicy.Severity.SEVERE);
        activity.setContentView(frame);
      });
      androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync();
      scenario.onActivity(activity -> {
        final View frame = ((android.view.ViewGroup) activity.findViewById(android.R.id.content)).getChildAt(0);
        frame.getLocationOnScreen(point);
        point[0] += frame.getWidth() - 3;
        point[1] += frame.getHeight() / 2;
      });
      app.organicmaps.settings.InCarSettingsGeometryTest.tap(point[0], point[1]);
      assertEquals(1, taps.get());
    }
  }

  @Test
  public void warningColourRefreshesAtUnchangedSeverityWhenNightChanges()
  {
    try (ActivityScenario<InCarUiTestActivity> scenario = ActivityScenario.launch(InCarUiTestActivity.class))
    {
      scenario.onActivity(activity -> {
        final Configuration day = new Configuration(activity.getResources().getConfiguration());
        day.uiMode = (day.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_NO;
        final android.content.Context context =
            new android.view.ContextThemeWrapper(activity.createConfigurationContext(day), R.style.MwmTheme);
        final InCarNavigationSpeedView speed = new InCarNavigationSpeedView(context);
        InCarSpeedDisplayPolicy.isSpeeding(111, 100);
        speed.setSpeeding(true);
        assertEquals(Color.BLACK, speed.getCurrentTextColor());
        final Configuration night = new Configuration(day);
        night.uiMode = (night.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_YES;
        context.getResources().updateConfiguration(night, context.getResources().getDisplayMetrics());
        context.getTheme().applyStyle(R.style.MwmTheme, true);
        speed.onConfigurationChanged(night);
        speed.setSpeeding(true);
        assertEquals(Color.WHITE, speed.getCurrentTextColor());
        InCarSpeedDisplayPolicy.resetSpeeding();
        speed.setSpeeding(false);
        assertEquals(app.organicmaps.util.ThemeUtils.getColor(context, R.attr.iconTint), speed.getCurrentTextColor());
      });
    }
  }
}
