#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def replace_once(path: str, old: str, new: str) -> None:
    target = ROOT / path
    text = target.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{path}: expected one match, found {count}\n--- needle ---\n{old}")
    target.write_text(text.replace(old, new, 1), encoding="utf-8")


def append_before(path: str, marker: str, addition: str) -> None:
    replace_once(path, marker, addition + marker)


motion_header = r'''#pragma once

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

// Uses only real provider observations. Short coherent trajectories are more useful than
// instantaneous GNSS bearing while a car is crawling, but noisy/stationary fixes deliberately
// produce no direction evidence.
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

    evidence.m_confidence =
        std::clamp((0.25 + 0.35 * distanceConfidence + 0.25 * coherenceConfidence + 0.15 * sampleConfidence) *
                       accuracyConfidence,
                   0.0, 1.0);
    evidence.m_direction = m_samples.back().m_point - m_samples.front().m_point;
    return evidence;
  }

  std::deque<Sample> m_samples;
};
}  // namespace routing::free_driving_snap
'''
(ROOT / "libs/routing/free_driving_motion_evidence.hpp").write_text(motion_header, encoding="utf-8")

# Android/provider evidence: keep wall time for persisted state, add monotonic time and uncertainty.
replace_once(
    "libs/platform/location.hpp",
    '''  /// @TODO(bykoianko) |m_timestamp| is calculated based on platform methods which don't
  /// guarantee that |m_timestamp| is monotonic. |m_monotonicTimeMs| should be added to
  /// class |GpsInfo|. This time should be calculated based on Location::getElapsedRealtimeNanos()
  /// method in case of Android. How to calculate such time in case of iOS should be
  /// investigated.
  /// \\note For most cases |m_timestamp| is monotonic.
  double m_timestamp = 0.0;             //!< seconds from 1st Jan 1970
  double m_latitude = 0.0;              //!< degrees
  double m_longitude = 0.0;             //!< degrees
  double m_horizontalAccuracy = 100.0;  //!< metres
  double m_altitude = 0.0;              //!< metres
  double m_verticalAccuracy = -1.0;     //!< metres
  double m_bearing = -1.0;              //!< positive degrees from the true North
  double m_speed = -1.0;                //!< metres per second

  bool IsValid() const { return m_source != EUndefined; }
  bool HasBearing() const { return m_bearing >= 0.0; }
  bool HasSpeed() const { return m_speed >= 0.0; }
  bool HasAltitude() const { return m_verticalAccuracy >= 0.0; }
''',
    '''  // Wall time is retained for persisted state. m_monotonicTimestamp is preferred for
  // short-lived motion evidence when a provider supplies it (Android elapsedRealtimeNanos()).
  double m_timestamp = 0.0;              //!< seconds from 1st Jan 1970
  double m_monotonicTimestamp = -1.0;    //!< seconds from a monotonic platform clock
  double m_latitude = 0.0;               //!< degrees
  double m_longitude = 0.0;              //!< degrees
  double m_horizontalAccuracy = 100.0;   //!< metres
  double m_altitude = 0.0;               //!< metres
  double m_verticalAccuracy = -1.0;      //!< metres
  double m_bearing = -1.0;               //!< positive degrees from the true North
  double m_bearingAccuracy = -1.0;       //!< degrees
  double m_speed = -1.0;                 //!< metres per second
  double m_speedAccuracy = -1.0;         //!< metres per second

  bool IsValid() const { return m_source != EUndefined; }
  bool HasMonotonicTimestamp() const { return m_monotonicTimestamp > 0.0; }
  bool HasBearing() const { return m_bearing >= 0.0; }
  bool HasBearingAccuracy() const { return m_bearingAccuracy >= 0.0; }
  bool HasSpeed() const { return m_speed >= 0.0; }
  bool HasSpeedAccuracy() const { return m_speedAccuracy >= 0.0; }
  bool HasAltitude() const { return m_verticalAccuracy >= 0.0; }
''')

replace_once(
    "android/sdk/src/main/java/app/organicmaps/sdk/location/LocationState.java",
    '''  static native void nativeLocationUpdated(long time, double lat, double lon, float accuracyH, double altitude,
                                           float accuracyV, float speed, float bearing);
''',
    '''  static native void nativeLocationUpdated(long time, long monotonicTimeNanos, double lat, double lon,
                                           float accuracyH, double altitude, float accuracyV, float speed,
                                           float speedAccuracy, float bearing, float bearingAccuracy);
''')

replace_once(
    "android/sdk/src/main/java/app/organicmaps/sdk/location/LocationHelper.java",
    '''    final LocationCompatExtractor.Altitude altitude = LocationCompatExtractor.getAltitude(mSavedLocation);
    LocationState.nativeLocationUpdated(
        mSavedLocation.getTime(), mSavedLocation.getLatitude(), mSavedLocation.getLongitude(),
        mSavedLocation.getAccuracy(), altitude != null ? altitude.altitude() : 0,
        altitude != null ? altitude.accuracy() : -1, mSavedLocation.hasSpeed() ? mSavedLocation.getSpeed() : -1,
        mSavedLocation.hasBearing() ? mSavedLocation.getBearing() : -1);
''',
    '''    final LocationCompatExtractor.Altitude altitude = LocationCompatExtractor.getAltitude(mSavedLocation);
    final LocationCompatExtractor.Speed speed = LocationCompatExtractor.getSpeed(mSavedLocation);
    final LocationCompatExtractor.Bearing bearing = LocationCompatExtractor.getBearing(mSavedLocation);
    LocationState.nativeLocationUpdated(
        mSavedLocation.getTime(), mSavedLocation.getElapsedRealtimeNanos(), mSavedLocation.getLatitude(),
        mSavedLocation.getLongitude(), mSavedLocation.getAccuracy(), altitude != null ? altitude.altitude() : 0,
        altitude != null ? altitude.accuracy() : -1, speed != null ? speed.speed() : -1,
        speed != null ? speed.accuracy() : -1, bearing != null ? bearing.bearing() : -1,
        bearing != null ? bearing.accuracy() : -1);
''')

replace_once(
    "android/sdk/src/main/cpp/app/organicmaps/sdk/LocationState.cpp",
    '''JNIEXPORT void Java_app_organicmaps_sdk_location_LocationState_nativeLocationUpdated(JNIEnv * env, jclass clazz,
                                                                                     jlong time, jdouble lat,
                                                                                     jdouble lon, jfloat accuracyH,
                                                                                     jdouble altitude, jfloat accuracyV,
                                                                                     jfloat speed, jfloat bearing)
''',
    '''JNIEXPORT void Java_app_organicmaps_sdk_location_LocationState_nativeLocationUpdated(
    JNIEnv * env, jclass clazz, jlong time, jlong monotonicTimeNanos, jdouble lat, jdouble lon, jfloat accuracyH,
    jdouble altitude, jfloat accuracyV, jfloat speed, jfloat speedAccuracy, jfloat bearing, jfloat bearingAccuracy)
''')
replace_once(
    "android/sdk/src/main/cpp/app/organicmaps/sdk/LocationState.cpp",
    '''  info.m_timestamp = static_cast<double>(time) / 1000.0;
  info.m_latitude = lat;
''',
    '''  info.m_timestamp = static_cast<double>(time) / 1000.0;
  if (monotonicTimeNanos > 0)
    info.m_monotonicTimestamp = static_cast<double>(monotonicTimeNanos) / 1.0e9;
  info.m_latitude = lat;
''')
replace_once(
    "android/sdk/src/main/cpp/app/organicmaps/sdk/LocationState.cpp",
    '''  if (bearing >= 0)
    info.m_bearing = bearing;

  if (speed >= 0)
    info.m_speed = speed;
''',
    '''  if (bearing >= 0)
    info.m_bearing = bearing;
  if (bearingAccuracy >= 0)
    info.m_bearingAccuracy = bearingAccuracy;

  if (speed >= 0)
    info.m_speed = speed;
  if (speedAccuracy >= 0)
    info.m_speedAccuracy = speedAccuracy;
''')

