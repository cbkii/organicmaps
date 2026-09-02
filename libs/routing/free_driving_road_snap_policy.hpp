#pragma once

#include "platform/location.hpp"

#include <algorithm>
#include <array>
#include <cstdint>
#include <string_view>

namespace routing::free_driving_snap
{
// Free-driving matching is a display-only aid for the direct-display InCar product. Raw GNSS remains
// authoritative for routing, search, track recording and other framework state.
enum class SnapMode : uint8_t
{
  Off = 0,
  Auto = 1,
  Strong = 2,
};

enum class MatchState : uint8_t
{
  Disabled = 0,
  Raw = 1,
  Matched = 2,
  OffRoadSuspected = 3,
};

size_t constexpr kCandidateLimit = 5;
size_t constexpr kHistoryMaxSamples = 8;
double constexpr kHistoryWindowSeconds = 4.0;
double constexpr kMinHistoryDisplacementM = 4.0;
double constexpr kMovingEvidenceSpeedMps = 2.0;
double constexpr kStationarySpeedMps = 2.0 / 3.6;
double constexpr kHighSpeedStrongPriorMps = 40.0 / 3.6;
double constexpr kOffRoadEvidenceSeconds = 10.0;
double constexpr kCatastrophicHorizontalAccuracyM = 250.0;
double constexpr kStationaryHoldSeconds = 30.0;

inline bool IsAccuracyCatastrophic(location::GpsInfo const & info)
{
  // Android's reported horizontal accuracy can be conservative or noisy on fixed head units. It is therefore
  // deliberately a weak signal. Only clearly unusable values disable matching outright.
  return info.m_horizontalAccuracy > kCatastrophicHorizontalAccuracyM;
}

inline double ProjectionRadiusM(location::GpsInfo const & info, SnapMode mode)
{
  if (mode == SnapMode::Off)
    return 0.0;

  double const speedMps = info.HasSpeed() ? std::max(0.0, info.m_speed) : 0.0;
  double baseM = 18.0;
  if (speedMps >= kHighSpeedStrongPriorMps)
    baseM = mode == SnapMode::Strong ? 70.0 : 55.0;
  else if (speedMps >= 20.0 / 3.6)
    baseM = mode == SnapMode::Strong ? 48.0 : 38.0;
  else if (speedMps >= 5.0 / 3.6)
    baseM = mode == SnapMode::Strong ? 36.0 : 28.0;
  else if (mode == SnapMode::Strong)
    baseM = 24.0;

  // Accuracy only expands the candidate search a little; it must not dominate coherent recent motion.
  double accuracyExtraM = 0.0;
  if (info.m_horizontalAccuracy > 10.0)
    accuracyExtraM = std::clamp((info.m_horizontalAccuracy - 10.0) * 0.20, 0.0, 12.0);
  return std::min(baseM + accuracyExtraM, mode == SnapMode::Strong ? 90.0 : 72.0);
}

inline double AcceptanceScore(SnapMode mode)
{
  return mode == SnapMode::Strong ? 9.0 : 7.0;
}

inline double HighSpeedSingleFixScore(SnapMode mode)
{
  return mode == SnapMode::Strong ? 7.0 : 5.2;
}

inline bool IsLeftHandTrafficCountry(std::string_view countryId)
{
  // Low-weight tie-break only. Organic Maps country ids for split regions retain their country prefix.
  // Keep the list intentionally explicit and harmless: an omission merely removes a small lateral preference.
  static constexpr std::array<std::string_view, 31> kLeftTrafficPrefixes = {
      "Australia",     "Bahamas",       "Bangladesh", "Barbados",     "Bhutan",      "Botswana",
      "Brunei",       "Cyprus",        "Fiji",       "Guyana",       "Hong Kong",   "India",
      "Indonesia",    "Ireland",       "Jamaica",    "Japan",        "Kenya",       "Lesotho",
      "Macau",        "Malaysia",      "Maldives",   "Malta",        "Mauritius",   "Mozambique",
      "Namibia",      "Nepal",         "New Zealand", "Singapore",    "South Africa", "Sri Lanka",
      "United Kingdom"};

  for (auto const prefix : kLeftTrafficPrefixes)
    if (countryId.starts_with(prefix))
      return true;
  return false;
}
}  // namespace routing::free_driving_snap
