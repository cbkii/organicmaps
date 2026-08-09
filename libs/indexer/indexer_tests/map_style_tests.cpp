#include "indexer/map_style.hpp"

#include "testing/testing.hpp"

UNIT_TEST(MapStyle_InCarRoundTrip)
{
  TEST_EQUAL(MapStyleFromSettings("MapStyleInCarLight"), MapStyleInCarLight, ());
  TEST_EQUAL(MapStyleFromSettings("MapStyleInCarDark"), MapStyleInCarDark, ());
  TEST_EQUAL(MapStyleToString(MapStyleInCarLight), "MapStyleInCarLight", ());
  TEST_EQUAL(MapStyleToString(MapStyleInCarDark), "MapStyleInCarDark", ());
}

UNIT_TEST(MapStyle_InCarVariants)
{
  TEST(!MapStyleIsDark(MapStyleInCarLight), ());
  TEST(MapStyleIsDark(MapStyleInCarDark), ());
  TEST_EQUAL(GetDarkMapStyleVariant(MapStyleInCarLight), MapStyleInCarDark, ());
  TEST_EQUAL(GetLightMapStyleVariant(MapStyleInCarDark), MapStyleInCarLight, ());
}
