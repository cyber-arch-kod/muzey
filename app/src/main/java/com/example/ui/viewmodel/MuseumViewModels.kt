package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.config.PricingConfig
import com.example.data.media.ImageCompressor
import com.example.data.repository.MuseumRepository
import com.example.data.repository.SubscriptionRepository
import com.example.domain.model.DraftPhotoItem
import com.example.domain.model.LocationPin
import com.example.domain.model.Museum
import com.example.domain.model.RoomStyle
import com.example.domain.model.SubscriptionPlan
import com.example.domain.model.UserSubscription
import com.example.domain.model.WeddingWish
import com.example.i18n.UzStrings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CreateEditUiState(
    val isEditMode: Boolean = false,
    val existingMuseumId: String? = null,
    val existingSlug: String? = null,
    val existingEditToken: String? = null,
    val title: String = "",
    val dedication: String = "",
    val brideName: String = "",
    val groomName: String = "",
    val weddingDate: String = "",
    val roomStyle: RoomStyle = RoomStyle.CLASSIC_WHITE,
    val museumLocation: LocationPin? = null,
    val photos: List<DraftPhotoItem> = emptyList(),
    val isCompressing: Boolean = false,
    val isPublishing: Boolean = false,
    val errorMessage: String? = null,
    val limitReachedRequiresUpgrade: Boolean = false,
    val publishedSlug: String? = null
)

class CreateEditViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MuseumRepository(application)
    private val subscriptionRepository = SubscriptionRepository(application)
    private val compressor = ImageCompressor(application)

    private val _uiState = MutableStateFlow(CreateEditUiState())
    val uiState: StateFlow<CreateEditUiState> = _uiState.asStateFlow()

    val subscription: StateFlow<UserSubscription> = subscriptionRepository.observeSubscription()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserSubscription(userId = subscriptionRepository.deviceUserId)
        )

    init {
        viewModelScope.launch {
            subscriptionRepository.getCurrentSubscription()
        }
    }

    fun loadForEditIfNeeded(slug: String?) {
        if (slug.isNullOrBlank()) return
        if (_uiState.value.existingSlug == slug) return
        viewModelScope.launch {
            repository.getMuseumBySlug(slug).onSuccess { museum ->
                if (museum != null) {
                    _uiState.update {
                        it.copy(
                            isEditMode = true,
                            existingMuseumId = museum.id,
                            existingSlug = museum.slug,
                            existingEditToken = museum.editToken,
                            title = museum.title,
                            dedication = museum.dedication,
                            brideName = museum.brideName,
                            groomName = museum.groomName,
                            weddingDate = museum.weddingDate,
                            roomStyle = museum.roomStyle,
                            museumLocation = museum.locationPin,
                            photos = museum.photos.map { p ->
                                DraftPhotoItem(
                                    id = p.id,
                                    compressedFilePath = p.localPath,
                                    remoteUrl = p.imageUrl.ifBlank { null },
                                    width = p.width,
                                    height = p.height,
                                    title = p.title,
                                    caption = p.caption,
                                    locationPin = p.locationPin
                                )
                            },
                            errorMessage = null,
                            limitReachedRequiresUpgrade = false
                        )
                    }
                }
            }
        }
    }

    fun onPhotosPicked(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val maxPhotos = subscription.value.maxPhotos
        val currentCount = _uiState.value.photos.size
        val remainingSlots = (maxPhotos - currentCount).coerceAtLeast(0)
        if (remainingSlots == 0) {
            val isFree = !subscription.value.isPaidActive()
            _uiState.update {
                it.copy(
                    errorMessage = if (isFree) {
                        UzStrings.LIMIT_FREE_PHOTOS_REACHED
                    } else {
                        String.format(UzStrings.VALIDATION_MAX_PHOTOS_FMT, maxPhotos)
                    },
                    limitReachedRequiresUpgrade = isFree
                )
            }
            return
        }
        val toProcess = uris.take(remainingSlots)
        viewModelScope.launch {
            _uiState.update { it.copy(isCompressing = true, errorMessage = null, limitReachedRequiresUpgrade = false) }
            val processed = mutableListOf<DraftPhotoItem>()
            for (uri in toProcess) {
                compressor.compressToWebP(uri).onSuccess { res ->
                    processed.add(
                        DraftPhotoItem(
                            compressedFilePath = res.filePath,
                            width = res.width,
                            height = res.height
                        )
                    )
                }
            }
            _uiState.update { state ->
                val exceeded = uris.size > remainingSlots
                val isFree = !subscription.value.isPaidActive()
                val warnMax = if (exceeded) {
                    if (isFree) UzStrings.LIMIT_FREE_PHOTOS_REACHED
                    else String.format(UzStrings.VALIDATION_MAX_PHOTOS_FMT, maxPhotos)
                } else null
                state.copy(
                    photos = state.photos + processed,
                    isCompressing = false,
                    errorMessage = warnMax,
                    limitReachedRequiresUpgrade = exceeded && isFree
                )
            }
        }
    }

    fun removePhoto(id: String) {
        _uiState.update { state ->
            state.copy(
                photos = state.photos.filterNot { it.id == id },
                errorMessage = null,
                limitReachedRequiresUpgrade = false
            )
        }
    }

    fun movePhotoUp(index: Int) {
        if (index <= 0) return
        _uiState.update { state ->
            val list = state.photos.toMutableList()
            val item = list.removeAt(index)
            list.add(index - 1, item)
            state.copy(photos = list)
        }
    }

    fun movePhotoDown(index: Int) {
        _uiState.update { state ->
            if (index >= state.photos.lastIndex) return@update state
            val list = state.photos.toMutableList()
            val item = list.removeAt(index)
            list.add(index + 1, item)
            state.copy(photos = list)
        }
    }

    fun updatePhotoTitle(id: String, title: String) {
        _uiState.update { state ->
            state.copy(photos = state.photos.map { if (it.id == id) it.copy(title = title) else it })
        }
    }

    fun updatePhotoCaption(id: String, caption: String) {
        _uiState.update { state ->
            state.copy(photos = state.photos.map { if (it.id == id) it.copy(caption = caption) else it })
        }
    }

    fun updatePhotoLocation(id: String, pin: LocationPin?) {
        _uiState.update { state ->
            state.copy(photos = state.photos.map { if (it.id == id) it.copy(locationPin = pin) else it })
        }
    }

    fun updateTitle(value: String) {
        _uiState.update { it.copy(title = value, errorMessage = null) }
    }

    fun updateDedication(value: String) {
        _uiState.update { it.copy(dedication = value) }
    }

    fun updateBrideName(value: String) {
        _uiState.update { it.copy(brideName = value, errorMessage = null) }
    }

    fun updateGroomName(value: String) {
        _uiState.update { it.copy(groomName = value, errorMessage = null) }
    }

    fun updateWeddingDate(value: String) {
        _uiState.update { it.copy(weddingDate = value) }
    }

    fun updateRoomStyle(style: RoomStyle): Boolean {
        if (style.isPremium && !subscription.value.isPaidActive()) {
            return false
        }
        _uiState.update { it.copy(roomStyle = style, errorMessage = null) }
        return true
    }

    fun updateMuseumLocation(pin: LocationPin?) {
        _uiState.update { it.copy(museumLocation = pin) }
    }

    fun clearPublishedSlug() {
        _uiState.update { it.copy(publishedSlug = null) }
    }

    fun publish() {
        val state = _uiState.value
        val activeSub = subscription.value
        val maxPhotos = activeSub.maxPhotos

        if (state.photos.size < 3) {
            _uiState.update { it.copy(errorMessage = UzStrings.VALIDATION_MIN_PHOTOS) }
            return
        }
        if (state.photos.size > maxPhotos) {
            _uiState.update {
                it.copy(
                    errorMessage = if (!activeSub.isPaidActive()) {
                        UzStrings.LIMIT_FREE_PHOTOS_REACHED
                    } else {
                        String.format(UzStrings.VALIDATION_MAX_PHOTOS_FMT, maxPhotos)
                    },
                    limitReachedRequiresUpgrade = !activeSub.isPaidActive()
                )
            }
            return
        }
        if (state.title.isBlank()) {
            _uiState.update { it.copy(errorMessage = UzStrings.VALIDATION_NAME_REQUIRED) }
            return
        }
        if (state.roomStyle == RoomStyle.WEDDING && (state.brideName.isBlank() || state.groomName.isBlank())) {
            _uiState.update { it.copy(errorMessage = UzStrings.VALIDATION_WEDDING_REQUIRED) }
            return
        }
        if (state.roomStyle.isPremium && !activeSub.isPaidActive()) {
            _uiState.update {
                it.copy(
                    errorMessage = UzStrings.VIEWER_LOCKED_DESC,
                    limitReachedRequiresUpgrade = true
                )
            }
            return
        }

        viewModelScope.launch {
            if (!state.isEditMode && !activeSub.isPaidActive()) {
                val ownedCount = repository.countOwnedMuseums()
                if (ownedCount >= PricingConfig.FREE_MAX_MUSEUMS) {
                    _uiState.update {
                        it.copy(
                            errorMessage = UzStrings.LIMIT_FREE_MUSEUMS_REACHED,
                            limitReachedRequiresUpgrade = true
                        )
                    }
                    return@launch
                }
            }

            _uiState.update { it.copy(isPublishing = true, errorMessage = null, limitReachedRequiresUpgrade = false) }
            val result = repository.saveMuseum(
                existingMuseumId = state.existingMuseumId,
                existingSlug = state.existingSlug,
                existingEditToken = state.existingEditToken,
                ownerUserId = activeSub.userId,
                title = state.title,
                dedication = state.dedication,
                roomStyle = state.roomStyle,
                brideName = state.brideName,
                groomName = state.groomName,
                weddingDate = state.weddingDate,
                museumLocation = state.museumLocation,
                draftPhotos = state.photos
            )
            result.onSuccess { saved ->
                _uiState.update {
                    it.copy(
                        isPublishing = false,
                        publishedSlug = saved.slug
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isPublishing = false,
                        errorMessage = err.localizedMessage ?: "Xatolik yuz berdi"
                    )
                }
            }
        }
    }
}

