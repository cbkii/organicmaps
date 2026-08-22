#include "Framework.hpp"

#include "map/bookmark_manager.hpp"
#include "map/user_mark.hpp"

#include "geometry/any_rect2d.hpp"
#include "geometry/point2d.hpp"
#include "geometry/rect2d.hpp"

#include <algorithm>
#include <limits>

extern "C"
{
JNIEXPORT jboolean Java_app_organicmaps_sdk_SearchMarkerHitTest_nativeHasSearchMarkerAt(
    JNIEnv *, jclass, jfloat xPx, jfloat yPx, jfloat radiusPx)
{
  if (!g_framework || !g_framework->IsDrapeEngineCreated() || radiusPx <= 0.0f)
    return JNI_FALSE;

  auto * framework = frm();
  if (framework == nullptr)
    return JNI_FALSE;

  // Convert the four corners of the requested screen-space touch target through the current
  // model view. The resulting global bounding rectangle is deliberately a little forgiving on
  // a rotated map, which is preferable for a direct-display in-car touch target.
  auto const radius = static_cast<double>(radiusPx);
  m2::RectD globalRect;
  globalRect.Add(framework->PtoG({static_cast<double>(xPx) - radius, static_cast<double>(yPx) - radius}));
  globalRect.Add(framework->PtoG({static_cast<double>(xPx) + radius, static_cast<double>(yPx) - radius}));
  globalRect.Add(framework->PtoG({static_cast<double>(xPx) + radius, static_cast<double>(yPx) + radius}));
  globalRect.Add(framework->PtoG({static_cast<double>(xPx) - radius, static_cast<double>(yPx) + radius}));
  if (!globalRect.IsValid())
    return JNI_FALSE;

  double distance = std::numeric_limits<double>::max();
  auto const * mark = framework->GetBookmarkManager().FindMarkInRect(
      static_cast<kml::MarkGroupId>(UserMark::Type::SEARCH), m2::AnyRectD(globalRect), true /* findOnlyVisible */,
      distance);
  return mark == nullptr ? JNI_FALSE : JNI_TRUE;
}
}  // extern "C"
