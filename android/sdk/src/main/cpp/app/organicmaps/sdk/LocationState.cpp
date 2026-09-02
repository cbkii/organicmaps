#include "Framework.hpp"
#include "map/gps_tracker.hpp"

#include "app/organicmaps/sdk/core/jni_helper.hpp"

#include "app/organicmaps/sdk/platform/AndroidPlatform.hpp"

#include "drape_frontend/user_event_stream.hpp"

#include "geometry/mercator.hpp"

#include <chrono>

namespace
{
auto constexpr kStartupCameraBridgeLifetime = std::chrono::seconds(10);

struct StartupCameraBridgeState
{
  df::DrapeEngine * m_engine = nullptr;
  bool m_forceDrivingArea = false;
  bool m_disableDrivingViewAfterLocation = false;
  std::chrono::steady_clock::time_point m_armedAt;
};

StartupCameraBridgeState g_startupCameraBridge;

auto GetDrapeEngine()
{
  if (!g_framework || !g_framework->IsDrapeEngineCreated())
    return decltype(g_framework->NativeFramework()->GetDrapeEngine()){};
  return g_framework->NativeFramework()->GetDrapeEngine();
}

void ResetStartupCameraBridge()
{
  g_startupCameraBridge = {};
}

void CancelStartupCameraBridge()
{
  auto const drapeEngine = GetDrapeEngine();
  if (drapeEngine != nullptr && g_startupCameraBridge.m_engine == drapeEngine.get() &&
      g_startupCameraBridge.m_disableDrivingViewAfterLocation)
  {
    drapeEngine->SetDrivingView(false /* enabled */, false /* autoReturn */, false /* recenter */);
  }
  ResetStartupCameraBridge();
}

bool HasCurrentStartupCameraBridge(df::DrapeEngine * engine)
{
  if (engine == nullptr || g_startupCameraBridge.m_engine != engine)
    return false;
  return std::chrono::steady_clock::now() - g_startupCameraBridge.m_armedAt <= kStartupCameraBridgeLifetime;
}

void ShowLocalArea(double lat, double lon, double radiusMeters)
{
  auto const drapeEngine = GetDrapeEngine();
  if (drapeEngine == nullptr || radiusMeters <= 0.0)
    return;

  if (g_framework->NativeFramework()->GetRoutingManager().IsRoutingActive())
    return;

  auto const rect = mercator::MetersToXY(lon, lat, radiusMeters);
  drapeEngine->SetModelViewRect(rect, false /* applyRotation */, df::kDoNotChangeZoom, false /* isAnim */,
                                true /* useVisibleViewport */);
}
}  // namespace

