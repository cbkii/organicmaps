#pragma once

#include <chrono>
#include <cmath>
#include <cstdint>

namespace routing
{
// Posted metadata only. This snapshot never supplies a position or route instruction.
struct CurrentRoadSpeedLimit
{
  using Clock = std::chrono::steady_clock;
  static constexpr auto kMaxAge = std::chrono::seconds(5);

  double m_speedLimitMps = -1.0;  // Unknown < 0; explicitly unrestricted = 0.
  double m_observationMonotonicSeconds = 0.0;
  uint64_t m_roadToken = 0;
  Clock::time_point m_observedAt;

  bool IsFresh(Clock::time_point now) const
  {
    return std::isfinite(m_speedLimitMps) && m_speedLimitMps >= 0.0 && m_roadToken != 0 &&
           std::isfinite(m_observationMonotonicSeconds) && m_observationMonotonicSeconds > 0.0 &&
           now >= m_observedAt && now - m_observedAt < kMaxAge;
  }
};
}  // namespace routing