# Matcher policy: richer parking context, uncertainty-aware motion weighting and low-speed progress.
replace_once(
    "libs/routing/free_driving_road_snap_policy.hpp",
    '''struct AreaContext
{
  bool m_insideParking = false;
  bool m_insideStructuredParking = false;
  bool m_nearParkingEntrance = false;
  bool m_insideLargeBuilding = false;
  bool m_insideStrongOpenArea = false;
  bool m_insideWeakOpenArea = false;

  // When the current fix is in a mapped parking/building area this stores that area's
  // triangulation. ParkingFree display projection can therefore keep the actual measured
  // position (or its nearest in-area point) rather than collapsing to an area centre.
  std::vector<m2::PointD> m_freeAreaTriangles;

  bool HasParkingArea() const { return m_insideParking || m_insideStructuredParking; }
  bool HasFreeAreaGeometry() const { return !m_freeAreaTriangles.empty(); }
};
''',
    '''struct AreaContext
{
  bool m_insideParking = false;
  bool m_insideStructuredParking = false;
  bool m_nearParkingArea = false;
  bool m_nearParkingEntrance = false;
  bool m_nearParkingSpace = false;
  bool m_insideStreetSideParking = false;
  bool m_insideParkingLane = false;
  bool m_insideLargeBuilding = false;
  bool m_insideStrongOpenArea = false;
  bool m_insideWeakOpenArea = false;
  double m_nearestParkingAreaDistanceM = std::numeric_limits<double>::max();

  // Free-area geometry is used only after ParkingFree has been entered. Nearby parking geometry
  // is separately retained so bounded road candidates can be classified as inside/outside a
  // facility without turning parking proximity itself into snap authority.
  std::vector<m2::PointD> m_freeAreaTriangles;
  std::vector<m2::PointD> m_nearbyParkingAreaTriangles;

  bool HasParkingArea() const { return m_insideParking || m_insideStructuredParking; }
  bool HasParkingSignals() const
  {
    return HasParkingArea() || m_nearParkingArea || m_nearParkingEntrance || m_nearParkingSpace ||
           m_insideStreetSideParking || m_insideParkingLane || m_insideLargeBuilding;
  }
  bool SuppressesParkingFree() const { return m_insideStreetSideParking || m_insideParkingLane; }
  bool HasFreeAreaGeometry() const { return !m_freeAreaTriangles.empty(); }
};
''')
replace_once(
    "libs/routing/free_driving_road_snap_policy.hpp",
    '''inline double EffectiveSpeedMps(location::GpsInfo const & info, double rawStepM, double deltaSeconds)
{
  if (info.HasSpeed())
    return std::max(0.0, info.m_speed);
  if (deltaSeconds <= 0.0 || rawStepM < 0.0 || rawStepM > 80.0)
    return 0.0;
  return rawStepM / deltaSeconds;
}
''',
    '''inline double EffectiveSpeedMps(location::GpsInfo const & info, double rawStepM, double deltaSeconds)
{
  if (info.HasSpeed())
  {
    double const providerSpeed = std::max(0.0, info.m_speed);
    if (info.HasSpeedAccuracy() && deltaSeconds > 0.0 && rawStepM >= 0.0)
    {
      double const plausibleRawLimitM = std::max(20.0, providerSpeed * deltaSeconds * 4.0 + 10.0);
      if (rawStepM <= plausibleRawLimitM)
      {
        double const displacementSpeed = rawStepM / deltaSeconds;
        double const providerWeight = std::clamp(1.0 - info.m_speedAccuracy / 5.0, 0.25, 1.0);
        return providerWeight * providerSpeed + (1.0 - providerWeight) * displacementSpeed;
      }
    }
    return providerSpeed;
  }
  if (deltaSeconds <= 0.0 || rawStepM < 0.0 || rawStepM > 80.0)
    return 0.0;
  return rawStepM / deltaSeconds;
}
''')
append_before(
    "libs/routing/free_driving_road_snap_policy.hpp",
    '''inline double HeadingAgreementFactor(double disagreementDegrees)
''',
    '''inline double BearingReliability(location::GpsInfo const & info)
{
  if (!info.HasBearing())
    return 0.0;
  if (!info.HasBearingAccuracy())
    return 0.65;
  if (info.m_bearingAccuracy <= 10.0)
    return 1.0;
  if (info.m_bearingAccuracy >= 60.0)
    return 0.10;
  return 1.0 - (info.m_bearingAccuracy - 10.0) / 50.0 * 0.90;
}

inline double TrajectoryHeadingWeight(double speedMps, double motionConfidence)
{
  motionConfidence = std::clamp(motionConfidence, 0.0, 1.0);
  double const lowSpeedWeight = 0.35 * motionConfidence;
  return std::max(lowSpeedWeight, HeadingWeightForSpeed(speedMps) * motionConfidence);
}

''')
append_before(
    "libs/routing/free_driving_road_snap_policy.hpp",
    '''inline double ParkingReleaseTimeSeconds(AreaContext const & context, bool recentEntranceHint)
''',
    '''inline double ProgressScaleM(location::GpsInfo const & info, double expectedM, double motionConfidence)
{
  double floorM = 10.0;
  auto const accuracy = GetAccuracyBand(info);
  if (accuracy == AccuracyBand::Good && motionConfidence >= 0.35)
  {
    double const confidence = std::clamp((motionConfidence - 0.35) / 0.65, 0.0, 1.0);
    floorM = 6.0 - 2.0 * confidence;
  }
  else if (accuracy == AccuracyBand::Moderate && motionConfidence >= 0.60)
  {
    floorM = 7.0;
  }
  return std::max(floorM, expectedM + 3.0);
}

inline double ParkingCandidateScoreAdjustment(AreaContext const & context, RoadMetadata const & metadata,
                                              RoadRelation relation, bool candidateInsideParkingArea,
                                              double motionConfidence)
{
  if (context.SuppressesParkingFree())
    return 0.0;

  double adjustment = 0.0;
  if (candidateInsideParkingArea && context.HasParkingArea())
  {
    if (metadata.m_parkingAisle)
      adjustment -= 0.45;
    else if (metadata.m_class == RoadClass::Service)
      adjustment -= 0.28;

    if (context.m_nearParkingEntrance && relation == RoadRelation::Connected)
      adjustment -= 0.18;
  }
  else if (candidateInsideParkingArea && context.m_nearParkingEntrance && relation == RoadRelation::Connected &&
           motionConfidence >= 0.25)
  {
    // Entry transition: useful only when topology and coherent motion agree. Mere proximity to a
    // car park must not pull a slowly moving vehicle off the adjacent public road.
    adjustment -= metadata.m_parkingAisle ? 0.28 : (metadata.m_class == RoadClass::Service ? 0.18 : 0.0);
    if (metadata.m_driveway)
      adjustment -= 0.08;
  }

  if (context.m_nearParkingSpace && candidateInsideParkingArea && metadata.m_parkingAisle)
    adjustment -= 0.06;

  if (context.HasParkingArea() && !candidateInsideParkingArea && relation == RoadRelation::Unrelated &&
      metadata.m_class != RoadClass::Service)
  {
    adjustment += 0.15;
  }

  return adjustment;
}

inline bool IsParallelCandidateAmbiguous(double undirectedAngleDegrees, double separationM, double scoreGap,
                                         double semanticGap, location::GpsInfo const & info, double motionConfidence)
{
  if (undirectedAngleDegrees > 20.0 || scoreGap > 0.75)
    return false;
  double const separationLimitM = std::clamp(info.m_horizontalAccuracy * 1.5, 8.0, 30.0);
  if (separationM > separationLimitM)
    return false;
  // Strong, motion-supported semantic evidence (for example a mapped aisle inside the parking
  // polygon versus a parallel public road outside it) may break an otherwise parallel tie.
  return !(semanticGap >= 0.25 && motionConfidence >= 0.35);
}

''')
replace_once(
    "libs/routing/free_driving_road_snap_policy.hpp",
    '''inline bool ParkingReleaseEligible(location::GpsInfo const & info, AreaContext const & context, bool roadMismatch,
                                   bool bestParkingAisle, bool stationaryHold)
{
  if (!info.HasSpeed() || info.m_speed > kParkingFreeMaxSpeedMps)
    return false;

  // Entrance proximity is a transition hint only. It must never release a car that is simply
  // travelling slowly on a public road beside a car-park entrance.
  if (!context.HasParkingArea() && !context.m_insideLargeBuilding)
    return false;

  if (context.m_insideLargeBuilding && !context.HasParkingArea())
    return roadMismatch;

  // Keep a good mapped parking aisle while traversing the facility, then allow fine-position
  // free movement as the car slows to manoeuvre/park or when the aisle no longer explains the fix.
  if (bestParkingAisle && !roadMismatch && !stationaryHold && info.m_speed > kParkingFinePositionMaxSpeedMps)
    return false;
  return roadMismatch || stationaryHold || !bestParkingAisle || info.m_speed <= kParkingFinePositionMaxSpeedMps;
}
''',
    '''inline bool ParkingReleaseEligible(location::GpsInfo const & info, AreaContext const & context, bool roadMismatch,
                                   bool bestParkingRoad, bool stationaryHold, bool motionEstablished)
{
  if (!info.HasSpeed() || info.m_speed > kParkingFreeMaxSpeedMps || context.SuppressesParkingFree())
    return false;

  // Entrance proximity is a transition hint only. It must never release a car that is simply
  // travelling slowly on a public road beside a car-park entrance.
  if (!context.HasParkingArea() && !context.m_insideLargeBuilding)
    return false;

  if (context.m_insideLargeBuilding && !context.HasParkingArea())
    return roadMismatch;

  // A moving vehicle on a plausible mapped parking road remains road-snapped even below 6 km/h.
  // ParkingFree is for final manoeuvring/stationary positioning, missing geometry or sustained
  // mismatch, not a speed threshold by itself.
  if (bestParkingRoad && !roadMismatch && motionEstablished)
    return false;
  if (bestParkingRoad && !roadMismatch && !stationaryHold && info.m_speed > kParkingFinePositionMaxSpeedMps)
    return false;
  return roadMismatch || stationaryHold || !bestParkingRoad || info.m_speed <= kParkingFinePositionMaxSpeedMps;
}
''')