sealed interface ViewerContentState {
    data object Loading : ViewerContentState
    data class Ready(val museum: Museum, val isLockedBySubscription: Boolean) : ViewerContentState
    data object NotFound : ViewerContentState
    data class Error(val message: String) : ViewerContentState
}

class ViewerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MuseumRepository(application)
    private val subscriptionRepository = SubscriptionRepository(application)

    private val _contentState = MutableStateFlow<ViewerContentState>(ViewerContentState.Loading)
    val contentState: StateFlow<ViewerContentState> = _contentState.asStateFlow()

    private val _selectedPhotoIndex = MutableStateFlow<Int?>(null)
    val selectedPhotoIndex: StateFlow<Int?> = _selectedPhotoIndex.asStateFlow()

    private val _shadowsEnabled = MutableStateFlow(true)
    val shadowsEnabled: StateFlow<Boolean> = _shadowsEnabled.asStateFlow()

    private val _isMusicPlaying = MutableStateFlow(false)
    val isMusicPlaying: StateFlow<Boolean> = _isMusicPlaying.asStateFlow()

    private val _isMusicLoading = MutableStateFlow(false)
    val isMusicLoading: StateFlow<Boolean> = _isMusicLoading.asStateFlow()

    private val _weddingWishes = MutableStateFlow<List<WeddingWish>>(emptyList())
    val weddingWishes: StateFlow<List<WeddingWish>> = _weddingWishes.asStateFlow()

    val subscription: StateFlow<UserSubscription> = subscriptionRepository.observeSubscription()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserSubscription(userId = subscriptionRepository.deviceUserId)
        )

    fun loadMuseum(slug: String) {
        viewModelScope.launch {
            _contentState.value = ViewerContentState.Loading
            val sub = subscriptionRepository.getCurrentSubscription()
            repository.getMuseumBySlug(slug)
                .onSuccess { museum ->
                    if (museum != null) {
                        val locked = museum.roomStyle.isPremium && !sub.isPaidActive()
                        _contentState.value = ViewerContentState.Ready(museum, locked)
                        if (museum.roomStyle == RoomStyle.WEDDING) {
                            launch {
                                repository.observeWeddingWishes(museum.id).collect { wishes ->
                                    _weddingWishes.value = wishes
                                }
                            }
                            launch {
                                repository.refreshWeddingWishes(museum.id)
                            }
                        }
                    } else {
                        _contentState.value = ViewerContentState.NotFound
                    }
                }
                .onFailure { err ->
                    _contentState.value = ViewerContentState.Error(
                        err.localizedMessage ?: UzStrings.VIEWER_NOT_FOUND
                    )
                }
        }
    }

    fun selectArtwork(index: Int) {
        _selectedPhotoIndex.value = index
    }

    fun clearSelectedArtwork() {
        _selectedPhotoIndex.value = null
    }

    fun toggleShadows() {
        _shadowsEnabled.update { !it }
    }

    fun setMusicState(loading: Boolean, playing: Boolean) {
        _isMusicLoading.value = loading
        _isMusicPlaying.value = playing
    }

    fun submitWeddingWish(museumId: String, guestName: String, message: String, onDone: (Boolean) -> Unit) {
        if (guestName.isBlank() || message.isBlank()) {
            onDone(false)
            return
        }
        viewModelScope.launch {
            repository.submitWeddingWish(museumId, guestName, message)
                .onSuccess { onDone(true) }
                .onFailure { onDone(false) }
        }
    }
}

class MyMuseumsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MuseumRepository(application)
    private val subscriptionRepository = SubscriptionRepository(application)

    val museums: StateFlow<List<Museum>> = repository.ownedMuseums
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val subscription: StateFlow<UserSubscription> = subscriptionRepository.observeSubscription()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserSubscription(userId = subscriptionRepository.deviceUserId)
        )

    init {
        viewModelScope.launch {
            subscriptionRepository.getCurrentSubscription()
        }
    }

    fun deleteMuseum(museum: Museum) {
        viewModelScope.launch {
            repository.deleteMuseum(museum)
        }
    }
}

class ShareViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MuseumRepository(application)

    private val _museum = MutableStateFlow<Museum?>(null)
    val museum: StateFlow<Museum?> = _museum.asStateFlow()

    fun load(slug: String) {
        viewModelScope.launch {
            repository.getMuseumBySlug(slug).onSuccess {
                _museum.value = it
            }
        }
    }
}

class PricingViewModel(application: Application) : AndroidViewModel(application) {
    private val subscriptionRepository = SubscriptionRepository(application)

    val subscription: StateFlow<UserSubscription> = subscriptionRepository.observeSubscription()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserSubscription(userId = subscriptionRepository.deviceUserId)
        )

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    init {
        viewModelScope.launch {
            subscriptionRepository.getCurrentSubscription()
        }
    }

    fun selectTestPlan(plan: SubscriptionPlan) {
        viewModelScope.launch {
            subscriptionRepository.activateTestPlan(plan)
                .onSuccess { sub ->
                    _statusMessage.value = "${sub.plan.displayName} faollashtirildi"
                }
                .onFailure { err ->
                    _statusMessage.value = err.localizedMessage
                }
        }
    }

    fun simulateExpiry() {
        viewModelScope.launch {
            subscriptionRepository.simulateExpiryForTest()
                .onSuccess {
                    _statusMessage.value = UzStrings.EXPIRED_PLAN_BADGE
                }
        }
    }

    fun clearMessage() {
        _statusMessage.value = null
    }
}
