package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.remote.SecretsConfig
import com.example.domain.model.LocationPin
import com.example.domain.model.Museum
import com.example.i18n.UzStrings
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState

@Composable
fun MapLocationPickerDialog(
    initialPin: LocationPin?,
    onDismiss: () -> Unit,
    onSave: (LocationPin?) -> Unit
) {
    // Default centered on Tashkent, Uzbekistan
    val defaultLatLng = remember(initialPin) {
        if (initialPin != null) {
            LatLng(initialPin.latitude, initialPin.longitude)
        } else {
            LatLng(41.2995, 69.2401)
        }
    }

    var selectedLatLng by remember { mutableStateOf(initialPin?.let { LatLng(it.latitude, it.longitude) }) }
    var locationName by remember { mutableStateOf(initialPin?.name.orEmpty()) }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultLatLng, if (initialPin != null) 12f else 6f)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = UzStrings.MAP_PICKER_TITLE,
                        style = MaterialTheme.typography.titleLarge
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = UzStrings.CLOSE_BTN)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (SecretsConfig.isMapsConfigured) {
                    Text(
                        text = UzStrings.MAP_PICKER_HINT,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(270.dp)
                            .clip(MaterialTheme.shapes.medium)
                    ) {
                        GoogleMap(
                            modifier = Modifier.fillMaxSize(),
                            cameraPositionState = cameraPositionState,
                            onMapClick = { latLng ->
                                selectedLatLng = latLng
                            }
                        ) {
                            selectedLatLng?.let { coords ->
                                Marker(
                                    state = MarkerState(position = coords),
                                    title = locationName.ifBlank { UzStrings.MUSEUM_LOCATION_LABEL }
                                )
                            }
                        }
                    }
                } else {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Outlined.Map,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = UzStrings.MAP_NO_KEY_TITLE,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = UzStrings.MAP_NO_KEY_DESC,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = locationName,
                    onValueChange = { locationName = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("map_location_name_input"),
                    label = { Text(UzStrings.MAP_LOCATION_NAME_HINT) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small
                )

                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        onClick = {
                            onSave(null)
                            onDismiss()
                        },
                        modifier = Modifier.testTag("map_clear_button")
                    ) {
                        Text(UzStrings.CLEAR_LOCATION)
                    }
                    Button(
                        onClick = {
                            val pin = selectedLatLng?.let {
                                LocationPin(it.latitude, it.longitude, locationName.trim())
                            } ?: if (locationName.isNotBlank()) {
                                LocationPin(defaultLatLng.latitude, defaultLatLng.longitude, locationName.trim())
                            } else null
                            onSave(pin)
                            onDismiss()
                        },
                        modifier = Modifier.testTag("map_save_button")
                    ) {
                        Text(UzStrings.MAP_SAVE_PIN)
                    }
                }
            }
        }
    }
}

data class MapPinDisplay(
    val id: String,
    val title: String,
    val subtitle: String,
    val latitude: Double,
    val longitude: Double,
    val museumSlug: String? = null
)

@Composable
fun MuseumsExploreMap(
    pins: List<MapPinDisplay>,
    modifier: Modifier = Modifier,
    onOpenMuseum: ((String) -> Unit)? = null
) {
    if (!SecretsConfig.isMapsConfigured) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Map,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(UzStrings.MAP_NO_KEY_TITLE, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = UzStrings.MAP_NO_KEY_DESC,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        return
    }

    if (pins.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = UzStrings.MAP_NO_PINS_YET,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    val initialLatLng = LatLng(pins.first().latitude, pins.first().longitude)
    val cameraState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(initialLatLng, 6.5f)
    }
    var activePin by remember { mutableStateOf<MapPinDisplay?>(pins.firstOrNull()) }

    Box(modifier = modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraState
        ) {
            pins.forEach { pin ->
                Marker(
                    state = MarkerState(position = LatLng(pin.latitude, pin.longitude)),
                    title = pin.title,
                    snippet = pin.subtitle,
                    onClick = {
                        activePin = pin
                        false
                    }
                )
            }
        }

        activePin?.let { selected ->
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(selected.title, style = MaterialTheme.typography.titleMedium)
                        if (selected.subtitle.isNotBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = selected.subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (selected.museumSlug != null && onOpenMuseum != null) {
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(onClick = { onOpenMuseum(selected.museumSlug) }) {
                            Text(UzStrings.ACTION_OPEN)
                        }
                    }
                }
            }
        }
    }
}

fun Museum.collectMapPins(): List<MapPinDisplay> {
    val result = mutableListOf<MapPinDisplay>()
    if (latitude != null && longitude != null) {
        result.add(
            MapPinDisplay(
                id = "museum-$id",
                title = title,
                subtitle = locationName.ifBlank { dedication },
                latitude = latitude,
                longitude = longitude,
                museumSlug = slug
            )
        )
    }
    photos.forEachIndexed { idx, p ->
        if (p.latitude != null && p.longitude != null) {
            result.add(
                MapPinDisplay(
                    id = "photo-${p.id}",
                    title = p.title.ifBlank { "${UzStrings.VIEWER_UNTITLED_PHOTO} #${idx + 1}" },
                    subtitle = p.locationName.ifBlank { p.caption },
                    latitude = p.latitude,
                    longitude = p.longitude,
                    museumSlug = slug
                )
            )
        }
    }
    return result
}

@Composable
fun SetupGuideDialog(onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = UzStrings.SETUP_DIALOG_TITLE,
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = UzStrings.SETUP_DIALOG_BODY,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = SecretsConfig.SUPABASE_SETUP_SQL.trim(),
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp),
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace)
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(SecretsConfig.SUPABASE_SETUP_SQL.trim()))
                            copied = true
                        }
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (copied) UzStrings.SQL_COPIED else UzStrings.COPY_SQL_BTN)
                    }
                    Button(onClick = onDismiss) {
                        Text(UzStrings.CLOSE_BTN)
                    }
                }
            }
        }
    }
}