# Area context: distinguish actual parking facilities from roadway parking and retain nearby facility geometry.
append_before(
    "libs/routing/free_driving_area_context.hpp",
    '''inline void MaybeSelectFreeArea(FeatureType & feature, int priority, int & selectedPriority, double & selectedAreaM2,
''',
    '''inline bool IsPointInsideTriangles(std::vector<m2::PointD> const & triangles, m2::PointD const & point)
{
  for (size_t i = 0; i + 2 < triangles.size(); i += 3)
    if (m2::IsPointInsideTriangle(point, triangles[i], triangles[i + 1], triangles[i + 2]))
      return true;
  return false;
}

''')
append_before(
    "libs/routing/free_driving_area_context.hpp",
    '''// Constrains |point| to the cached mapped parking/building area. A point already inside the
''',
    '''inline bool IsPointInsideNearbyParkingArea(AreaContext const & context, m2::PointD const & point)
{
  return detail::IsPointInsideTriangles(context.m_nearbyParkingAreaTriangles, point);
}

''')
replace_once(
    "libs/routing/free_driving_area_context.hpp",
    '''  static ftypes::BaseCheckerEx const structuredParking(
      {{"amenity", "parking", "underground"}, {"amenity", "parking", "multi-storey"}});
  static ftypes::BaseCheckerEx const parkingEntrance({{"amenity", "parking_entrance"}});
  static ftypes::BaseCheckerEx const parkingLikeLand({{"landuse", "garages"}});
''',
    '''  static ftypes::BaseCheckerEx const structuredParking(
      {{"amenity", "parking", "underground"}, {"amenity", "parking", "multi-storey"}});
  static ftypes::BaseCheckerEx const parkingEntrance({{"amenity", "parking_entrance"}});
  static ftypes::BaseCheckerEx const parkingSpace({{"amenity", "parking_space"}});
  static ftypes::BaseCheckerEx const parkingLane({{"amenity", "parking", "lane"}});
  static ftypes::BaseCheckerEx const streetSideParking({{"amenity", "parking", "street_side"}});
  static ftypes::BaseCheckerEx const parkingLikeLand({{"landuse", "garages"}});
''')
replace_once(
    "libs/routing/free_driving_area_context.hpp",
    '''    if (parkingEntrance(types))
    {
      if (feature::GetMinDistanceMeters(feature, point) <= 20.0)
        context.m_nearParkingEntrance = true;
      return;
    }

    if (ftypes::IsParkingChecker::Instance()(types))
    {
      if (detail::IsInsideArea(feature, point))
      {
        context.m_insideParking = true;
        bool const structured = structuredParking(types);
        if (structured)
          context.m_insideStructuredParking = true;
        detail::MaybeSelectFreeArea(feature, structured ? 3 : 2, selectedFreeAreaPriority, selectedFreeAreaM2,
                                    context.m_freeAreaTriangles);
      }
      return;
    }

    if (parkingLikeLand(types))
    {
      if (detail::IsInsideArea(feature, point))
      {
        context.m_insideParking = true;
        detail::MaybeSelectFreeArea(feature, 2, selectedFreeAreaPriority, selectedFreeAreaM2,
                                    context.m_freeAreaTriangles);
      }
      return;
    }
''',
    '''    if (parkingEntrance(types))
    {
      if (feature::GetMinDistanceMeters(feature, point) <= 20.0)
        context.m_nearParkingEntrance = true;
      return;
    }

    if (parkingSpace(types))
    {
      if (feature::GetMinDistanceMeters(feature, point) <= 15.0)
        context.m_nearParkingSpace = true;
      return;
    }

    if (parkingLane(types) || streetSideParking(types))
    {
      if (detail::IsInsideArea(feature, point))
      {
        context.m_insideParkingLane = parkingLane(types);
        context.m_insideStreetSideParking = streetSideParking(types);
      }
      return;
    }

    if (ftypes::IsParkingChecker::Instance()(types))
    {
      double const distanceM = feature::GetMinDistanceMeters(feature, point);
      if (distanceM <= 30.0)
      {
        context.m_nearParkingArea = true;
        if (distanceM < context.m_nearestParkingAreaDistanceM)
        {
          context.m_nearestParkingAreaDistanceM = distanceM;
          detail::CopyAreaTriangles(feature, context.m_nearbyParkingAreaTriangles);
        }
      }
      if (detail::IsInsideArea(feature, point))
      {
        context.m_insideParking = true;
        bool const structured = structuredParking(types);
        if (structured)
          context.m_insideStructuredParking = true;
        detail::MaybeSelectFreeArea(feature, structured ? 3 : 2, selectedFreeAreaPriority, selectedFreeAreaM2,
                                    context.m_freeAreaTriangles);
      }
      return;
    }

    if (parkingLikeLand(types))
    {
      double const distanceM = feature::GetMinDistanceMeters(feature, point);
      if (distanceM <= 30.0)
      {
        context.m_nearParkingArea = true;
        if (distanceM < context.m_nearestParkingAreaDistanceM)
        {
          context.m_nearestParkingAreaDistanceM = distanceM;
          detail::CopyAreaTriangles(feature, context.m_nearbyParkingAreaTriangles);
        }
      }
      if (detail::IsInsideArea(feature, point))
      {
        context.m_insideParking = true;
        detail::MaybeSelectFreeArea(feature, 2, selectedFreeAreaPriority, selectedFreeAreaM2,
                                    context.m_freeAreaTriangles);
      }
      return;
    }
''')

