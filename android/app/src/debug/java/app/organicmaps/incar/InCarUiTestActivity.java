package app.organicmaps.incar;

import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

/** Empty debug-only host for instrumentation of production Views without starting navigation. */
public final class InCarUiTestActivity extends AppCompatActivity
{
  @Override
  protected void onCreate(@Nullable Bundle state)
  {
    super.onCreate(state);
    // Real injected taps must remain in the app-owned area on target-35+ edge-to-edge emulators.
    final android.view.View content = findViewById(android.R.id.content);
    androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(content, (view, insets) -> {
      final androidx.core.graphics.Insets safe = insets.getInsets(
          androidx.core.view.WindowInsetsCompat.Type.systemBars() | androidx.core.view.WindowInsetsCompat.Type.displayCutout());
      view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
      return insets;
    });
    androidx.core.view.ViewCompat.requestApplyInsets(content);
  }
}
