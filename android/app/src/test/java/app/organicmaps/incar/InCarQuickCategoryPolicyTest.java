package app.organicmaps.incar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import app.organicmaps.R;
import org.junit.Test;

public class InCarQuickCategoryPolicyTest
{
  @Test
  public void mapsActionsToEstablishedOfflineSearchCategories()
  {
    assertEquals(R.string.category_fuel,
                 InCarQuickCategoryPolicy.searchTermRes(InCarQuickCategoryPolicy.Category.FUEL));
    assertEquals(R.string.in_car_quick_charging_category_query,
                 InCarQuickCategoryPolicy.searchTermRes(InCarQuickCategoryPolicy.Category.CHARGING));
    assertEquals(R.string.category_parking,
                 InCarQuickCategoryPolicy.searchTermRes(InCarQuickCategoryPolicy.Category.PARKING));
    assertEquals(R.string.category_toilet,
                 InCarQuickCategoryPolicy.searchTermRes(InCarQuickCategoryPolicy.Category.TOILETS));
    assertEquals(R.string.category_eat, InCarQuickCategoryPolicy.searchTermRes(InCarQuickCategoryPolicy.Category.FOOD));
  }

  @Test
  public void onlyEvChargingUsesCanonicalEnglishAlias()
  {
    assertTrue(InCarQuickCategoryPolicy.usesEnglishCanonicalQuery(InCarQuickCategoryPolicy.Category.CHARGING));
    assertFalse(InCarQuickCategoryPolicy.usesEnglishCanonicalQuery(InCarQuickCategoryPolicy.Category.FUEL));
    assertFalse(InCarQuickCategoryPolicy.usesEnglishCanonicalQuery(InCarQuickCategoryPolicy.Category.PARKING));
    assertFalse(InCarQuickCategoryPolicy.usesEnglishCanonicalQuery(InCarQuickCategoryPolicy.Category.TOILETS));
    assertFalse(InCarQuickCategoryPolicy.usesEnglishCanonicalQuery(InCarQuickCategoryPolicy.Category.FOOD));
  }
}