# State machine carries the stronger notion of a mapped parking road, not only parking_aisle.
replace_once(
    "libs/routing/free_driving_road_matcher.hpp",
    '''  bool m_bestParkingAisle = false;
''',
    '''  bool m_bestParkingAisle = false;
  bool m_bestParkingRoad = false;
''')
replace_once(
    "libs/routing/free_driving_road_matcher.cpp",
    '''                                    (m_freeDrivingAreaContext.m_nearParkingEntrance && !bestParkingAisle);
''',
    '''                                    (m_freeDrivingAreaContext.m_nearParkingEntrance && !bestParkingRoad);
''') if False else None
# The matcher source uses the evidence object rather than session member names.
replace_once(
    "libs/routing/free_driving_road_matcher.cpp",
    '''      allowFreeStateRoadReacquire = !m_freeDrivingAreaContext.HasParkingArea() ||
                                    (m_freeDrivingAreaContext.m_nearParkingEntrance && !bestParkingAisle);
''',
    '''      allowFreeStateRoadReacquire = !m_freeDrivingAreaContext.HasParkingArea() ||
                                    (m_freeDrivingAreaContext.m_nearParkingEntrance && !bestParkingRoad);
''') if False else None
# Actual matcher condition is independent of area construction; update the parking-free reacquire guard if present.
matcher_path = ROOT / "libs/routing/free_driving_road_matcher.cpp"
matcher_text = matcher_path.read_text(encoding="utf-8")
matcher_text = matcher_text.replace("!evidence.m_bestParkingAisle", "!evidence.m_bestParkingRoad")
matcher_path.write_text(matcher_text, encoding="utf-8")

# RoutingSession owns the real-fix motion estimator and last evidence for display projection.
replace_once(
    "libs/routing/routing_session.hpp",
    '''#include "routing/free_driving_road_matcher.hpp"
#include "routing/free_driving_road_snap_policy.hpp"
''',
    '''#include "routing/free_driving_motion_evidence.hpp"
#include "routing/free_driving_road_matcher.hpp"
#include "routing/free_driving_road_snap_policy.hpp"
''')
replace_once(
    "libs/routing/routing_session.hpp",
    '''  PositionAccumulator m_freeDrivingPositionAccumulator;
  bool m_freeDrivingRoadSnapEnabled = false;
''',
    '''  PositionAccumulator m_freeDrivingPositionAccumulator;
  free_driving_snap::LowSpeedMotionEstimator m_freeDrivingMotionEstimator;
  free_driving_snap::MotionEvidence m_freeDrivingLastMotionEvidence;
  bool m_freeDrivingRoadSnapEnabled = false;
''')

