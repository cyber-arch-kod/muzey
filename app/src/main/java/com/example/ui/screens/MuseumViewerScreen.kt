package com.example.ui.screens

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.webkit.WebViewAssetLoader
import com.example.data.media.MuseumAudioManager
import com.example.domain.model.Museum
import com.example.domain.model.RoomStyle
import com.example.domain.model.WeddingWish
import com.example.i18n.UzStrings
import com.example.ui.components.MuseumsExploreMap
import com.example.ui.components.collectMapPins
import com.example.ui.viewmodel.ViewerContentState
import com.example.ui.viewmodel.ViewerViewModel
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
private data class JsMuseumPayload(
    val title: String,
    val dedication: String,
    val roomStyle: String,
    val brideName: String,
    val groomName: String,
    val weddingDate: String,
    val hintText: String,
    val photos: List<JsPhotoPayload>
)

@Serializable
private data class JsPhotoPayload(
    val url: String,
    val width: Int,
    val height: Int,
    val title: String,
    val caption: String
)

@Composable
fun MuseumViewerScreen(
    slug: String,
    onBack: () -> Unit,
    onOpenPricing: () -> Unit,
    viewModel: ViewerViewModel = viewModel()
) {
    val contentState by viewModel.contentState.collectAsStateWithLifecycle()
    val selectedPhotoIndex by viewModel.selectedPhotoIndex.collectAsStateWithLifecycle()
    val shadowsEnabled by viewModel.shadowsEnabled.collectAsStateWithLifecycle()
    val isMusicPlaying by viewModel.isMusicPlaying.collectAsStateWithLifecycle()
    val isMusicLoading by viewModel.isMusicLoading.collectAsStateWithLifecycle()
    val weddingWishes by viewModel.weddingWishes.collectAsStateWithLifecycle()
    val subscription by viewModel.subscription.collectAsStateWithLifecycle()

    var showMapModal by remember { mutableStateOf(false) }
    var showGuestbookModal by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val audioManager = remember(context) {
        MuseumAudioManager(
            context = context,
            onStateChanged = { loading, playing ->
                viewModel.setMusicState(loading, playing)
            },
            onUnavailable = {
                scope.launch {
                    snackbarHostState.showSnackbar(UzStrings.MUSIC_FILE_MISSING_HINT)
                }
            }
        )
    }

    DisposableEffect(audioManager) {
        onDispose {
            audioManager.release()
        }
    }

    LaunchedEffect(slug) {
        viewModel.loadMuseum(slug)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.navigationBarsPadding()
            )
        }
    ) { _ ->
        when (val state = contentState) {
            is ViewerContentState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(PaddingValues(24.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(UzStrings.VIEWER_LOADING, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            is ViewerContentState.NotFound, is ViewerContentState.Error -> {
                val msg = (state as? ViewerContentState.Error)?.message ?: UzStrings.VIEWER_NOT_FOUND
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(msg, style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = { viewModel.loadMuseum(slug) }) {
                                Text(UzStrings.VIEWER_RETRY)
                            }
                            OutlinedButton(onClick = onBack) {
                                Text(UzStrings.CLOSE_BTN)
                            }
                        }
                    }
                }
            }

            is ViewerContentState.Ready -> {
                val museum = state.museum

                if (state.isLockedBySubscription) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(WindowInsets.safeDrawing.asPaddingValuesSafe()),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            shape = MaterialTheme.shapes.extraLarge,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = UzStrings.VIEWER_LOCKED_TITLE,
                                    style = MaterialTheme.typography.headlineMedium,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = UzStrings.VIEWER_LOCKED_DESC,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                                Button(
                                    onClick = onOpenPricing,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .testTag("viewer_locked_upgrade_btn")
                                ) {
                                    Text(UzStrings.OPEN_PRICING_BTN)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = onBack,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(UzStrings.CLOSE_BTN)
                                }
                            }
                        }
                    }
                    return@Scaffold
                }

                val mapPins = remember(museum) { museum.collectMapPins() }
                var webViewRef by remember { mutableStateOf<WebView?>(null) }

                if (showMapModal) {
                    Dialog(
                        onDismissRequest = { showMapModal = false },
                        properties = DialogProperties(usePlatformDefaultWidth = false)
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = UzStrings.VIEWER_MAP_MODAL_TITLE,
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                    IconButton(onClick = { showMapModal = false }) {
                                        Icon(Icons.Default.Close, contentDescription = UzStrings.CLOSE_BTN)
                                    }
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    MuseumsExploreMap(pins = mapPins)
                                }
                            }
                        }
                    }
                }

                if (showGuestbookModal && museum.roomStyle == RoomStyle.WEDDING) {
                    WeddingGuestbookDialog(
                        wishes = weddingWishes,
                        onDismiss = { showGuestbookModal = false },
                        onSubmit = { name, msg ->
                            viewModel.submitWeddingWish(museum.id, name, msg) { ok ->
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        if (ok) UzStrings.GUESTBOOK_SAVED else UzStrings.GUESTBOOK_VALIDATION
                                    )
                                }
                            }
                        }
                    )
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    ThreeJsMuseumWebView(
                        museum = museum,
                        shadowsEnabled = shadowsEnabled,
                        onWebViewCreated = { webViewRef = it },
                        onArtworkSelected = { idx -> viewModel.selectArtwork(idx) },
                        onArtworkClosed = { viewModel.clearSelectedArtwork() },
                        onGuestbookOpened = { showGuestbookModal = true }
                    )

                    // Top Floating Museum HUD
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xAA1F1C18)
                        ) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.testTag("viewer_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = UzStrings.CLOSE_BTN,
                                    tint = Color.White
                                )
                            }
                        }

                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = Color(0xAA1F1C18),
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .padding(horizontal = 8.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = museum.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    maxLines = 1
                                )
                                val subtitleText = if (museum.roomStyle == RoomStyle.WEDDING &&
                                    (museum.brideName.isNotBlank() || museum.groomName.isNotBlank())
                                ) {
                                    "${museum.brideName} & ${museum.groomName}"
                                } else {
                                    museum.dedication
                                }
                                if (subtitleText.isNotBlank()) {
                                    Text(
                                        text = subtitleText,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color(0xFFE6C79C),
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Music toggle slot (Romantik / To'y / Paid plans)
                            if (subscription.hasMusicFeature || museum.roomStyle.isPremium) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xAA1F1C18)
                                ) {
                                    IconButton(
                                        onClick = { audioManager.toggle(museum.roomStyle) },
                                        enabled = !isMusicLoading,
                                        modifier = Modifier.testTag("viewer_music_toggle")
                                    ) {
                                        if (isMusicLoading) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                strokeWidth = 2.dp,
                                                color = Color(0xFFE6C79C)
                                            )
                                        } else {
                                            Icon(
                                                imageVector = if (isMusicPlaying) Icons.Default.MusicNote else Icons.Default.MusicOff,
                                                contentDescription = if (isMusicPlaying) UzStrings.MUSIC_TOGGLE_ON else UzStrings.MUSIC_TOGGLE_OFF,
                                                tint = if (isMusicPlaying) Color(0xFFE6C79C) else Color.White.copy(alpha = 0.7f)
                                            )
                                        }
                                    }
                                }
                            }

                            if (mapPins.isNotEmpty()) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xAA1F1C18)
                                ) {
                                    IconButton(
                                        onClick = { showMapModal = true },
                                        modifier = Modifier.testTag("viewer_map_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Map,
                                            contentDescription = UzStrings.VIEWER_OPEN_MAP,
                                            tint = Color.White
                                        )
                                    }
                                }
                            }
                            Surface(
                                shape = CircleShape,
                                color = Color(0xAA1F1C18)
                            ) {
                                IconButton(
                                    onClick = {
                                        viewModel.toggleShadows()
                                        val nextVal = !shadowsEnabled
                                        webViewRef?.evaluateJavascript("window.setShadowsEnabled($nextVal);", null)
                                    },
                                    modifier = Modifier.testTag("viewer_shadows_toggle")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.WbSunny,
                                        contentDescription = if (shadowsEnabled) UzStrings.VIEWER_SHADOWS_ON else UzStrings.VIEWER_SHADOWS_OFF,
                                        tint = if (shadowsEnabled) Color(0xFFE6C79C) else Color.White.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }

                    // Wedding Guestbook floating button when no artwork is focused
                    if (museum.roomStyle == RoomStyle.WEDDING && selectedPhotoIndex == null) {
                        ExtendedFloatingActionButton(
                            onClick = { showGuestbookModal = true },
                            icon = { Icon(Icons.Default.EditNote, contentDescription = null) },
                            text = {
                                Text(
                                    if (weddingWishes.isNotEmpty()) {
                                        "${UzStrings.GUESTBOOK_BTN} (${weddingWishes.size})"
                                    } else {
                                        UzStrings.GUESTBOOK_BTN
                                    }
                                )
                            },
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                            contentColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .navigationBarsPadding()
                                .padding(end = 16.dp, bottom = 92.dp)
                                .testTag("viewer_guestbook_fab")
                        )
                    }

                    // Bottom Artwork Detail Card when tapped
                    val focusedPhoto = selectedPhotoIndex?.let { museum.photos.getOrNull(it) }
                    AnimatedVisibility(
                        visible = focusedPhoto != null,
                        enter = slideInVertically { it } + fadeIn(),
                        exit = slideOutVertically { it } + fadeOut(),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(16.dp)
                    ) {
                        focusedPhoto?.let { photo ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.large,
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                            ) {
                                Column(modifier = Modifier.padding(18.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = photo.title.ifBlank { UzStrings.VIEWER_UNTITLED_PHOTO },
                                            style = MaterialTheme.typography.headlineMedium,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = {
                                                webViewRef?.evaluateJavascript("window.exitFocusMode();", null)
                                                viewModel.clearSelectedArtwork()
                                            },
                                            modifier = Modifier.testTag("viewer_close_artwork_button")
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = UzStrings.VIEWER_BACK_TO_WALK)
                                        }
                                    }
                                    if (photo.caption.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = photo.caption,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (photo.locationName.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.LocationOn,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = photo.locationName,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = UzStrings.VIEWER_TAP_AGAIN_HINT,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeddingGuestbookDialog(
    wishes: List<WeddingWish>,
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Unit
) {
    var guestName by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = UzStrings.GUESTBOOK_TITLE,
                        style = MaterialTheme.typography.headlineMedium
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = UzStrings.CLOSE_BTN)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = guestName,
                    onValueChange = { guestName = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("guestbook_name_input"),
                    label = { Text(UzStrings.GUESTBOOK_NAME_HINT) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("guestbook_message_input"),
                    label = { Text(UzStrings.GUESTBOOK_MESSAGE_HINT) },
                    maxLines = 3,
                    shape = MaterialTheme.shapes.small
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = {
                        if (guestName.isNotBlank() && message.isNotBlank()) {
                            onSubmit(guestName, message)
                            guestName = ""
                            message = ""
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("guestbook_submit_btn"),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(UzStrings.GUESTBOOK_SUBMIT_BTN)
                }

                Spacer(modifier = Modifier.height(16.dp))
                if (wishes.isEmpty()) {
                    Text(
                        text = UzStrings.GUESTBOOK_EMPTY,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(wishes, key = { it.id }) { wish ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                ),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = wish.guestName,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = wish.message,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WindowInsets.asPaddingValuesSafe() = PaddingValues(24.dp)

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ThreeJsMuseumWebView(
    museum: Museum,
    shadowsEnabled: Boolean,
    onWebViewCreated: (WebView) -> Unit,
    onArtworkSelected: (Int) -> Unit,
    onArtworkClosed: () -> Unit,
    onGuestbookOpened: () -> Unit
) {
    val context = LocalContext.current
    val cacheDir = remember { File(context.filesDir, "museums_cache").apply { mkdirs() } }
    val assetLoader = remember {
        WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
            .addPathHandler("/cache/", WebViewAssetLoader.InternalStoragePathHandler(context, cacheDir))
            .build()
    }

    val payloadJson = remember(museum) {
        val payload = JsMuseumPayload(
            title = museum.title,
            dedication = museum.dedication,
            roomStyle = museum.roomStyle.key,
            brideName = museum.brideName,
            groomName = museum.groomName,
            weddingDate = museum.weddingDate,
            hintText = UzStrings.VIEWER_HINT,
            photos = museum.photos.map { p ->
                val url = when {
                    p.localPath != null && File(p.localPath).exists() -> {
                        "https://appassets.androidplatform.net/cache/${File(p.localPath).name}"
                    }
                    else -> p.imageUrl
                }
                JsPhotoPayload(
                    url = url,
                    width = p.width,
                    height = p.height,
                    title = p.title,
                    caption = p.caption
                )
            }
        )
        Json.encodeToString(payload)
    }

    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            webViewInstance?.destroy()
        }
    }

    AndroidView(
        modifier = Modifier
            .fillMaxSize()
            .testTag("threejs_webview"),
        factory = { ctx ->
            WebView(ctx).apply {
                webViewInstance = this
                onWebViewCreated(this)
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                }
                setBackgroundColor(android.graphics.Color.parseColor("#F6F1EA"))

                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun onViewerReady() {
                            post {
                                val escaped = Json.encodeToString(payloadJson)
                                evaluateJavascript("window.loadMuseum($escaped);", null)
                                evaluateJavascript("window.setShadowsEnabled($shadowsEnabled);", null)
                            }
                        }

                        @JavascriptInterface
                        fun onArtworkSelected(index: Int) {
                            post { onArtworkSelected(index) }
                        }

                        @JavascriptInterface
                        fun onArtworkClosed() {
                            post { onArtworkClosed() }
                        }

                        @JavascriptInterface
                        fun onGuestbookOpened() {
                            post { onGuestbookOpened() }
                        }
                    },
                    "AndroidBridge"
                )

                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest
                    ): WebResourceResponse? {
                        return assetLoader.shouldInterceptRequest(request.url)
                    }
                }

                loadUrl("https://appassets.androidplatform.net/assets/museum.html")
            }
        }
    )
}
