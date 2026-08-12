package app.organicmaps.incar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import app.organicmaps.R;
import org.junit.Test;

public class InCarQuickCategoryPolicyTest
{
  @Test
  public void mapsActionsToOfflineSearchCategories()
  {
    assertEquals(R.string.in_car_quick_fuel_charging_category_query,
                 InCarQuickCategoryPolicy.searchTermRes(InCarQuickCategoryPolicy.Category.FUEL_CHARGING));
    assertEquals(R.string.category_parking,
                 InCarQuickCategoryPolicy.searchTermRes(InCarQuickCategoryPolicy.Category.PARKING));
    assertEquals(R.string.category_toilet,
                 InCarQuickCategoryPolicy.searchTermRes(InCarQuickCategoryPolicy.Category.TOILETS));
    assertEquals(R.string.category_eat, InCarQuickCategoryPolicy.searchTermRes(InCarQuickCategoryPolicy.Category.FOOD));
    assertEquals(R.string.in_car_quick_rest_water_category_query,
                 InCarQuickCategoryPolicy.searchTermRes(InCarQuickCategoryPolicy.Category.REST_WATER));
  }

  @Test
  public void unionQueriesUseCanonicalEnglishAliases()
  {
    assertTrue(InCarQuickCategoryPolicy.usesEnglishCanonicalQuery(InCarQuickCategoryPolicy.Category.FUEL_CHARGING));
    assertTrue(InCarQuickCategoryPolicy.usesEnglishCanonicalQuery(InCarQuickCategoryPolicy.Category.REST_WATER));
    assertFalse(InCarQuickCategoryPolicy.usesEnglishCanonicalQuery(InCarQuickCategoryPolicy.Category.PARKING));
    assertFalse(InCarQuickCategoryPolicy.usesEnglishCanonicalQuery(InCarQuickCategoryPolicy.Category.TOILETS));
    assertFalse(InCarQuickCategoryPolicy.usesEnglishCanonicalQuery(InCarQuickCategoryPolicy.Category.FOOD));
  }
}
