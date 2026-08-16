package app.organicmaps.incar;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class InCarDestinationSearchPolicyTest
{
  @Test
  public void queryStateDistinguishesIdleShortAndSearchableInput()
  {
    assertEquals(InCarDestinationSearchPolicy.UiState.IDLE, InCarDestinationSearchPolicy.stateForQuery(null));
    assertEquals(InCarDestinationSearchPolicy.UiState.IDLE, InCarDestinationSearchPolicy.stateForQuery("   "));
    assertEquals(InCarDestinationSearchPolicy.UiState.QUERY_TOO_SHORT, InCarDestinationSearchPolicy.stateForQuery("a"));
    assertEquals(InCarDestinationSearchPolicy.UiState.SEARCHING, InCarDestinationSearchPolicy.stateForQuery("ab"));
    assertEquals(InCarDestinationSearchPolicy.UiState.SEARCHING,
                 InCarDestinationSearchPolicy.stateForQuery(" Canberra "));
  }

  @Test
  public void completedSearchDistinguishesEmptyAndResultStates()
  {
    assertEquals(InCarDestinationSearchPolicy.UiState.EMPTY, InCarDestinationSearchPolicy.stateForCompletedResults(0));
    assertEquals(InCarDestinationSearchPolicy.UiState.EMPTY, InCarDestinationSearchPolicy.stateForCompletedResults(-1));
    assertEquals(InCarDestinationSearchPolicy.UiState.RESULTS,
                 InCarDestinationSearchPolicy.stateForCompletedResults(1));
  }
}
