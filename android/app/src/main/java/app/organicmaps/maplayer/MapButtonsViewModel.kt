package app.organicmaps.maplayer

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import app.organicmaps.BuildConfig
import app.organicmaps.sdk.location.TrackRecorder

class MapButtonsViewModel : ViewModel() {

    private val _buttonsHidden = MutableLiveData(false)
    val buttonsHidden: LiveData<Boolean> = _buttonsHidden

    private val _bottomButtonsHidden = MutableLiveData(false)
    val bottomButtonsHidden: LiveData<Boolean> = _bottomButtonsHidden

    private val _fullscreen = MutableLiveData(false)
    val fullscreen: LiveData<Boolean> = _fullscreen

    private var navigationFooterOwnsHeight = false
    private val _bottomButtonsHeight = MutableLiveData(0f)
    val bottomButtonsHeight: LiveData<Float> = _bottomButtonsHeight

    private val _topButtonsMarginTop = MutableLiveData(-1)
    val topButtonsMarginTop: LiveData<Int> = _topButtonsMarginTop

    private val _layoutMode = MutableLiveData(MapButtonsController.LayoutMode.regular)
    val layoutMode: LiveData<MapButtonsController.LayoutMode> = _layoutMode

    private val _myPositionMode = MutableLiveData<Int>()
    val myPositionMode: LiveData<Int> = _myPositionMode

    private val _searchOption = MutableLiveData<SearchWheel.SearchOption?>()
    val searchOption: LiveData<SearchWheel.SearchOption?> = _searchOption

    private val _trackRecorderState = MutableLiveData(TrackRecorder.nativeIsTrackRecordingEnabled())
    val trackRecorderState: LiveData<Boolean> = _trackRecorderState

    // Height of the top header (routing plan / navigation frame) the search sheet must clear when expanded.
    private val _topHeaderHeight = MutableLiveData(0)
    val topHeaderHeight: LiveData<Int> = _topHeaderHeight

    fun setButtonsHidden(buttonsHidden: Boolean) {
        _buttonsHidden.value = buttonsHidden
    }

    fun setBottomButtonsHidden(buttonsHidden: Boolean) {
        _bottomButtonsHidden.value = buttonsHidden
    }

    fun setFullscreen(fullscreen: Boolean) {
        _fullscreen.value = fullscreen
    }

    fun setBottomButtonsHeight(height: Float) {
        // InCar active navigation has a dedicated fixed route footer rather than the legacy map-buttons
        // bottom frame. NavigationController publishes that real footer height; do not let the absent
        // legacy frame overwrite it with zero while the navigation layout is active.
        if (
            BuildConfig.IS_IN_CAR &&
            navigationFooterOwnsHeight &&
            _layoutMode.value == MapButtonsController.LayoutMode.navigation &&
            height <= 0f
        ) {
            if ((_bottomButtonsHeight.value ?: 0f) > 0f) return
        }
        _bottomButtonsHeight.value = height
    }

    fun setNavigationFooterHeight(height: Float) {
        navigationFooterOwnsHeight = BuildConfig.IS_IN_CAR && height > 0f
        _bottomButtonsHeight.value = height.coerceAtLeast(0f)
    }

    fun setTopButtonsMarginTop(margin: Int) {
        // The InCar track control is part of the fixed zoom rail, not the legacy top status slot.
        // Preserve its explicit gap from +/- instead of translating it below navigation headers.
        if (!BuildConfig.IS_IN_CAR) {
            _topButtonsMarginTop.value = margin
        }
    }

    fun setLayoutMode(layoutMode: MapButtonsController.LayoutMode) {
        if (layoutMode != MapButtonsController.LayoutMode.navigation && navigationFooterOwnsHeight) {
            setNavigationFooterHeight(0f)
        }
        _layoutMode.value = layoutMode
    }

    fun setMyPositionMode(mode: Int) {
        _myPositionMode.value = mode
    }

    fun setSearchOption(searchOption: SearchWheel.SearchOption?) {
        _searchOption.value = searchOption
    }

    fun setTrackRecorderState(state: Boolean) {
        _trackRecorderState.value = state
    }

    fun setTopHeaderHeight(height: Int) {
        // Layout listeners call this on every pass; skip redundant updates so the search sheet's
        // expanded offset is recomputed only when the height actually changes.
        if (_topHeaderHeight.value != height) {
            _topHeaderHeight.value = height
        }
    }
}
