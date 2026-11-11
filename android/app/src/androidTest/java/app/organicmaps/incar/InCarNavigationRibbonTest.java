package app.organicmaps.incar;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import app.organicmaps.R;
import app.organicmaps.sdk.routing.LaneInfo;
import app.organicmaps.sdk.routing.LaneWay;
import app.organicmaps.sdk.routing.roadshield.RoadShield;
import app.organicmaps.sdk.routing.roadshield.RoadShieldInfo;
import app.organicmaps.sdk.routing.roadshield.RoadShieldType;
import app.organicmaps.sdk.widget.roadshield.RoadShieldUtils;
import app.organicmaps.sdk.widgets.lanes.LanesView;
import java.lang.reflect.Constructor;
import java.util.Locale;
import org.junit.Test;
import org.junit.runner.RunWith;

@SdkSuppress(minSdkVersion = 26)
@RunWith(AndroidJUnit4.class)
public class InCarNavigationRibbonTest
{
  @Test
  public void longRoadNamesUseTheMeasuredHeightAndKeepGuidanceCentred()
  {
    try (ActivityScenario<InCarUiTestActivity> scenario = ActivityScenario.launch(InCarUiTestActivity.class))
    {
      scenario.onActivity(activity -> {
        boolean exercisedMoreThanThreeLines = false;
        for (boolean night : new boolean[] {false, true})
        {
          for (float fontScale : new float[] {1.0f, 1.3f})
          {
            final Configuration config = new Configuration(activity.getResources().getConfiguration());
            config.orientation = Configuration.ORIENTATION_LANDSCAPE;
            config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                          | (night ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
            config.fontScale = fontScale;
            config.setLocale(Locale.ENGLISH);
            final Context context =
                new ContextThemeWrapper(activity.createConfigurationContext(config), R.style.MwmTheme);
            final ViewGroup root = (ViewGroup) LayoutInflater.from(context).inflate(R.layout.layout_nav_top, null);
            activity.setContentView(root);
            final ViewGroup now = root.findViewById(R.id.in_car_nav_immediate_group);
            final TextView street = root.findViewById(R.id.street);
            final TextView distance = now.findViewById(R.id.distance);
            final ImageView turn = now.findViewById(R.id.turn);
            turn.setImageResource(app.organicmaps.sdk.R.drawable.ic_roundabout_exit_12);
            distance.setText("320 m");
            root.findViewById(R.id.in_car_nav_speed).setVisibility(View.VISIBLE);
            root.findViewById(R.id.in_car_nav_guidance_label).setVisibility(View.VISIBLE);
            ((LanesView) root.findViewById(R.id.lanes))
                .setLanes(new LaneInfo[] {new LaneInfo(new LaneWay[] {LaneWay.Through}, LaneWay.Through),
                                          new LaneInfo(new LaneWay[] {LaneWay.Right}, LaneWay.None)});
            final String longRoad = "Barton Highway towards Northbourne Avenue via Flemington Road and Federal Highway";
            final String arabicRoad = "شارع نورثبورن باتجاه طريق بارتون السريع عبر طريق فليمنغتون وطريق فيدرال السريع";
            final CharSequence[] names = {
                longRoad, arabicRoad, RoadShieldUtils.createAdaptiveStreetTextWithShields("A25", shieldInfo(3)),
                RoadShieldUtils.createAdaptiveStreetTextWithShields("A25 " + longRoad, shieldInfo(3))};
            // The same Views must refit when the task returns from full screen to a compact window.
            for (int widthDp : new int[] {640, 1100, 640})
            {
              for (CharSequence name : names)
              {
                street.setTextDirection(name.toString().equals(arabicRoad) ? View.TEXT_DIRECTION_RTL
                                                                           : View.TEXT_DIRECTION_LTR);
                street.setText(name);
                measure(root, px(context, widthDp), px(context, 240));
                assertTextFits(street);
                assertTextFits(distance);
                assertEquals(Gravity.RIGHT | Gravity.CENTER_VERTICAL, street.getGravity());
                assertEquals(Gravity.END | Gravity.CENTER_VERTICAL, distance.getGravity());
                final Rect nowBounds = bounds(root, now);
                final Rect turnBounds = bounds(root, turn);
                assertTrue("turn glyph escaped NOW", nowBounds.contains(turnBounds));
                assertTrue(turnBounds.width() > 0 && turnBounds.height() > 0);
                final float centre = nowBounds.exactCenterY();
                assertEquals(centre, textCentre(root, street), 1.0f);
                assertEquals(centre, textCentre(root, distance), 1.0f);
                assertEquals(centre, turnBounds.exactCenterY(), 1.0f);
                final StaticLayout atMinimum = atMinimumSize(street);
                if (atMinimum.getLineCount() > 3
                    && atMinimum.getHeight()
                           <= street.getHeight() - street.getCompoundPaddingTop() - street.getCompoundPaddingBottom())
                {
                  exercisedMoreThanThreeLines = true;
                  assertTrue("a three-line cap would hide this road name", street.getLineCount() > 3);
                }
              }
            }
          }
        }
        assertTrue("fixtures must exercise a road name needing more than three lines at the size floor",
                   exercisedMoreThanThreeLines);
      });
    }
  }

  private static StaticLayout atMinimumSize(TextView view)
  {
    final TextPaint paint = new TextPaint(view.getPaint());
    paint.setTextSize(view.getAutoSizeMinTextSize());
    return StaticLayout.Builder
        .obtain(view.getText(), 0, view.length(), paint,
                view.getWidth() - view.getCompoundPaddingLeft() - view.getCompoundPaddingRight())
        .setIncludePad(view.getIncludeFontPadding())
        .setLineSpacing(view.getLineSpacingExtra(), view.getLineSpacingMultiplier())
        .setBreakStrategy(view.getBreakStrategy())
        .setHyphenationFrequency(view.getHyphenationFrequency())
        .build();
  }

  private static void assertTextFits(TextView view)
  {
    final Layout layout = view.getLayout();
    assertNotNull(layout);
    assertTrue(layout.getLineCount() > 0);
    assertEquals("the complete instruction must be laid out", view.length(),
                 layout.getLineEnd(layout.getLineCount() - 1));
    assertTrue("instruction height exceeds its box",
               layout.getHeight() <= view.getHeight() - view.getCompoundPaddingTop() - view.getCompoundPaddingBottom());
    final int width = view.getWidth() - view.getCompoundPaddingLeft() - view.getCompoundPaddingRight();
    for (int line = 0; line < layout.getLineCount(); line++)
    {
      assertEquals(0, layout.getEllipsisCount(line));
      assertTrue("instruction extends outside its width",
                 layout.getLineLeft(line) >= -1.0f && layout.getLineRight(line) <= width + 1.0f);
    }
  }

  private static float textCentre(ViewGroup root, TextView view)
  {
    return bounds(root, view).top + view.getTotalPaddingTop() + view.getLayout().getHeight() / 2.0f;
  }

  private static Rect bounds(ViewGroup root, View view)
  {
    final Rect rect = new Rect(0, 0, view.getWidth(), view.getHeight());
    root.offsetDescendantRectToMyCoords(view, rect);
    return rect;
  }

  private static int px(Context context, int dp)
  {
    return Math.round(dp * context.getResources().getDisplayMetrics().density);
  }

  private static void measure(View view, int width, int height)
  {
    for (int pass = 0; pass < 3; pass++)
    {
      view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                   View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
      view.layout(0, 0, width, height);
    }
  }

  private static RoadShieldInfo shieldInfo(int end)
  {
    try
    {
      final Constructor<RoadShield> shield =
          RoadShield.class.getDeclaredConstructor(RoadShieldType.class, String.class, String.class);
      shield.setAccessible(true);
      final Constructor<RoadShieldInfo> info =
          RoadShieldInfo.class.getDeclaredConstructor(RoadShield[].class, int.class, int.class, int.class, int.class);
      info.setAccessible(true);
      return info.newInstance(new RoadShield[] {shield.newInstance(RoadShieldType.GenericWhite, "A25", null)}, 0, end,
                              0, 0);
    }
    catch (ReflectiveOperationException e)
    {
      throw new AssertionError(e);
    }
  }
}
