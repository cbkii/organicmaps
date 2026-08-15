package app.organicmaps.incar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Pure UI-state policy for the InCar Home/Work destination search picker. */
public final class InCarDestinationSearchPolicy
{
  public static final int MIN_QUERY_LENGTH = 2;

  public enum UiState
  {
    IDLE,
    QUERY_TOO_SHORT,
    SEARCHING,
    RESULTS,
    EMPTY
  }

  private InCarDestinationSearchPolicy() {}

  @NonNull
  public static UiState stateForQuery(@Nullable CharSequence query)
  {
    final String text = query == null ? "" : query.toString().trim();
    if (text.isEmpty())
      return UiState.IDLE;
    if (text.length() < MIN_QUERY_LENGTH)
      return UiState.QUERY_TOO_SHORT;
    return UiState.SEARCHING;
  }

  @NonNull
  public static UiState stateForCompletedResults(int resultCount)
  {
    return resultCount > 0 ? UiState.RESULTS : UiState.EMPTY;
  }
}