extern "C"
{
static void LocationStateModeChanged(location::EMyPositionMode mode, std::shared_ptr<jobject> const & listener)
{
  JNIEnv * env = jni::GetEnv();
  env->CallVoidMethod(*listener, jni::GetMethodID(env, *listener.get(), "onMyPositionModeChanged", "(I)V"),
                      static_cast<jint>(mode));
}

//  public static void nativeSwitchToNextMode();
JNIEXPORT void Java_app_organicmaps_sdk_location_LocationState_nativeSwitchToNextMode(JNIEnv * env, jclass clazz)
{
  g_framework->SwitchMyPositionNextMode();
}

// private static int nativeGetMode();
JNIEXPORT jint Java_app_organicmaps_sdk_location_LocationState_nativeGetMode(JNIEnv * env, jclass clazz)
{
  // GetMyPositionMode() is initialized only after drape creation.
  // https://github.com/organicmaps/organicmaps/issues/1128#issuecomment-1784435190
  ASSERT(g_framework && g_framework->IsDrapeEngineCreated(), ());
  return g_framework->GetMyPositionMode();
}

//  public static void nativeSetListener(ModeChangeListener listener);
JNIEXPORT void Java_app_organicmaps_sdk_location_LocationState_nativeSetListener(JNIEnv * env, jclass clazz,
                                                                                 jobject listener)
{
  g_framework->SetMyPositionModeListener(
      std::bind(&LocationStateModeChanged, std::placeholders::_1, jni::make_global_ref(listener)));
}

//  public static void nativeRemoveListener();
JNIEXPORT void Java_app_organicmaps_sdk_location_LocationState_nativeRemoveListener(JNIEnv * env, jclass clazz)
{
  g_framework->SetMyPositionModeListener(location::TMyPositionModeChanged());
}

JNIEXPORT void Java_app_organicmaps_sdk_location_LocationState_nativeOnLocationError(JNIEnv * env, jclass clazz,
                                                                                     int errorCode)
{
  g_framework->OnLocationError(errorCode);
}

JNIEXPORT void Java_app_organicmaps_sdk_location_LocationState_nativeLocationUpdated(JNIEnv * env, jclass clazz,
                                                                                     jlong time, jdouble lat,
                                                                                     jdouble lon, jfloat accuracyH,
                                                                                     jdouble altitude, jfloat accuracyV,
                                                                                     jfloat speed, jfloat bearing)
{
  location::GpsInfo info;
  info.m_source = location::EAndroidNative;

  info.m_timestamp = static_cast<double>(time) / 1000.0;
  info.m_latitude = lat;
  info.m_longitude = lon;

  if (accuracyH > 0)
    info.m_horizontalAccuracy = accuracyH;

  if (accuracyV > 0)
  {
    info.m_altitude = altitude;
    info.m_verticalAccuracy = accuracyV;
  }

  if (bearing >= 0)
    info.m_bearing = bearing;

  if (speed >= 0)
    info.m_speed = speed;

  auto const drapeEngine = GetDrapeEngine();
  bool hasPendingStartupCamera = drapeEngine != nullptr && HasCurrentStartupCameraBridge(drapeEngine.get());
  if (!hasPendingStartupCamera && g_startupCameraBridge.m_engine != nullptr)
  {
    CancelStartupCameraBridge();
    hasPendingStartupCamera = false;
  }

  // If launch happened before a live fix, frame the requested driving area immediately before the GPS message.
  // Both operations use the render thread's normal-priority queue, so follow-and-rotate inherits this sane scale.
  if (hasPendingStartupCamera && g_startupCameraBridge.m_forceDrivingArea)
    ShowLocalArea(lat, lon, 5000.0);

  g_framework->OnLocationUpdated(info);
  GpsTracker::Instance().OnLocationUpdated(info);

  if (hasPendingStartupCamera && g_startupCameraBridge.m_disableDrivingViewAfterLocation)
    drapeEngine->SetDrivingView(false /* enabled */, false /* autoReturn */, false /* recenter */);

  if (hasPendingStartupCamera || g_startupCameraBridge.m_engine != nullptr)
    ResetStartupCameraBridge();
}

JNIEXPORT void Java_app_organicmaps_sdk_location_LocationState_nativeSetDrivingViewEnabled(JNIEnv * env, jclass clazz,
                                                                                           jboolean enabled,
                                                                                           jboolean autoReturn,
                                                                                           jboolean recenter)
{
  auto const drapeEngine = GetDrapeEngine();
  if (drapeEngine != nullptr)
    drapeEngine->SetDrivingView(enabled, autoReturn, recenter);
}

JNIEXPORT void Java_app_organicmaps_incar_InCarStartupCameraNative_nativeShowLocalArea(JNIEnv * env, jclass clazz,
                                                                                       jdouble lat, jdouble lon,
                                                                                       jdouble radiusMeters)
{
  ShowLocalArea(lat, lon, radiusMeters);
}

JNIEXPORT void Java_app_organicmaps_incar_InCarStartupCameraNative_nativeRequestFollowAndRotate(
    JNIEnv * env, jclass clazz, jboolean forceDrivingArea, jboolean keepDrivingViewEnabled, jboolean autoReturn)
{
  auto const drapeEngine = GetDrapeEngine();
  if (drapeEngine == nullptr || g_framework->NativeFramework()->GetRoutingManager().IsRoutingActive())
  {
    CancelStartupCameraBridge();
    return;
  }

  CancelStartupCameraBridge();

  auto const mode = drapeEngine->GetMyPositionMode();
  bool const waitingForLocation = mode == location::PendingPosition || mode == location::NotFollowNoPosition;

  // Reuse the established native camera transition only as a bounded launch bridge. When ordinary Driving View is
  // not enabled, it is disabled again after the first queued GPS update (or immediately when a position already
  // exists), leaving FollowAndRotate as the location mode without creating a second persistent camera authority.
  drapeEngine->SetDrivingView(true /* enabled */, keepDrivingViewEnabled ? autoReturn : false, true /* recenter */);

  if (waitingForLocation)
  {
    g_startupCameraBridge.m_engine = drapeEngine.get();
    g_startupCameraBridge.m_forceDrivingArea = forceDrivingArea;
    g_startupCameraBridge.m_disableDrivingViewAfterLocation = !keepDrivingViewEnabled;
    g_startupCameraBridge.m_armedAt = std::chrono::steady_clock::now();
    return;
  }

  if (!keepDrivingViewEnabled)
    drapeEngine->SetDrivingView(false /* enabled */, false /* autoReturn */, false /* recenter */);
}

JNIEXPORT void Java_app_organicmaps_incar_InCarStartupCameraNative_nativeCancelPending(JNIEnv * env, jclass clazz)
{
  CancelStartupCameraBridge();
}
}  // extern "C"
