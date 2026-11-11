#include <gtest/gtest.h>

#include "coding/reader.hpp"
#include "coding/writer.hpp"
#include "indexer/feature_meta.hpp"
#include "timezone/serdes.hpp"
#include "timezone/timezone.hpp"

using namespace om::tz;

TEST(TimeZoneInMetadata, ShouldCorrectlyStoreAndLoadTimeZone)
{
  TimeZone const tz{.generation_year_offset = 0,
                    .base_offset = 68,  // UTC+1
                    .dst_delta = 60,    // DST +1h
                    .transitions = {
                        Transition{.day_delta = 88, .minute_of_day = 60},
                        Transition{.day_delta = 154, .minute_of_day = 60},
                        Transition{.day_delta = 210, .minute_of_day = 120},
                        Transition{.day_delta = 234, .minute_of_day = 120},
                    }};

  feature::RegionData rd;
  auto const result = Serialize(tz);
  EXPECT_TRUE(result.has_value());
  rd.Set(feature::RegionData::RD_TIMEZONE, result.value());
  EXPECT_EQ(tz, Deserialize(rd.Get(feature::RegionData::RD_TIMEZONE)).value());

  std::vector<uint8_t> buf;

  // Serializing RegionMeta
  MemWriter writer(buf);
  rd.Serialize(writer);

  // Deserializing RegionMeta
  MemReader const reader(buf.data(), buf.size());
  ReaderSource src(reader);
  feature::RegionData rd2;
  rd2.Deserialize(src);
  EXPECT_EQ(tz, Deserialize(rd2.Get(feature::RegionData::RD_TIMEZONE)).value());
  ASSERT_TRUE(rd2.GetTimeZone().has_value());

  // Reusing the receiver for an old map must not keep the previous map's zone.
  buf.clear();
  feature::RegionData legacy;
  MemWriter legacyWriter(buf);
  legacy.Serialize(legacyWriter);
  MemReader const legacyReader(buf.data(), buf.size());
  ReaderSource legacySource(legacyReader);
  rd2.Deserialize(legacySource);
  EXPECT_FALSE(rd2.GetTimeZone().has_value());
}

TEST(TimeZoneInMetadata, MalformedDataUsesSystemFallback)
{
  feature::RegionData malformed;
  malformed.Set(feature::RegionData::RD_TIMEZONE, "x");
  std::vector<uint8_t> buf;
  MemWriter writer(buf);
  malformed.Serialize(writer);
  MemReader const reader(buf.data(), buf.size());
  ReaderSource source(reader);
  feature::RegionData loaded;
  loaded.Deserialize(source);
  EXPECT_FALSE(loaded.GetTimeZone().has_value());
}