# Free-driving scoring and transitions.
replace_once(
    "libs/routing/routing_session_free_driving.cpp",
    '''  double m_roadClassPenalty = 0.0;
  double m_score = std::numeric_limits<double>::max();
''',
    '''  double m_roadClassPenalty = 0.0;
  double m_parkingSemanticAdjustment = 0.0;
  bool m_insideParkingArea = false;
  bool m_parkingRoad = false;
  double m_score = std::numeric_limits<double>::max();
''')
append_before(
    "libs/routing/routing_session_free_driving.cpp",
    '''double ObservationIntervalSeconds(double previousTimestamp, double currentTimestamp)
''',
    '''double ObservationTimestamp(location::GpsInfo const & info)
{
  return free_driving_snap::LowSpeedMotionEstimator::ObservationTimestamp(info);
}

''')
replace_once(
    "libs/routing/routing_session_free_driving.cpp",
    '''Candidate ScoreCandidate(AsyncRouter & router, CandidateSeed seed, location::GpsInfo const & info,
                         m2::PointD const & rawPoint, m2::PointD const & movementDirection, double headingWeight,
                         EdgeProj const * previous, double roadIntervalSeconds, double rawTravelSinceAcceptedM,
                         bool parkingContext)
''',
    '''Candidate ScoreCandidate(AsyncRouter & router, CandidateSeed seed, location::GpsInfo const & info,
                         m2::PointD const & rawPoint, m2::PointD const & movementDirection, double headingWeight,
                         EdgeProj const * previous, double roadIntervalSeconds, double rawTravelSinceAcceptedM,
                         free_driving_snap::AreaContext const & areaContext, double motionConfidence)
''')
replace_once(
    "libs/routing/routing_session_free_driving.cpp",
    '''  bool const needMetadata = candidate.m_relation == free_driving_snap::RoadRelation::Unrelated || parkingContext;
  if (needMetadata)
    candidate.m_hasMetadata = router.GetFreeDrivingRoadMetadata(candidate.m_projection.m_edge, candidate.m_metadata);
''',
    '''  bool const needMetadata = candidate.m_relation == free_driving_snap::RoadRelation::Unrelated ||
                            areaContext.HasParkingSignals();
  if (needMetadata)
    candidate.m_hasMetadata = router.GetFreeDrivingRoadMetadata(candidate.m_projection.m_edge, candidate.m_metadata);

  candidate.m_insideParkingArea =
      free_driving_snap::IsPointInsideNearbyParkingArea(areaContext, candidate.m_projection.m_point);
  candidate.m_parkingRoad = candidate.m_hasMetadata &&
                            (candidate.m_metadata.m_parkingAisle ||
                             (candidate.m_insideParkingArea &&
                              candidate.m_metadata.m_class == free_driving_snap::RoadClass::Service));
  if (candidate.m_hasMetadata)
  {
    candidate.m_parkingSemanticAdjustment = free_driving_snap::ParkingCandidateScoreAdjustment(
        areaContext, candidate.m_metadata, candidate.m_relation, candidate.m_insideParkingArea, motionConfidence);
  }
''')
replace_once(
    "libs/routing/routing_session_free_driving.cpp",
    '''    double const scaleM = std::max(10.0, expectedM + 5.0);
''',
    '''    double const scaleM = free_driving_snap::ProgressScaleM(info, expectedM, motionConfidence);
''')
replace_once(
    "libs/routing/routing_session_free_driving.cpp",
    '''  candidate.m_score = candidate.m_distancePenalty + candidate.m_headingPenalty + candidate.m_continuityPenalty +
                      candidate.m_pathProgressPenalty + candidate.m_chordProgressPenalty + candidate.m_roadClassPenalty;
''',
    '''  candidate.m_score = candidate.m_distancePenalty + candidate.m_headingPenalty + candidate.m_continuityPenalty +
                      candidate.m_pathProgressPenalty + candidate.m_chordProgressPenalty + candidate.m_roadClassPenalty +
                      candidate.m_parkingSemanticAdjustment;
''')
replace_once(
    "libs/routing/routing_session_free_driving.cpp",
    '''double RunnerUpScore(std::vector<Candidate> const & candidates, Candidate const & best)
{
  for (auto const & candidate : candidates)
    if (candidate.m_projection.m_edge.GetFeatureId() != best.m_projection.m_edge.GetFeatureId())
      return candidate.m_score;
  return free_driving_snap::kNoRunnerUpScore;
}
''',
    '''Candidate const * RunnerUpCandidate(std::vector<Candidate> const & candidates, Candidate const & best)
{
  for (auto const & candidate : candidates)
    if (candidate.m_projection.m_edge.GetFeatureId() != best.m_projection.m_edge.GetFeatureId())
      return &candidate;
  return nullptr;
}

double UndirectedDirectionAngleDegrees(m2::PointD const & lhs, m2::PointD const & rhs)
{
  double const angle = DirectionAngleDegrees(lhs, rhs);
  return std::min(angle, 180.0 - angle);
}
''')
replace_once(
    "libs/routing/routing_session_free_driving.cpp",
    '''  m_freeDrivingPositionAccumulator.Clear();
  m_freeDrivingRoadMatcher.Reset();
''',
    '''  m_freeDrivingPositionAccumulator.Clear();
  m_freeDrivingMotionEstimator.Reset();
  m_freeDrivingLastMotionEvidence = {};
  m_freeDrivingRoadMatcher.Reset();
''')
replace_once(
    "libs/routing/routing_session_free_driving.cpp",
    '''  m2::PointD const rawPoint = mercator::FromLatLon(rawLocation.m_latitude, rawLocation.m_longitude);

  if (m_freeDrivingLastObservationTimestamp > 0.0 && rawLocation.m_timestamp - m_freeDrivingLastObservationTimestamp >
                                                         free_driving_snap::kMaxAcceptedObservationGapSeconds)
''',
    '''  m2::PointD const rawPoint = mercator::FromLatLon(rawLocation.m_latitude, rawLocation.m_longitude);
  double const observationTimestamp = ObservationTimestamp(rawLocation);

  if (m_freeDrivingLastObservationTimestamp > 0.0 &&
      observationTimestamp - m_freeDrivingLastObservationTimestamp > free_driving_snap::kMaxAcceptedObservationGapSeconds)
''')
replace_once(
    "libs/routing/routing_session_free_driving.cpp",
    '''  double const observationIntervalSeconds =
      ObservationIntervalSeconds(m_freeDrivingLastObservationTimestamp, rawLocation.m_timestamp);
  double const evidenceDeltaSeconds =
      free_driving_snap::TemporalEvidenceDeltaSeconds(m_freeDrivingLastObservationTimestamp, rawLocation.m_timestamp);
''',
    '''  double const observationIntervalSeconds =
      ObservationIntervalSeconds(m_freeDrivingLastObservationTimestamp, observationTimestamp);
  double const evidenceDeltaSeconds =
      free_driving_snap::TemporalEvidenceDeltaSeconds(m_freeDrivingLastObservationTimestamp, observationTimestamp);
''')
replace_once(
    "libs/routing/routing_session_free_driving.cpp",
    '''        free_driving_snap::ShouldRefreshAreaContext(m_freeDrivingAreaContextTimestamp, rawLocation.m_timestamp,
                                                    contextMoveM))
''',
    '''        free_driving_snap::ShouldRefreshAreaContext(m_freeDrivingAreaContextTimestamp, observationTimestamp,
                                                    contextMoveM))
''')
replace_once(
    "libs/routing/routing_session_free_driving.cpp",
    '''      m_freeDrivingAreaContextTimestamp = rawLocation.m_timestamp;
''',
    '''      m_freeDrivingAreaContextTimestamp = observationTimestamp;
''')
replace_once(
    "libs/routing/routing_session_free_driving.cpp",
    '''    m_freeDrivingLastObservationTimestamp = rawLocation.m_timestamp;
    return;
  }

  location::GpsInfo policyInfo = rawLocation;
''',
    '''    m_freeDrivingLastObservationTimestamp = observationTimestamp;
    return;
  }

  auto const motionEvidence = m_freeDrivingMotionEstimator.Push(rawLocation, rawPoint);
  m_freeDrivingLastMotionEvidence = motionEvidence;

  location::GpsInfo policyInfo = rawLocation;
''')
replace_once(
    "libs/routing/routing_session_free_driving.cpp",
    '''  m2::PointD displacementDirection;
  double const displacementThresholdM = std::clamp(rawLocation.m_horizontalAccuracy * 0.35, 2.0, 6.0);
  if (m_freeDrivingHasLastRawPoint && observationIntervalSeconds > 0.0 && rawStepM >= displacementThresholdM &&
      rawStepM <= 80.0)
  {
    displacementDirection = rawPoint - m_freeDrivingLastRawPoint;
  }
  if (displacementDirection.IsAlmostZero())
  {
    displacementDirection =
        m_freeDrivingPositionAccumulator.GetRecentDirection(free_driving_snap::RecentDirectionTrackLengthM(policyInfo));
  }

  m2::PointD movementDirection = displacementDirection;
  double headingWeight = free_driving_snap::HeadingWeightForSpeed(effectiveSpeedMps);
  if (rawLocation.HasBearing())
  {
    m2::PointD const bearingDirection = DirectionFromBearing(rawLocation.m_bearing);
    movementDirection = bearingDirection;
    if (!displacementDirection.IsAlmostZero())
    {
      headingWeight *=
          free_driving_snap::HeadingAgreementFactor(DirectionAngleDegrees(bearingDirection, displacementDirection));
    }
  }

  bool const stationaryHold = free_driving_snap::IsStationaryHold(policyInfo, rawStepM, observationIntervalSeconds);
  bool const motionEstablished =
      free_driving_snap::HasEstablishedMotion(policyInfo, rawStepM, observationIntervalSeconds);
''',
    '''  m2::PointD displacementDirection = motionEvidence.HasDirection() ? motionEvidence.m_direction : m2::PointD{};
  double const displacementThresholdM = std::clamp(rawLocation.m_horizontalAccuracy * 0.35, 2.0, 6.0);
  if (displacementDirection.IsAlmostZero() && m_freeDrivingHasLastRawPoint && observationIntervalSeconds > 0.0 &&
      rawStepM >= displacementThresholdM && rawStepM <= 80.0)
  {
    displacementDirection = rawPoint - m_freeDrivingLastRawPoint;
  }
  if (displacementDirection.IsAlmostZero())
  {
    displacementDirection =
        m_freeDrivingPositionAccumulator.GetRecentDirection(free_driving_snap::RecentDirectionTrackLengthM(policyInfo));
  }

  m2::PointD movementDirection = displacementDirection;
  double headingWeight = 0.0;
  double const bearingReliability = free_driving_snap::BearingReliability(rawLocation);
  if (effectiveSpeedMps <= free_driving_snap::kCruiseSpeedMps && motionEvidence.HasDirection() &&
      motionEvidence.m_confidence >= 0.15)
  {
    movementDirection = motionEvidence.m_direction;
    headingWeight = free_driving_snap::TrajectoryHeadingWeight(effectiveSpeedMps, motionEvidence.m_confidence);
    if (rawLocation.HasBearing())
    {
      double const agreement = free_driving_snap::HeadingAgreementFactor(
          DirectionAngleDegrees(DirectionFromBearing(rawLocation.m_bearing), motionEvidence.m_direction));
      headingWeight *= 0.75 + 0.25 * agreement * bearingReliability;
    }
  }
  else if (rawLocation.HasBearing())
  {
    m2::PointD const bearingDirection = DirectionFromBearing(rawLocation.m_bearing);
    movementDirection = bearingDirection;
    headingWeight = free_driving_snap::HeadingWeightForSpeed(effectiveSpeedMps) * bearingReliability;
    if (motionEvidence.HasDirection())
    {
      headingWeight *=
          free_driving_snap::HeadingAgreementFactor(DirectionAngleDegrees(bearingDirection, motionEvidence.m_direction));
    }
    else if (!displacementDirection.IsAlmostZero())
    {
      headingWeight *=
          free_driving_snap::HeadingAgreementFactor(DirectionAngleDegrees(bearingDirection, displacementDirection));
    }
  }
  else if (motionEvidence.HasDirection())
  {
    movementDirection = motionEvidence.m_direction;
    headingWeight = free_driving_snap::TrajectoryHeadingWeight(effectiveSpeedMps, motionEvidence.m_confidence);
  }
  else if (!displacementDirection.IsAlmostZero())
  {
    headingWeight = free_driving_snap::HeadingWeightForSpeed(effectiveSpeedMps) * 0.5;
  }

  bool const stationaryHold = free_driving_snap::IsStationaryHold(policyInfo, rawStepM, observationIntervalSeconds);
  bool const motionEstablished =
      free_driving_snap::HasEstablishedMotion(policyInfo, rawStepM, observationIntervalSeconds) ||
      motionEvidence.m_confidence >= 0.50;
''')
replace_once(
    "libs/routing/routing_session_free_driving.cpp",
    '''      rawLocation.m_timestamp > m_freeDrivingLastAcceptedRoadTimestamp)
  {
    double const interval = rawLocation.m_timestamp - m_freeDrivingLastAcceptedRoadTimestamp;
''',
    '''      observationTimestamp > m_freeDrivingLastAcceptedRoadTimestamp)
  {
    double const interval = observationTimestamp - m_freeDrivingLastAcceptedRoadTimestamp;
''')
replace_once(
    "libs/routing/routing_session_free_driving.cpp",
    '''  bool const parkingContext = m_freeDrivingAreaContext.HasParkingArea() ||
                              m_freeDrivingAreaContext.m_insideLargeBuilding ||
                              m_freeDrivingAreaContext.m_nearParkingEntrance;
  std::vector<Candidate> candidates;
''',
    '''  std::vector<Candidate> candidates;
''')
replace_once(
    "libs/routing/routing_session_free_driving.cpp",
    '''    candidates.push_back(ScoreCandidate(*m_router, std::move(seed), policyInfo, rawPoint, movementDirection,
                                        headingWeight, previous, roadIntervalSeconds, rawTravelSinceAcceptedM,
                                        parkingContext));
''',
    '''    candidates.push_back(ScoreCandidate(*m_router, std::move(seed), policyInfo, rawPoint, movementDirection,
                                        headingWeight, previous, roadIntervalSeconds, rawTravelSinceAcceptedM,
                                        m_freeDrivingAreaContext, motionEvidence.m_confidence));
''')
replace_once(
    "libs/routing/routing_session_free_driving.cpp",
    '''  double const runnerUpScore = best != nullptr ? RunnerUpScore(candidates, *best) : free_driving_snap::kNoRunnerUpScore;
  bool const bestUnambiguous =
      best != nullptr && free_driving_snap::IsUnambiguous(best->m_score, runnerUpScore, accuracy);
''',
    '''  Candidate const * runnerUp = best != nullptr ? RunnerUpCandidate(candidates, *best) : nullptr;
  double const runnerUpScore = runnerUp != nullptr ? runnerUp->m_score : free_driving_snap::kNoRunnerUpScore;
  bool parallelAmbiguous = false;
  if (best != nullptr && runnerUp != nullptr)
  {
    double const angle = UndirectedDirectionAngleDegrees(best->m_projection.m_edge.GetDirection(),
                                                         runnerUp->m_projection.m_edge.GetDirection());
    double const separationM = mercator::DistanceOnEarth(best->m_projection.m_point, runnerUp->m_projection.m_point);
    parallelAmbiguous = free_driving_snap::IsParallelCandidateAmbiguous(
        angle, separationM, std::abs(runnerUp->m_score - best->m_score),
        std::abs(runnerUp->m_parkingSemanticAdjustment - best->m_parkingSemanticAdjustment), policyInfo,
        motionEvidence.m_confidence);
  }
  bool const bestUnambiguous = best != nullptr &&
      free_driving_snap::IsUnambiguous(best->m_score, runnerUpScore, accuracy) && !parallelAmbiguous;
''')
replace_once(
    "libs/routing/routing_session_free_driving.cpp",
    '''  bool const bestParkingAisle = best != nullptr && best->m_hasMetadata && best->m_metadata.m_parkingAisle;
''',
    '''  bool const bestParkingAisle = best != nullptr && best->m_hasMetadata && best->m_metadata.m_parkingAisle;
  bool const bestParkingRoad = best != nullptr && best->m_parkingRoad;
''')
replace_once(
    "libs/routing/routing_session_free_driving.cpp",
    '''  bool const parkingReleaseEligible = free_driving_snap::ParkingReleaseEligible(
      policyInfo, m_freeDrivingAreaContext, roadMismatch || weakRoadMatch, bestParkingAisle, stationaryHold);
''',
    '''  bool const parkingReleaseEligible = free_driving_snap::ParkingReleaseEligible(
      policyInfo, m_freeDrivingAreaContext, roadMismatch || weakRoadMatch, bestParkingRoad, stationaryHold,
      motionEstablished);
''')
replace_once(
    "libs/routing/routing_session_free_driving.cpp",
    '''                                    (m_freeDrivingAreaContext.m_nearParkingEntrance && !bestParkingAisle);
''',
    '''                                    (m_freeDrivingAreaContext.m_nearParkingEntrance && !bestParkingRoad);
''')
replace_once(
    "libs/routing/routing_session_free_driving.cpp",
    '''  evidence.m_bestParkingAisle = bestParkingAisle;
''',
    '''  evidence.m_bestParkingAisle = bestParkingAisle;
  evidence.m_bestParkingRoad = bestParkingRoad;
''')
# Accepted-road timestamps must use the same monotonic clock as interval evidence.
session_path = ROOT / "libs/routing/routing_session_free_driving.cpp"
session_text = session_path.read_text(encoding="utf-8")
session_text = session_text.replace("m_freeDrivingLastAcceptedRoadTimestamp = rawLocation.m_timestamp;",
                                    "m_freeDrivingLastAcceptedRoadTimestamp = observationTimestamp;")
