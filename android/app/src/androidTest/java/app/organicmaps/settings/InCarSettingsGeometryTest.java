package app.organicmaps.settings;

import static org.junit.Assert.*;

import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.widget.SwitchCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import app.organicmaps.R;
import app.organicmaps.incar.InCarSettingsRows;
import app.organicmaps.incar.InCarUiTestActivity;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class InCarSettingsGeometryTest
{
  @Test
  public void preferenceRowsMeasureBeforeLayoutAndSurviveRecycling()
  {
    try (ActivityScenario<InCarUiTestActivity> scenario = ActivityScenario.launch(InCarUiTestActivity.class))
    {
      scenario.onActivity(activity -> {
        final PreferenceManager manager = new PreferenceManager(activity);
        final PreferenceScreen screen = manager.createPreferenceScreen(activity);
        final PreferenceCategory heading = new PreferenceCategory(activity);
        heading.setTitle("Category");
        screen.addPreference(heading);
        for (int i = 0; i < 16; i++)
        {
          final Preference preference = new Preference(activity);
          preference.setKey("row" + i);
          preference.setTitle("Row " + i);
          if (i == 0)
            preference.setSummary("Long translated content ".repeat(24));
          if (i == 1)
            preference.setEnabled(false);
          screen.addPreference(preference);
        }
        final RecyclerView list = new RecyclerView(activity);
        list.setLayoutManager(new LinearLayoutManager(activity));
        final BaseXmlSettingsFragment fragment = new BaseXmlSettingsFragment() {
          @Override
          protected int getXmlResources()
          {
            return R.xml.prefs_in_car;
          }
        };
        list.setAdapter(fragment.onCreateAdapter(screen));
        activity.setContentView(list);
        final int target = activity.getResources().getDimensionPixelSize(R.dimen.in_car_runtime_row_min_height);
        measure(list, target * 6, target * 5);
        final View title = list.findViewHolderForAdapterPosition(0).itemView;
        assertTrue(title.getMinimumHeight() < target);
        final View longRow = list.findViewHolderForAdapterPosition(1).itemView;
        assertTrue(longRow.getHeight() > target);
        list.scrollToPosition(10);
        measure(list, target * 6, target * 5);
        for (int i = 0; i < list.getChildCount(); i++)
          assertTrue(list.getChildAt(i).getHeight() >= target);
        list.scrollToPosition(2);
        measure(list, target * 6, target * 5);
        assertTrue(list.findViewHolderForAdapterPosition(2).itemView.getHeight() >= target);
      });
    }
  }

  @Test
  public void actualPreferenceKeysMatchRealTapBoundsAndDisabledRowsStayInert()
  {
    final java.util.concurrent.atomic.AtomicReference<String> selected =
        new java.util.concurrent.atomic.AtomicReference<>();
    final int[][] bounds = new int[4][4];
    try (ActivityScenario<InCarUiTestActivity> scenario = ActivityScenario.launch(InCarUiTestActivity.class))
    {
      scenario.onActivity(activity -> {
        final PreferenceScreen screen = new PreferenceManager(activity).createPreferenceScreen(activity);
        for (int i = 0; i < 4; i++)
        {
          final Preference preference = i == 0 ? new PreferenceCategory(activity) : new Preference(activity);
          final String key = "row" + i;
          preference.setKey(key);
          preference.setTitle(key);
          preference.setEnabled(i != 2);
          preference.setOnPreferenceClickListener(item -> {
            selected.set(item.getKey());
            return true;
          });
          screen.addPreference(preference);
        }
        final RecyclerView list = new RecyclerView(activity);
        list.setId(android.R.id.list);
        list.setLayoutManager(new LinearLayoutManager(activity));
        final BaseXmlSettingsFragment fragment = new BaseXmlSettingsFragment() {
          @Override
          protected int getXmlResources()
          {
            return R.xml.prefs_in_car;
          }
        };
        list.setAdapter(fragment.onCreateAdapter(screen));
        activity.setContentView(list);
      });
      InstrumentationRegistry.getInstrumentation().waitForIdleSync();
      scenario.onActivity(activity -> {
        final RecyclerView list = activity.findViewById(android.R.id.list);
        for (int i = 0; i < 4; i++)
        {
          final View row = list.findViewHolderForAdapterPosition(i).itemView;
          final android.graphics.Rect visible = new android.graphics.Rect();
          assertTrue(row.getGlobalVisibleRect(visible));
          bounds[i] = new int[] {visible.left, visible.top, visible.width(), visible.height()};
        }
      });
      for (int i = 0; i < 4; i++)
      {
        final int[] row = bounds[i];
        for (float y : new float[] {2, row[3] / 2f, row[3] - 2})
        {
          for (float x : new float[] {2, row[2] / 2f, row[2] - 2})
          {
            selected.set(null);
            tap(row[0] + x, row[1] + y);
            assertEquals(i == 0 || i == 2 ? null : "row" + i, selected.get());
          }
        }
      }
    }
  }

  @Test
  public void wholeRowsToggleExactlyOnceAtEveryVisibleEdge()
  {
    final AtomicInteger changes = new AtomicInteger();
    final int[][] bounds = new int[2][4];
    try (ActivityScenario<InCarUiTestActivity> scenario = ActivityScenario.launch(InCarUiTestActivity.class))
    {
      scenario.onActivity(activity -> {
        final LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < 2; i++)
        {
          final LinearLayout row = new LinearLayout(activity);
          row.setOrientation(LinearLayout.HORIZONTAL);
          final TextView label = new TextView(activity);
          label.setText("Switch " + i);
          row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
          final SwitchCompat toggle = new SwitchCompat(activity);
          row.addView(toggle);
          toggle.setOnCheckedChangeListener((button, checked) -> changes.incrementAndGet());
          content.addView(row);
          InCarSettingsRows.bindToggle(toggle, label.getText());
        }
        activity.setContentView(content);
      });
      InstrumentationRegistry.getInstrumentation().waitForIdleSync();
      scenario.onActivity(activity -> {
        final LinearLayout content =
            (LinearLayout) ((ViewGroup) activity.findViewById(android.R.id.content)).getChildAt(0);
        for (int i = 0; i < 2; i++)
        {
          final View row = content.getChildAt(i);
          final int[] xy = new int[2];
          row.getLocationOnScreen(xy);
          bounds[i] = new int[] {xy[0], xy[1], row.getWidth(), row.getHeight()};
        }
      });
      int expected = 0;
      for (int[] row : bounds)
      {
        for (float y : new float[] {2, row[3] / 2f, row[3] - 2})
        {
          for (float x : new float[] {2, row[2] / 2f, row[2] - 2})
          {
            tap(row[0] + x, row[1] + y);
            assertEquals(++expected, changes.get());
          }
        }
      }
    }
  }

  @Test
  @SuppressWarnings("deprecation")
  public void choiceDialogsPreserveCancellationRecreationAndChangeCallbacks()
  {
    DialogOwnerFragment.changes.set(0);
    try (ActivityScenario<InCarUiTestActivity> scenario = ActivityScenario.launch(InCarUiTestActivity.class))
    {
      scenario.onActivity(activity -> {
        final DialogOwnerFragment owner = new DialogOwnerFragment();
        activity.getSupportFragmentManager().beginTransaction().add(owner, "owner").commitNow();
        final InCarListPreferenceDialogFragment dialog = InCarListPreferenceDialogFragment.newInstance("choice");
        dialog.setTargetFragment(owner, 0);
        dialog.showNow(activity.getSupportFragmentManager(), "choiceDialog");
      });
      scenario.recreate();
      scenario.onActivity(activity -> {
        final InCarListPreferenceDialogFragment fragment =
            (InCarListPreferenceDialogFragment) activity.getSupportFragmentManager().findFragmentByTag("choiceDialog");
        assertNotNull(fragment);
        fragment.requireDialog().cancel();
      });
      InstrumentationRegistry.getInstrumentation().waitForIdleSync();
      scenario.onActivity(activity -> {
        final DialogOwnerFragment owner =
            (DialogOwnerFragment) activity.getSupportFragmentManager().findFragmentByTag("owner");
        final androidx.preference.ListPreference preference = owner.findPreference("choice");
        assertEquals("light", preference.getValue());
        assertEquals(0, DialogOwnerFragment.changes.get());
        final InCarListPreferenceDialogFragment fragment = InCarListPreferenceDialogFragment.newInstance("choice");
        fragment.setTargetFragment(owner, 0);
        fragment.showNow(activity.getSupportFragmentManager(), "choiceDialog");
        final android.widget.ListView list =
            ((androidx.appcompat.app.AlertDialog) fragment.requireDialog()).getListView();
        list.performItemClick(list.getChildAt(1), 1, list.getAdapter().getItemId(1));
      });
      InstrumentationRegistry.getInstrumentation().waitForIdleSync();
      scenario.onActivity(activity -> {
        final DialogOwnerFragment owner =
            (DialogOwnerFragment) activity.getSupportFragmentManager().findFragmentByTag("owner");
        final androidx.preference.ListPreference preference = owner.findPreference("choice");
        assertEquals("dark", preference.getValue());
        assertEquals(1, DialogOwnerFragment.changes.get());
      });
    }
  }

  public static class DialogOwnerFragment extends androidx.preference.PreferenceFragmentCompat
  {
    static final AtomicInteger changes = new AtomicInteger();

    @Override
    public void onCreatePreferences(android.os.Bundle state, String rootKey)
    {
      final PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(requireContext());
      final androidx.preference.ListPreference preference = new androidx.preference.ListPreference(requireContext());
      preference.setKey("choice");
      preference.setPersistent(false);
      preference.setEntries(new String[] {"Light", "Dark"});
      preference.setEntryValues(new String[] {"light", "dark"});
      preference.setValue("light");
      preference.setOnPreferenceChangeListener((item, value) -> {
        changes.incrementAndGet();
        return true;
      });
      screen.addPreference(preference);
      setPreferenceScreen(screen);
    }
  }

  private static void measure(View view, int width, int height)
  {
    view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                 View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
    view.layout(0, 0, width, height);
  }

  public static void tap(float x, float y)
  {
    final android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    final long now = android.os.SystemClock.uptimeMillis();
    final android.view.MotionEvent down =
        android.view.MotionEvent.obtain(now, now, android.view.MotionEvent.ACTION_DOWN, x, y, 0);
    android.view.MotionEvent up = null;
    try
    {
      instrumentation.sendPointerSync(down);
      android.os.SystemClock.sleep(20);
      up = android.view.MotionEvent.obtain(now, android.os.SystemClock.uptimeMillis(),
                                          android.view.MotionEvent.ACTION_UP, x, y, 0);
      instrumentation.sendPointerSync(up);
      instrumentation.waitForIdleSync();
    }
    finally
    {
      down.recycle();
      if (up != null)
        up.recycle();
    }
  }
}
