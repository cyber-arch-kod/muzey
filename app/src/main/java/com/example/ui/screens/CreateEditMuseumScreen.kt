package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.AddLocationAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.domain.model.DraftPhotoItem
import com.example.domain.model.LocationPin
import com.example.domain.model.RoomStyle
import com.example.i18n.UzStrings
import com.example.ui.components.MapLocationPickerDialog
import com.example.ui.viewmodel.CreateEditViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateEditMuseumScreen(
    editSlug: String? = null,
    onBack: () -> Unit,
    onPublished: (String) -> Unit,
    onOpenPricing: () -> Unit,
    viewModel: CreateEditViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val subscription by viewModel.subscription.collectAsStateWithLifecycle()
    val maxPhotos = subscription.maxPhotos
    val isPaidActive = subscription.isPaidActive()

    LaunchedEffect(editSlug) {
        viewModel.loadForEditIfNeeded(editSlug)
    }

    LaunchedEffect(state.publishedSlug) {
        val slug = state.publishedSlug
        if (!slug.isNullOrBlank()) {
            viewModel.clearPublishedSlug()
            onPublished(slug)
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 20)
    ) { uris ->
        viewModel.onPhotosPicked(uris)
    }

    var editingMuseumLocation by remember { mutableStateOf(false) }
    var editingPhotoPinTarget by remember { mutableStateOf<Pair<String, LocationPin?>?>(null) }

    if (editingMuseumLocation) {
        MapLocationPickerDialog(
            initialPin = state.museumLocation,
            onDismiss = { editingMuseumLocation = false },
            onSave = { pin -> viewModel.updateMuseumLocation(pin) }
        )
    }

    editingPhotoPinTarget?.let { (photoId, currentPin) ->
        MapLocationPickerDialog(
            initialPin = currentPin,
            onDismiss = { editingPhotoPinTarget = null },
            onSave = { pin -> viewModel.updatePhotoLocation(photoId, pin) }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (state.isEditMode) UzStrings.EDIT_TITLE else UzStrings.CREATE_TITLE,
                        style = MaterialTheme.typography.headlineMedium
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("create_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = UzStrings.VIEWER_BACK_TO_WALK
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                shadowElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    if (state.errorMessage != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = state.errorMessage.orEmpty(),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            if (state.limitReachedRequiresUpgrade) {
                                TextButton(onClick = onOpenPricing) {
                                    Text(UzStrings.OPEN_PRICING_BTN)
                                }
                            }
                        }
                    }
                    Button(
                        onClick = { viewModel.publish() },
                        enabled = !state.isCompressing && !state.isPublishing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("create_publish_button"),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        if (state.isPublishing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(UzStrings.PUBLISHING_PROGRESS)
                        } else {
                            Text(
                                text = if (state.isEditMode) UzStrings.SAVE_CHANGES_BTN else UzStrings.PUBLISH_BTN,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Section 1: Photos Header & Picker
                item {
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = String.format(UzStrings.SECTION_PHOTOS, maxPhotos),
                                    style = MaterialTheme.typography.titleLarge
                                )
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text(
                                        text = String.format(UzStrings.PHOTOS_COUNT_FORMAT, state.photos.size, maxPhotos),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = {
                                    if (state.photos.size >= maxPhotos && !isPaidActive) {
                                        onOpenPricing()
                                    } else {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    }
                                },
                                enabled = !state.isCompressing && (state.photos.size < maxPhotos || !isPaidActive),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("pick_photos_button"),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    if (state.photos.size >= maxPhotos && !isPaidActive) {
                                        UzStrings.OPEN_PRICING_BTN
                                    } else {
                                        UzStrings.PICK_PHOTOS_BTN
                                    }
                                )
                            }
                            if (state.isCompressing) {
                                Spacer(modifier = Modifier.height(10.dp))
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = UzStrings.COMPRESSING_PHOTOS,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Selected Photos List
                itemsIndexed(
                    items = state.photos,
                    key = { _, item -> item.id }
                ) { index, photo ->
                    DraftPhotoCard(
                        index = index,
                        total = state.photos.size,
                        item = photo,
                        onMoveUp = { viewModel.movePhotoUp(index) },
                        onMoveDown = { viewModel.movePhotoDown(index) },
                        onRemove = { viewModel.removePhoto(photo.id) },
                        onTitleChange = { viewModel.updatePhotoTitle(photo.id, it) },
                        onCaptionChange = { viewModel.updatePhotoCaption(photo.id, it) },
                        onPinClick = { editingPhotoPinTarget = photo.id to photo.locationPin }
                    )
                }

                // Section 2: Museum Details & Wedding Fields
                item {
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = UzStrings.SECTION_DETAILS,
                                style = MaterialTheme.typography.titleLarge
                            )
                            OutlinedTextField(
                                value = state.title,
                                onValueChange = viewModel::updateTitle,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("museum_title_input"),
                                label = { Text(UzStrings.MUSEUM_NAME_LABEL) },
                                placeholder = { Text(UzStrings.MUSEUM_NAME_HINT) },
                                singleLine = true,
                                shape = MaterialTheme.shapes.small
                            )
                            OutlinedTextField(
                                value = state.dedication,
                                onValueChange = viewModel::updateDedication,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("museum_dedication_input"),
                                label = { Text(UzStrings.DEDICATION_LABEL) },
                                placeholder = { Text(UzStrings.DEDICATION_HINT) },
                                singleLine = true,
                                shape = MaterialTheme.shapes.small
                            )

                            AnimatedVisibility(visible = state.roomStyle == RoomStyle.WEDDING) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedTextField(
                                        value = state.brideName,
                                        onValueChange = viewModel::updateBrideName,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("wedding_bride_input"),
                                        label = { Text(UzStrings.BRIDE_NAME_LABEL) },
                                        placeholder = { Text(UzStrings.BRIDE_NAME_HINT) },
                                        singleLine = true,
                                        shape = MaterialTheme.shapes.small
                                    )
                                    OutlinedTextField(
                                        value = state.groomName,
                                        onValueChange = viewModel::updateGroomName,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("wedding_groom_input"),
                                        label = { Text(UzStrings.GROOM_NAME_LABEL) },
                                        placeholder = { Text(UzStrings.GROOM_NAME_HINT) },
                                        singleLine = true,
                                        shape = MaterialTheme.shapes.small
                                    )
                                    OutlinedTextField(
                                        value = state.weddingDate,
                                        onValueChange = viewModel::updateWeddingDate,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("wedding_date_input"),
                                        label = { Text(UzStrings.WEDDING_DATE_LABEL) },
                                        placeholder = { Text(UzStrings.WEDDING_DATE_HINT) },
                                        singleLine = true,
                                        shape = MaterialTheme.shapes.small
                                    )
                                }
                            }

                            OutlinedButton(
                                onClick = { editingMuseumLocation = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                                    .testTag("museum_map_pin_button"),
                                shape = MaterialTheme.shapes.small
                            ) {
                                Icon(
                                    imageVector = if (state.museumLocation != null) {
                                        Icons.Default.LocationOn
                                    } else {
                                        Icons.Outlined.AddLocationAlt
                                    },
                                    contentDescription = null
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = state.museumLocation?.let { pin ->
                                        val label = pin.name.ifBlank {
                                            "${String.format("%.4f", pin.latitude)}, ${String.format("%.4f", pin.longitude)}"
                                        }
                                        "${UzStrings.LOCATION_SET_PREFIX} $label"
                                    } ?: UzStrings.ADD_MUSEUM_MAP_PIN
                                )
                            }
                        }
                    }
                }

                // Section 3: Room Style Selection (Free + Paid)
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = UzStrings.SECTION_STYLE,
                            style = MaterialTheme.typography.titleLarge
                        )
                        RoomStyle.entries.forEach { style ->
                            val locked = style.isPremium && !isPaidActive
                            RoomStyleOptionCard(
                                style = style,
                                selected = state.roomStyle == style,
                                locked = locked,
                                onClick = {
                                    if (locked) {
                                        onOpenPricing()
                                    } else {
                                        viewModel.updateRoomStyle(style)
                                    }
                                }
                            )
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(12.dp)) }
            }
        }
    }
}