session_text = session_text.replace("m_freeDrivingLastObservationTimestamp = rawLocation.m_timestamp;",
                                    "m_freeDrivingLastObservationTimestamp = observationTimestamp;")
session_path.write_text(session_text, encoding="utf-8")
replace_once(
    "libs/routing/routing_session_free_driving.cpp",
    '''         "best_score=", best ? best->m_score : -1.0, "runner_up=", std::isfinite(runnerUpScore) ? runnerUpScore : -1.0,
         "parking=", m_freeDrivingAreaContext.m_insideParking,
''',
    '''         "best_score=", best ? best->m_score : -1.0, "runner_up=", std::isfinite(runnerUpScore) ? runnerUpScore : -1.0,
         "motion_conf=", motionEvidence.m_confidence, "bearing_acc=", rawLocation.m_bearingAccuracy,
         "speed_acc=", rawLocation.m_speedAccuracy, "parallel_ambiguous=", parallelAmbiguous,
         "parking_semantic=", best ? best->m_parkingSemanticAdjustment : 0.0,
         "parking=", m_freeDrivingAreaContext.m_insideParking,
''')
replace_once(
    "libs/routing/routing_session_free_driving.cpp",
    '''  EdgeProj bestProjection;
  double bestScore = std::numeric_limits<double>::max();
  m2::PointD bearingDirection;
  if (displayInput.HasBearing())
    bearingDirection = DirectionFromBearing(displayInput.m_bearing);

  for (auto const & edge : *edges)
  {
    EdgeProj projection{edge, ProjectToEdge(rawPoint, edge)};
    double score = mercator::DistanceOnEarth(rawPoint, projection.m_point);
    if (!bearingDirection.IsAlmostZero() && displayInput.HasSpeed() &&
        displayInput.m_speed > free_driving_snap::kDirectionUsefulMinSpeedMps)
    {
      score += DirectionAngleDegrees(bearingDirection, edge.GetDirection()) / 90.0 * 5.0;
    }

    if (score < bestScore)
''',
    '''  EdgeProj bestProjection;
  double bestScore = std::numeric_limits<double>::max();
  m2::PointD displayDirection;
  double displayDirectionWeight = 0.0;
  if (m_freeDrivingLastMotionEvidence.HasDirection())
  {
    displayDirection = m_freeDrivingLastMotionEvidence.m_direction;
    double const speedMps = displayInput.HasSpeed() ? std::max(0.0, displayInput.m_speed) : 0.0;
    displayDirectionWeight = free_driving_snap::TrajectoryHeadingWeight(
        speedMps, m_freeDrivingLastMotionEvidence.m_confidence);
  }
  if (displayInput.HasBearing() && displayInput.HasSpeed() &&
      displayInput.m_speed > free_driving_snap::kDirectionUsefulMinSpeedMps)
  {
    displayDirection = DirectionFromBearing(displayInput.m_bearing);
    displayDirectionWeight = free_driving_snap::HeadingWeightForSpeed(displayInput.m_speed) *
                             free_driving_snap::BearingReliability(displayInput);
  }

  for (auto const & edge : *edges)
  {
    EdgeProj projection{edge, ProjectToEdge(rawPoint, edge)};
    double score = mercator::DistanceOnEarth(rawPoint, projection.m_point);
    if (!displayDirection.IsAlmostZero() && displayDirectionWeight > 0.0)
      score += DirectionAngleDegrees(displayDirection, edge.GetDirection()) / 90.0 * 5.0 * displayDirectionWeight;

    if (SameDirectedEdge(edge, m_freeDrivingProjection.m_edge))
    {
      score -= 1.5;
      double const acceptedToEndM = mercator::DistanceOnEarth(m_freeDrivingProjection.m_point, edge.GetEndPoint());
      double const candidateToEndM = mercator::DistanceOnEarth(projection.m_point, edge.GetEndPoint());
      if (candidateToEndM > acceptedToEndM + 3.0)
        score += std::min(6.0, (candidateToEndM - acceptedToEndM - 3.0) * 0.5);
    }

    if (score < bestScore)
''')

