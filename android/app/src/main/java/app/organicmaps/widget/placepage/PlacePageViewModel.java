package app.organicmaps.widget.placepage;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import app.organicmaps.sdk.bookmarks.data.Bookmark;
import app.organicmaps.sdk.bookmarks.data.ElevationInfo;
import app.organicmaps.sdk.bookmarks.data.MapObject;
import app.organicmaps.sdk.bookmarks.data.Track;
import java.util.List;

public class PlacePageViewModel extends ViewModel
{
  private final MutableLiveData<List<PlacePageButtons.ButtonType>> mCurrentButtons = new MutableLiveData<>();
  private final MutableLiveData<MapObject> mMapObject = new MutableLiveData<>();
  private final MutableLiveData<Integer> mPlacePageWidth = new MutableLiveData<>();
  private final MutableLiveData<Integer> mPlacePageDistanceToTop = new MutableLiveData<>();
  public boolean isAlertDialogShowing = false;
  private long mSelectionGeneration;
  private int mActionSlots = 4;
  public int getActionSlots()
  {
    return mActionSlots;
  }
  public void setActionSlots(int slots)
  {
    mActionSlots = slots;
  }
  public long getSelectionGeneration()
  {
    return mSelectionGeneration;
  }

  public LiveData<List<PlacePageButtons.ButtonType>> getCurrentButtons()
  {
    return mCurrentButtons;
  }

  public void setCurrentButtons(List<PlacePageButtons.ButtonType> buttons)
  {
    mCurrentButtons.setValue(buttons);
  }

  public LiveData<MapObject> getMapObject()
  {
    return mMapObject;
  }

  public void setMapObject(MapObject mapObject)
  {
    final MapObject previous = mMapObject.getValue();
    final boolean changedTrack = previous instanceof Track previousTrack && mapObject instanceof Track nextTrack
                              && previousTrack.getTrackId() != nextTrack.getTrackId();
    if (!MapObject.same(previous, mapObject) || changedTrack)
      ++mSelectionGeneration;
    mMapObject.setValue(mapObject);
  }

  public LiveData<Integer> getPlacePageWidth()
  {
    return mPlacePageWidth;
  }

  public void setPlacePageWidth(int width)
  {
    mPlacePageWidth.setValue(width);
  }

  public LiveData<Integer> getPlacePageDistanceToTop()
  {
    return mPlacePageDistanceToTop;
  }

  public void setPlacePageDistanceToTop(int top)
  {
    mPlacePageDistanceToTop.setValue(top);
  }
}