@Composable
private fun DraftPhotoCard(
    index: Int,
    total: Int,
    item: DraftPhotoItem,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    onTitleChange: (String) -> Unit,
    onCaptionChange: (String) -> Unit,
    onPinClick: () -> Unit
) {
    val imageModel = remember(item.compressedFilePath, item.remoteUrl) {
        item.compressedFilePath?.let { File(it) } ?: item.remoteUrl
    }

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = imageModel,
                    contentDescription = item.title.ifBlank { "#${index + 1}" },
                    modifier = Modifier
                        .size(84.dp)
                        .clip(MaterialTheme.shapes.small),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "#${index + 1} • ${item.width}×${item.height}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = onMoveUp,
                            enabled = index > 0,
                            modifier = Modifier.testTag("photo_up_$index")
                        ) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = UzStrings.MOVE_UP_DESC)
                        }
                        IconButton(
                            onClick = onMoveDown,
                            enabled = index < total - 1,
                            modifier = Modifier.testTag("photo_down_$index")
                        ) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = UzStrings.MOVE_DOWN_DESC)
                        }
                        IconButton(
                            onClick = onRemove,
                            modifier = Modifier.testTag("photo_remove_$index")
                        ) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = UzStrings.REMOVE_PHOTO_DESC,
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = item.title,
                onValueChange = onTitleChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("photo_title_$index"),
                placeholder = { Text(UzStrings.PHOTO_TITLE_HINT) },
                singleLine = true,
                shape = MaterialTheme.shapes.small
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = item.caption,
                onValueChange = onCaptionChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("photo_caption_$index"),
                placeholder = { Text(UzStrings.PHOTO_CAPTION_HINT) },
                maxLines = 2,
                shape = MaterialTheme.shapes.small
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onPinClick,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("photo_pin_$index"),
                shape = MaterialTheme.shapes.small
            ) {
                Icon(
                    imageVector = if (item.locationPin != null) {
                        Icons.Default.LocationOn
                    } else {
                        Icons.Outlined.AddLocationAlt
                    },
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = item.locationPin?.let { pin ->
                        pin.name.ifBlank {
                            "${String.format("%.3f", pin.latitude)}, ${String.format("%.3f", pin.longitude)}"
                        }
                    } ?: UzStrings.PIN_LOCATION_BTN
                )
            }
        }
    }
}