# Focused policy tests, including coherent low-speed motion, uncertainty, parking semantics and parallel ambiguity.
replace_once(
    "libs/routing/routing_tests/free_driving_road_snap_policy_tests.cpp",
    '''#include "routing/free_driving_area_context.hpp"
#include "routing/free_driving_road_snap_policy.hpp"
''',
    '''#include "routing/free_driving_area_context.hpp"
#include "routing/free_driving_motion_evidence.hpp"
#include "routing/free_driving_road_snap_policy.hpp"
''')
# Update the two existing ParkingReleaseEligible call sites to the expanded contract.
policy_test_path = ROOT / "libs/routing/routing_tests/free_driving_road_snap_policy_tests.cpp"
policy_tests = policy_test_path.read_text(encoding="utf-8")
policy_tests = policy_tests.replace("ParkingReleaseEligible(slow, entranceOnly, true, false, false)",
                                    "ParkingReleaseEligible(slow, entranceOnly, true, false, false, true)")
policy_tests = policy_tests.replace("ParkingReleaseEligible(slow, surface, false, false, false)",
                                    "ParkingReleaseEligible(slow, surface, false, false, false, true)")
policy_tests = policy_tests.replace("ParkingReleaseEligible(traversing, surface, false, true, false)",
                                    "ParkingReleaseEligible(traversing, surface, false, true, false, true)")
policy_tests = policy_tests.replace("ParkingReleaseEligible(manoeuvring, surface, false, true, false)",
                                    "ParkingReleaseEligible(manoeuvring, surface, false, true, false, false)")
policy_tests = policy_tests.replace("ParkingReleaseEligible(traversing, surface, false, true, true)",
                                    "ParkingReleaseEligible(traversing, surface, false, true, true, false)")
