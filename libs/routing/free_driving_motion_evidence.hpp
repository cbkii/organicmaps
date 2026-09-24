#pragma once

#include "platform/location.hpp"

#include "geometry/mercator.hpp"
#include "geometry/point2d.hpp"

#include <algorithm>
#include <cmath>
#include <cstddef>
#include <deque>

namespace routing::free_driving_snap
{
double constexpr kMotionEvidenceWindowSeconds = 4.0;
size_t constexpr kMotionEvidenceMaxSamples = 8;

struct MotionEvidence
{
  m2::PointD m_direction;
  double m_confidence = 0.0;
  double m_pathLengthM = 0.0;
  double m_displacementM = 0.0;
  double m_coherence = 0.0;
  size_t m_sampleCount = 0;

  bool HasDirection() const { return m_confidence > 0.0 && !m_direction.IsAlmostZero(); }
};

// Uses only real low-speed provider observations; extrapolated display ticks never enter this estimator.
// The owner resets it outside the low-speed regime so fast-road history cannot bias a later crawl.
// Provider speed remains the speed authority; this trajectory supplies independent direction/confidence.
// Short coherent trajectories are more useful than instantaneous GNSS bearing while a car is
// crawling, but noisy/stationary fixes deliberately produce no direction evidence.
class LowSpeedMotionEstimator
{
public:
  MotionEvidence Push(location::GpsInfo const & info, m2::PointD const & point)
  {
    double const timestamp = ObservationTimestamp(info);
    if (timestamp <= 0.0)
      return {};

    if (!m_samples.empty() &&
        (timestamp <= m_samples.back().m_timestamp || timestamp - m_samples.back().m_timestamp > 5.0))
    {
      m_samples.clear();
    }

    m_samples.push_back({point, timestamp, info.m_horizontalAccuracy});
    while (m_samples.size() > kMotionEvidenceMaxSamples ||
           (!m_samples.empty() && timestamp - m_samples.front().m_timestamp > kMotionEvidenceWindowSeconds))
    {
      m_samples.pop_front();
    }

    return BuildEvidence();
  }

  void Reset() { m_samples.clear(); }

  static double ObservationTimestamp(location::GpsInfo const & info)
  {
    return info.HasMonotonicTimestamp() ? info.m_monotonicTimestamp : info.m_timestamp;
  }

private:
  struct Sample
  {
    m2::PointD m_point;
    double m_timestamp = 0.0;
    double m_accuracyM = 100.0;
  };

  MotionEvidence BuildEvidence() const
  {
    MotionEvidence evidence;
    evidence.m_sampleCount = m_samples.size();
    if (m_samples.size() < 2)
      return evidence;

    for (size_t i = 1; i < m_samples.size(); ++i)
      evidence.m_pathLengthM += mercator::DistanceOnEarth(m_samples[i - 1].m_point, m_samples[i].m_point);

    evidence.m_displacementM = mercator::DistanceOnEarth(m_samples.front().m_point, m_samples.back().m_point);
    if (evidence.m_pathLengthM <= 0.5)
      return evidence;

    evidence.m_coherence = std::clamp(evidence.m_displacementM / evidence.m_pathLengthM, 0.0, 1.0);
    if (m_samples.size() < 3)
      return evidence;

    double const accuracyM = std::max(m_samples.front().m_accuracyM, m_samples.back().m_accuracyM);
    double const displacementFloorM = std::clamp(accuracyM * 0.35, 2.0, 6.0);
    if (evidence.m_displacementM < displacementFloorM || evidence.m_coherence < 0.55)
      return evidence;

    double const distanceConfidence =
        std::clamp((evidence.m_displacementM - displacementFloorM) / std::max(4.0, displacementFloorM), 0.0, 1.0);
    double const coherenceConfidence = std::clamp((evidence.m_coherence - 0.55) / 0.35, 0.0, 1.0);
    double const sampleConfidence = std::clamp((static_cast<double>(m_samples.size()) - 2.0) / 3.0, 0.0, 1.0);
    double const accuracyConfidence = accuracyM <= 15.0 ? 1.0 : (accuracyM <= 35.0 ? 0.65 : 0.35);

    evidence.m_confidence = std::clamp(
        (0.25 + 0.35 * distanceConfidence + 0.25 * coherenceConfidence + 0.15 * sampleConfidence) * accuracyConfidence,
        0.0, 1.0);
    evidence.m_direction = m_samples.back().m_point - m_samples.front().m_point;
    return evidence;
  }

  std::deque<Sample> m_samples;
};
}  // namespace routing::free_driving_snap
