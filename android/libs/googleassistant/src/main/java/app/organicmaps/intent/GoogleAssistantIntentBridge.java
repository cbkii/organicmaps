package app.organicmaps.intent;

import android.content.Intent;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import app.organicmaps.api.GoogleAssistantSearchHandler;

/** Optional runtime bridge loaded reflectively by the app when this integration module is packaged. */
@Keep
public final class GoogleAssistantIntentBridge extends GoogleAssistantIntentHandler
{
  @Keep
  public boolean process(@NonNull Intent intent, @NonNull GoogleAssistantSearchHandler searchHandler)
  {
    return handleIntent(intent, searchHandler::handleSearch);
  }
}