policy_test_path.write_text(policy_tests, encoding="utf-8")
append_before(
    "libs/routing/routing_tests/free_driving_road_snap_policy_tests.cpp",
    '''UNIT_TEST(FreeDrivingRoadSnapPolicy_TemporalEvidenceRejectsBadClockIntervals)
''',
    '''UNIT_TEST(FreeDrivingRoadSnapPolicy_UsesProviderUncertainty)
{
  auto fix = MakeFix(2.0, 8.0, 1.0);
  fix.m_bearing = 90.0;
  fix.m_bearingAccuracy = 5.0;
  TEST_GREATER(BearingReliability(fix), 0.9, ());
  fix.m_bearingAccuracy = 50.0;
  TEST_LESS(BearingReliability(fix), 0.4, ());

  fix.m_speedAccuracy = 0.2;
  TEST_LESS(std::abs(EffectiveSpeedMps(fix, 10.0, 1.0) - 2.0), 0.5, ());
}

UNIT_TEST(FreeDrivingRoadSnapPolicy_CoherentCrawlBuildsDirectionEvidence)
{
  LowSpeedMotionEstimator estimator;
  auto fix = MakeFix(4.0 / 3.6, 5.0, 1.0);
  fix.m_monotonicTimestamp = 1.0;
  auto point = mercator::FromLatLon(-35.0, 149.0);
  TEST(!estimator.Push(fix, point).HasDirection(), ());

  fix.m_monotonicTimestamp = 2.0;
  point = mercator::FromLatLon(-35.0, 149.00003);
  TEST(!estimator.Push(fix, point).HasDirection(), ());

  fix.m_monotonicTimestamp = 3.0;
  point = mercator::FromLatLon(-35.0, 149.00006);
  auto const evidence = estimator.Push(fix, point);
  TEST(evidence.HasDirection(), ());
  TEST_GREATER(evidence.m_confidence, 0.15, ());
  TEST_GREATER(TrajectoryHeadingWeight(4.0 / 3.6, evidence.m_confidence), 0.0, ());
}

UNIT_TEST(FreeDrivingRoadSnapPolicy_LowSpeedProgressUsesFinerScaleOnlyWithEvidence)
{
  auto good = MakeFix(4.0 / 3.6, 6.0, 1.0);
  TEST_LESS(ProgressScaleM(good, 2.0, 0.8), ProgressScaleM(good, 2.0, 0.0), ());
  auto moderate = MakeFix(4.0 / 3.6, 25.0, 1.0);
  TEST_GREATER_OR_EQUAL(ProgressScaleM(moderate, 2.0, 0.2), 10.0, ());
}

''')
append_before(
    "libs/routing/routing_tests/free_driving_road_snap_policy_tests.cpp",
    '''UNIT_TEST(FreeDrivingRoadSnapPolicy_StructuredParkingReleasesSooner)
''',
    '''UNIT_TEST(FreeDrivingRoadSnapPolicy_MovingMappedParkingRoadStaysSnappedAtCrawlSpeed)
{
  AreaContext surface;
  surface.m_insideParking = true;
  auto crawling = MakeFix(3.0 / 3.6, 8.0, 10.0);
  TEST(!ParkingReleaseEligible(crawling, surface, false, true, false, true), ());
  TEST(ParkingReleaseEligible(crawling, surface, false, true, true, false), ());
}

UNIT_TEST(FreeDrivingRoadSnapPolicy_RoadwayParkingNeverCreatesParkingFreeAuthority)
{
  AreaContext streetSide;
  streetSide.m_insideStreetSideParking = true;
  auto crawling = MakeFix(3.0 / 3.6, 8.0, 10.0);
  TEST(!ParkingReleaseEligible(crawling, streetSide, true, false, true, false), ());
}

UNIT_TEST(FreeDrivingRoadSnapPolicy_ParkingSemanticsOnlyBreakSupportedTies)
{
  AreaContext inside;
  inside.m_insideParking = true;
  RoadMetadata aisle;
  aisle.m_class = RoadClass::Service;
  aisle.m_parkingAisle = true;
  TEST_LESS(ParkingCandidateScoreAdjustment(inside, aisle, RoadRelation::Unrelated, true, 0.5), 0.0, ());

  AreaContext merelyNear;
  merelyNear.m_nearParkingArea = true;
  TEST_ALMOST_EQUAL_ULPS(ParkingCandidateScoreAdjustment(merelyNear, aisle, RoadRelation::Unrelated, true, 0.8), 0.0,
                         ());

  merelyNear.m_nearParkingEntrance = true;
  TEST_LESS(ParkingCandidateScoreAdjustment(merelyNear, aisle, RoadRelation::Connected, true, 0.8), 0.0, ());
}

UNIT_TEST(FreeDrivingRoadSnapPolicy_ParallelRoadNeedsMoreThanOneLateralFix)
{
  auto fix = MakeFix(10.0 / 3.6, 8.0, 1.0);
  TEST(IsParallelCandidateAmbiguous(5.0, 8.0, 0.3, 0.0, fix, 0.6), ());
  TEST(!IsParallelCandidateAmbiguous(5.0, 8.0, 0.3, 0.30, fix, 0.6), ());
  TEST(!IsParallelCandidateAmbiguous(35.0, 8.0, 0.3, 0.0, fix, 0.6), ());
}

''')

# Matcher replay coverage: parking-road motion itself is not release evidence.
append_before(
    "libs/routing/routing_tests/free_driving_road_matcher_tests.cpp",
    '''UNIT_TEST(FreeDrivingRoadMatcher_ParkingManoeuvreEntersFinePositionMode)
''',
    '''UNIT_TEST(FreeDrivingRoadMatcher_MovingMappedParkingRoadRemainsRoad)
{
  FreeDrivingRoadMatcher matcher;
  AcquireRoad(matcher);
  AreaContext context;
  context.m_insideParking = true;

  auto evidence = RoadEvidence(1);
  evidence.m_relation = RoadRelation::SameDirectedEdge;
  evidence.m_bestParkingRoad = true;
  evidence.m_parkingReleaseEligible = false;
  for (size_t i = 0; i < 5; ++i)
    TEST_EQUAL(matcher.Update(evidence, context).m_state, MatchState::Road, ());
}

''')

# Ensure no accidental whitespace corruption before the workflow commits.
for path in [
    "libs/platform/location.hpp",
    "android/sdk/src/main/java/app/organicmaps/sdk/location/LocationState.java",
    "android/sdk/src/main/java/app/organicmaps/sdk/location/LocationHelper.java",
    "android/sdk/src/main/cpp/app/organicmaps/sdk/LocationState.cpp",
    "libs/routing/free_driving_motion_evidence.hpp",
    "libs/routing/free_driving_road_snap_policy.hpp",
    "libs/routing/free_driving_area_context.hpp",
    "libs/routing/free_driving_road_matcher.hpp",
    "libs/routing/free_driving_road_matcher.cpp",
    "libs/routing/routing_session.hpp",
    "libs/routing/routing_session_free_driving.cpp",
    "libs/routing/routing_tests/free_driving_road_snap_policy_tests.cpp",
    "libs/routing/routing_tests/free_driving_road_matcher_tests.cpp",
]:
    text = (ROOT / path).read_text(encoding="utf-8")
    if "\r\n" in text:
        raise RuntimeError(f"{path}: unexpected CRLF")

print("low-speed matching patch materialised")
