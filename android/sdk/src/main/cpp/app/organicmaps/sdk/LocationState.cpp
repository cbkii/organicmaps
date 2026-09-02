#include "Framework.hpp"
#include "map/gps_tracker.hpp"
#include "routing/free_driving_road_snap_policy.hpp"

#include "app/organicmaps/sdk/core/jni_helper.hpp"

#include "app/organicmaps/sdk/platform/AndroidPlatform.hpp"

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

  g_framework->OnLocationUpdated(info);
  GpsTracker::Instance().OnLocationUpdated(info);
}

JNIEXPORT void Java_app_organicmaps_sdk_location_LocationState_nativeSetDrivingViewEnabled(JNIEnv * env, jclass clazz,
                                                                                           jboolean enabled,
                                                                                           jboolean autoReturn,
                                                                                           jboolean recenter)
{
  if (!g_framework || !g_framework->IsDrapeEngineCreated())
    return;

  auto const drapeEngine = g_framework->NativeFramework()->GetDrapeEngine();
  if (drapeEngine != nullptr)
    drapeEngine->SetDrivingView(enabled, autoReturn, recenter);
}

JNIEXPORT void Java_app_organicmaps_sdk_location_LocationState_nativeSetFreeDrivingTracking(JNIEnv * env, jclass clazz,
                                                                                            jboolean enabled,
                                                                                            jint snapMode,
                                                                                            jboolean offRoadOverride)
{
  if (!g_framework)
    return;

  auto * framework = g_framework->NativeFramework();
  if (framework == nullptr)
    return;

  routing::free_driving_snap::SnapMode mode = routing::free_driving_snap::SnapMode::Auto;
  switch (snapMode)
  {
  case static_cast<jint>(routing::free_driving_snap::SnapMode::Off):
    mode = routing::free_driving_snap::SnapMode::Off;
    break;
  case static_cast<jint>(routing::free_driving_snap::SnapMode::Strong):
    mode = routing::free_driving_snap::SnapMode::Strong;
    break;
  case static_cast<jint>(routing::free_driving_snap::SnapMode::Auto):
  default: mode = routing::free_driving_snap::SnapMode::Auto; break;
  }

  framework->GetRoutingManager().SetFreeDrivingTracking(enabled, mode, offRoadOverride);
}

JNIEXPORT jint Java_app_organicmaps_sdk_location_LocationState_nativeGetFreeDrivingMatchState(JNIEnv * env,
                                                                                              jclass clazz)
{
  if (!g_framework)
    return static_cast<jint>(routing::free_driving_snap::MatchState::Disabled);

  auto * framework = g_framework->NativeFramework();
  if (framework == nullptr)
    return static_cast<jint>(routing::free_driving_snap::MatchState::Disabled);

  return static_cast<jint>(framework->GetRoutingManager().GetFreeDrivingMatchState());
}
}  // extern "C"