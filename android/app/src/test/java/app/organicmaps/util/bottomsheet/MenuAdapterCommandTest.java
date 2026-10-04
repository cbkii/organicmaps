package app.organicmaps.util.bottomsheet;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.view.View;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.widget.SwitchCompat;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

public class MenuAdapterCommandTest
{
  @Test
  public void observedBindingAndRapidRowSwitchRebindDispatchOnce()
  {
    for (boolean observed : new boolean[] {false, true})
    {
      AtomicInteger commands = new AtomicInteger();
      AtomicInteger dismissals = new AtomicInteger();
      ArrayList<MenuBottomSheetItem> items = new ArrayList<>();
      items.add(MenuBottomSheetItem.checkable(1, 2, observed, commands::incrementAndGet));
      MenuAdapter adapter = new MenuAdapter(items, dismissals::incrementAndGet);
      MenuAdapter.ViewHolder holder = mock(MenuAdapter.ViewHolder.class);
      SwitchCompat toggle = mock(SwitchCompat.class);
      LinearLayout row = mock(LinearLayout.class);
      TextView title = mock(TextView.class);
      when(holder.getToggle()).thenReturn(toggle);
      when(holder.getContainer()).thenReturn(row);
      when(holder.getIconImageView()).thenReturn(mock(ImageView.class));
      when(holder.getTitleTextView()).thenReturn(title);
      when(holder.getBadgeTextView()).thenReturn(mock(TextView.class));
      when(title.getText()).thenReturn("Track Recording");
      AtomicBoolean displayed = new AtomicBoolean(!observed);
      AtomicReference<CompoundButton.OnCheckedChangeListener> change = new AtomicReference<>();
      AtomicReference<View.OnClickListener> click = new AtomicReference<>();
      doAnswer(invocation -> {
        change.set(invocation.getArgument(0));
        return null;
      }).when(toggle).setOnCheckedChangeListener(any());
      when(toggle.isChecked()).thenAnswer(invocation -> displayed.get());
      doAnswer(invocation -> {
        boolean checked = invocation.getArgument(0);
        boolean old = displayed.getAndSet(checked);
        if (old != checked && change.get() != null)
          change.get().onCheckedChanged(toggle, checked);
        return null;
      }).when(toggle).setChecked(anyBoolean());
      doAnswer(invocation -> {
        click.set(invocation.getArgument(0));
        return null;
      }).when(row).setOnClickListener(any());
      // A recycled holder carries a previous listener that must be detached before checked is applied.
      change.set((button, checked) -> commands.incrementAndGet());
      adapter.onBindViewHolder(holder, 0);
      assertEquals(0, commands.get());
      click.get().onClick(row);
      toggle.setChecked(!toggle.isChecked());
      toggle.setChecked(!toggle.isChecked());
      adapter.onBindViewHolder(holder, 0);
      click.get().onClick(row);
      assertEquals(1, commands.get());
      assertEquals(1, dismissals.get());
      assertEquals(observed, items.get(0).checked);
    }
  }
}
