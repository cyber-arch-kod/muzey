package com.example.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.domain.model.Museum
import com.example.i18n.UzStrings
import com.example.ui.components.MuseumsExploreMap
import com.example.ui.components.collectMapPins
import com.example.ui.viewmodel.MyMuseumsViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyMuseumsScreen(
    onBack: () -> Unit,
    onCreateNew: () -> Unit,
    onOpenViewer: (String) -> Unit,
    onShareMuseum: (String) -> Unit,
    onEditMuseum: (String) -> Unit,
    onOpenPricing: () -> Unit,
    viewModel: MyMuseumsViewModel = viewModel()
) {
    val museums by viewModel.museums.collectAsStateWithLifecycle()
    val subscription by viewModel.subscription.collectAsStateWithLifecycle()
    val isPaidActive = subscription.isPaidActive()

    var selectedTab by remember { mutableIntStateOf(0) }
    var deleteCandidate by remember { mutableStateOf<Museum?>(null) }

    deleteCandidate?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text(UzStrings.DELETE_CONFIRM_TITLE) },
            text = { Text(String.format(UzStrings.DELETE_CONFIRM_BODY, target.title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteMuseum(target)
                        deleteCandidate = null
                    },
                    modifier = Modifier.testTag("confirm_delete_button")
                ) {
                    Text(UzStrings.CONFIRM_YES, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) {
                    Text(UzStrings.CONFIRM_CANCEL)
                }
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text(UzStrings.MY_MUSEUMS_TITLE, style = MaterialTheme.typography.headlineMedium) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("my_museums_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = UzStrings.CLOSE_BTN
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = onOpenPricing,
                        modifier = Modifier.testTag("my_museums_pricing_btn")
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(UzStrings.HOME_PRICING_CTA)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateNew,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("my_museums_fab_create")
            ) {
                Icon(Icons.Default.Add, contentDescription = UzStrings.HOME_CREATE_CTA)
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                SegmentedButton(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    modifier = Modifier.testTag("tab_list_button")
                ) {
                    Text(UzStrings.TAB_LIST)
                }
                SegmentedButton(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    modifier = Modifier.testTag("tab_map_button")
                ) {
                    Text(UzStrings.TAB_MAP)
                }
            }

            if (selectedTab == 1) {
                val allPins = remember(museums) { museums.flatMap { it.collectMapPins() } }
                MuseumsExploreMap(
                    pins = allPins,
                    onOpenMuseum = onOpenViewer
                )
            } else if (museums.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Collections,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(UzStrings.EMPTY_MUSEUMS_TITLE, style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = UzStrings.EMPTY_MUSEUMS_DESC,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        Button(onClick = onCreateNew) {
                            Text(UzStrings.HOME_CREATE_CTA)
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(museums, key = { it.id }) { museum ->
                        val isLocked = museum.roomStyle.isPremium && !isPaidActive
                        MuseumListCard(
                            museum = museum,
                            isLocked = isLocked,
                            onOpen = { onOpenViewer(museum.slug) },
                            onShare = { onShareMuseum(museum.slug) },
                            onEdit = {
                                if (isLocked) onOpenPricing() else onEditMuseum(museum.slug)
                            },
                            onDelete = { deleteCandidate = museum }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(72.dp)) }
                }
            }
        }
    }
}

@Composable
private fun MuseumListCard(
    museum: Museum,
    isLocked: Boolean,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val coverModel = remember(museum) {
        museum.photos.firstOrNull()?.let { p ->
            p.localPath?.let { File(it) } ?: p.imageUrl
        }
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
                if (coverModel != null) {
                    AsyncImage(
                        model = coverModel,
                        contentDescription = museum.title,
                        modifier = Modifier
                            .size(86.dp)
                            .clip(MaterialTheme.shapes.medium),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = museum.title, style = MaterialTheme.typography.titleLarge)
                    if (museum.dedication.isNotBlank()) {
                        Text(
                            text = museum.dedication,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.extraSmall
                        ) {
                            Text(
                                text = String.format(UzStrings.PHOTO_COUNT_BADGE, museum.photos.size),
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Surface(
                            color = if (museum.isSyncedToCloud) {
                                MaterialTheme.colorScheme.tertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            shape = MaterialTheme.shapes.extraSmall
                        ) {
                            Text(
                                text = if (museum.isSyncedToCloud) UzStrings.SYNCED_CLOUD_BADGE else UzStrings.LOCAL_ONLY_BADGE,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (museum.isSyncedToCloud) {
                                    MaterialTheme.colorScheme.onTertiaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        if (isLocked) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = MaterialTheme.shapes.extraSmall
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Lock,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = UzStrings.EXPIRED_PLAN_BADGE,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                    if (museum.locationName.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = museum.locationName,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onOpen,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("museum_open_${museum.slug}"),
                    shape = MaterialTheme.shapes.small
                ) {
                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(UzStrings.ACTION_OPEN)
                }
                OutlinedButton(
                    onClick = onShare,
                    modifier = Modifier.testTag("museum_share_${museum.slug}"),
                    shape = MaterialTheme.shapes.small
                ) {
                    Icon(Icons.Default.Share, contentDescription = UzStrings.ACTION_SHARE, modifier = Modifier.size(18.dp))
                }
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.testTag("museum_edit_${museum.slug}")
                ) {
                    Icon(Icons.Default.Edit, contentDescription = UzStrings.ACTION_EDIT)
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("museum_delete_${museum.slug}")
                ) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = UzStrings.ACTION_DELETE,
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