@Composable
private fun RoomStyleOptionCard(
    style: RoomStyle,
    selected: Boolean,
    locked: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .testTag("room_style_${style.key}"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (style.previewResId != null) {
                Image(
                    painter = painterResource(id = style.previewResId),
                    contentDescription = style.title,
                    modifier = Modifier
                        .size(width = 92.dp, height = 68.dp)
                        .clip(MaterialTheme.shapes.small),
                    contentScale = ContentScale.Crop
                )
            } else {
                ProceduralStyleThumbnail(
                    style = style,
                    modifier = Modifier
                        .size(width = 92.dp, height = 68.dp)
                        .clip(MaterialTheme.shapes.small)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(text = style.title, style = MaterialTheme.typography.titleMedium)
                    if (locked) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.extraSmall
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = UzStrings.STYLE_LOCKED_BADGE,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = style.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (selected && !locked) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun ProceduralStyleThumbnail(
    style: RoomStyle,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (style == RoomStyle.ROMANTIC) {
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFF9E1E6), Color(0xFFF3DFD3))
                )
            )
            // Golden glowing frame silhouette
            drawRoundRect(
                color = Color(0x55FFD6BA),
                topLeft = Offset(size.width * 0.24f, size.height * 0.16f),
                size = Size(size.width * 0.52f, size.height * 0.62f),
                cornerRadius = CornerRadius(8f, 8f)
            )
            drawRoundRect(
                color = Color(0xFFD4AF7A),
                topLeft = Offset(size.width * 0.30f, size.height * 0.22f),
                size = Size(size.width * 0.40f, size.height * 0.50f),
                cornerRadius = CornerRadius(4f, 4f),
                style = Stroke(width = 4f)
            )
            // Floating petals
            drawCircle(Color(0xFFE88AA0), radius = 5f, center = Offset(size.width * 0.18f, size.height * 0.3f))
            drawCircle(Color(0xFFF4B6C2), radius = 4f, center = Offset(size.width * 0.82f, size.height * 0.25f))
            drawCircle(Color(0xFFE88AA0), radius = 4.5f, center = Offset(size.width * 0.78f, size.height * 0.74f))
        } else {
            // WEDDING preview
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFCFBF8), Color(0xFFEFE4D4))
                )
            )
            // Gold arch & plaque
            drawRoundRect(
                color = Color(0xFFC9A25E),
                topLeft = Offset(size.width * 0.22f, size.height * 0.16f),
                size = Size(size.width * 0.56f, size.height * 0.68f),
                cornerRadius = CornerRadius(32f, 32f),
                style = Stroke(width = 4f)
            )
            drawRect(
                color = Color(0xFFFDFBF6),
                topLeft = Offset(size.width * 0.32f, size.height * 0.36f),
                size = Size(size.width * 0.36f, size.height * 0.28f)
            )
            // Gold confetti dots
            drawCircle(Color(0xFFD4AF37), radius = 3.5f, center = Offset(size.width * 0.15f, size.height * 0.24f))
            drawCircle(Color(0xFFC9A25E), radius = 3f, center = Offset(size.width * 0.84f, size.height * 0.34f))
            drawCircle(Color(0xFFD4AF37), radius = 3.5f, center = Offset(size.width * 0.80f, size.height * 0.78f))
        }
    }
}
