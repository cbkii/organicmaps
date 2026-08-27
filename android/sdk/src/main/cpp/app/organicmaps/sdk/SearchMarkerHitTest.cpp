#include "Framework.hpp"

#include "map/bookmark_manager.hpp"
#include "map/user_mark.hpp"

#include "geometry/any_rect2d.hpp"
#include "geometry/point2d.hpp"
#include "geometry/rect2d.hpp"

extern "C"
{
JNIEXPORT jboolean Java_app_organicmaps_sdk_SearchMarkerHitTest_nativeHasSearchMarkerAt(JNIEnv *, jclass, jfloat xPx,
                                                                                        jfloat yPx, jfloat radiusPx)
{
  if (!g_framework || !g_framework->IsDrapeEngineCreated() || radiusPx <= 0.0f)
    return JNI_FALSE;

  auto * framework = frm();
  if (framework == nullptr)
    return JNI_FALSE;

  // Convert the requested in-car screen-space touch target through the current model view. A rotated
  // map may make this global bounding box slightly more forgiving, which is preferable to a tiny POI
  // target on a direct-display head unit.
  auto const radius = static_cast<double>(radiusPx);
  m2::RectD globalRect;
  globalRect.Add(framework->PtoG({static_cast<double>(xPx) - radius, static_cast<double>(yPx) - radius}));
  globalRect.Add(framework->PtoG({static_cast<double>(xPx) + radius, static_cast<double>(yPx) - radius}));
  globalRect.Add(framework->PtoG({static_cast<double>(xPx) + radius, static_cast<double>(yPx) + radius}));
  globalRect.Add(framework->PtoG({static_cast<double>(xPx) - radius, static_cast<double>(yPx) + radius}));
  if (!globalRect.IsValid())
    return JNI_FALSE;

  m2::AnyRectD const touchRect(globalRect);
  auto const * mark = framework->GetBookmarkManager().FindNearestUserMark(
      [&touchRect](UserMark::Type) { return touchRect; }, [](UserMark::Type) { return true; } /* findOnlyVisible */);

  // FindNearestUserMark preserves the map's user-mark priority. A routing, road-warning, bookmark,
  // API or track mark overlapping the touch target therefore blocks the Quick tap rather than being
  // activated accidentally. Only the current SEARCH user-mark group is admitted.
  return mark != nullptr && mark->GetMarkType() == UserMark::Type::SEARCH ? JNI_TRUE : JNI_FALSE;
}
}  // extern "C"
