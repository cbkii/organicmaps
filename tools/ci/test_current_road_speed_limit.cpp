#include "routing/current_road_speed_limit.hpp"

#include <cassert>
#include <limits>

int main()
{
  using Info = routing::CurrentRoadSpeedLimit;
  auto const observed = Info::Clock::now();
  Info info{50.0 / 3.6, 1.0, 42, observed};
  assert(info.IsFresh(observed));
  assert(info.IsFresh(observed + Info::kMaxAge - std::chrono::nanoseconds(1)));
  assert(!info.IsFresh(observed + Info::kMaxAge));
  assert(!info.IsFresh(observed - std::chrono::nanoseconds(1)));
  info.m_speedLimitMps = 0.0;  // Explicit unrestricted remains valid metadata.
  assert(info.IsFresh(observed));
  for (auto const invalid : {-1.0, std::numeric_limits<double>::infinity(), std::numeric_limits<double>::quiet_NaN()})
  {
    info.m_speedLimitMps = invalid;
    assert(!info.IsFresh(observed));
  }
  info.m_speedLimitMps = 20.0;
  info.m_roadToken = 0;
  assert(!info.IsFresh(observed));
  info.m_roadToken = 42;
  info.m_observationMonotonicSeconds = 0;
  assert(!info.IsFresh(observed));
  info = {};  // Provider, road or lifecycle invalidation discards the entire observation.
  assert(!info.IsFresh(observed));
}
