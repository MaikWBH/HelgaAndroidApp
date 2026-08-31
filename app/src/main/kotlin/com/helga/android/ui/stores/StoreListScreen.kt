package com.helga.android.ui.stores

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.helga.android.R
import com.helga.android.data.local.entity.StoreAisleEntity
import com.helga.android.data.local.entity.StoreEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreListScreen(
    onBack: () -> Unit,
    viewModel: StoreListViewModel = hiltViewModel(),
) {
    val stores by viewModel.stores.collectAsStateWithLifecycle()
    val selectedStoreAisles by viewModel.selectedStoreAisles.collectAsStateWithLifecycle()

    var showNewStoreDialog by remember { mutableStateOf(false) }
    var aisleSheetStore by remember { mutableStateOf<StoreEntity?>(null) }

    if (showNewStoreDialog) {
        NewStoreDialog(
            onDismiss = { showNewStoreDialog = false },
            onCreate = { name ->
                viewModel.createStore(name)
                showNewStoreDialog = false
            },
        )
    }

    aisleSheetStore?.let { store ->
        AisleEditorSheet(
            store = store,
            aisles = selectedStoreAisles,
            onDismiss = {
                aisleSheetStore = null
                viewModel.selectStore(null)
            },
            onAddAisle = viewModel::addAisle,
            onDeleteAisle = viewModel::deleteAisle,
            onReorder = viewModel::reorderAisles,
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stores_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.recipe_detail_back),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showNewStoreDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.stores_new))
            }
        },
    ) { padding ->
        if (stores.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.stores_empty),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(bottom = 80.dp),
            ) {
                items(stores, key = { it.id }) { store ->
                    StoreRow(
                        store = store,
                        onSetActive = { viewModel.setActiveStore(store.id) },
                        onEditAisles = {
                            viewModel.selectStore(store.id)
                            aisleSheetStore = store
                        },
                        onDelete = { viewModel.deleteStore(store) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun StoreRow(
    store: StoreEntity,
    onSetActive: () -> Unit,
    onEditAisles: () -> Unit,
    onDelete: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(store.name) },
        leadingContent = {
            IconButton(onClick = onSetActive) {
                Icon(
                    imageVector = if (store.isActive == 1)
                        Icons.Filled.RadioButtonChecked
                    else
                        Icons.Filled.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (store.isActive == 1)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        trailingContent = {
            Row {
                IconButton(onClick = onEditAisles) {
                    Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.stores_edit_aisles))
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.recipe_delete),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AisleEditorSheet(
    store: StoreEntity,
    aisles: List<StoreAisleEntity>,
    onDismiss: () -> Unit,
    onAddAisle: (String) -> Unit,
    onDeleteAisle: (StoreAisleEntity) -> Unit,
    onReorder: (orderedIds: List<String>) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var newAisleName by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.stores_aisles_for, store.name),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = newAisleName,
                    onValueChange = { newAisleName = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.stores_aisle_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (newAisleName.isNotBlank()) {
                            onAddAisle(newAisleName)
                            newAisleName = ""
                        }
                    }),
                )
                IconButton(
                    onClick = {
                        if (newAisleName.isNotBlank()) {
                            onAddAisle(newAisleName)
                            newAisleName = ""
                        }
                    }
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                }
            }
            HorizontalDivider()
            Text(
                text = stringResource(R.string.stores_aisles_drag_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            ReorderableAisleList(
                aisles = aisles,
                onReorder = onReorder,
                onDeleteAisle = onDeleteAisle,
            )
        }
    }
}

/**
 * Handgerollter Drag-and-Drop-Reorder ohne externe Library (maerkte A1) — löst die
 * bisherigen Hoch/Runter-Pfeile ab, die bei vielen Gängen pro Verschiebung einen eigenen
 * Tap brauchten. Zieht per Long-Press, tauscht die Position sobald über die halbe Höhe des
 * Nachbar-Eintrags gezogen wurde (gleiches Prinzip wie andernorts bereits per
 * `detectDragGesturesAfterLongPress` in [com.helga.android.ui.shopping.ShoppingListScreen]).
 * Lokale Kopie der Reihenfolge, damit die Liste während des Ziehens nicht durch neu vom
 * Server/DB einlaufende Flow-Updates zurückspringt; erst bei Drag-Ende wird [onReorder] mit
 * der finalen Reihenfolge aufgerufen.
 */
@Composable
private fun ReorderableAisleList(
    aisles: List<StoreAisleEntity>,
    onReorder: (orderedIds: List<String>) -> Unit,
    onDeleteAisle: (StoreAisleEntity) -> Unit,
) {
    var localOrder by remember { mutableStateOf(aisles) }
    var draggedId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val itemHeights = remember { mutableStateMapOf<String, Int>() }
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(aisles) {
        if (draggedId == null) localOrder = aisles
    }

    LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
        items(localOrder, key = { it.id }) { aisle ->
            val isDragging = aisle.id == draggedId
            AisleRow(
                aisle = aisle,
                isDragging = isDragging,
                dragOffsetY = if (isDragging) dragOffset else 0f,
                modifier = Modifier
                    .onSizeChanged { itemHeights[aisle.id] = it.height }
                    .pointerInput(aisle.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                draggedId = aisle.id
                                dragOffset = 0f
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDrag = { change, offset ->
                                change.consume()
                                dragOffset += offset.y
                                val currentId = draggedId ?: return@detectDragGesturesAfterLongPress
                                val currentIndex = localOrder.indexOfFirst { it.id == currentId }
                                val height = itemHeights[currentId]?.toFloat() ?: return@detectDragGesturesAfterLongPress
                                if (dragOffset > height / 2 && currentIndex < localOrder.lastIndex) {
                                    localOrder = localOrder.toMutableList().apply {
                                        add(currentIndex + 1, removeAt(currentIndex))
                                    }
                                    dragOffset -= height
                                } else if (dragOffset < -height / 2 && currentIndex > 0) {
                                    localOrder = localOrder.toMutableList().apply {
                                        add(currentIndex - 1, removeAt(currentIndex))
                                    }
                                    dragOffset += height
                                }
                            },
                            onDragEnd = {
                                draggedId = null
                                dragOffset = 0f
                                onReorder(localOrder.map { it.id })
                            },
                            onDragCancel = {
                                draggedId = null
                                dragOffset = 0f
                                localOrder = aisles
                            },
                        )
                    },
                onDelete = { onDeleteAisle(aisle) },
            )
        }
    }
}

@Composable
private fun AisleRow(
    aisle: StoreAisleEntity,
    isDragging: Boolean,
    dragOffsetY: Float,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = Modifier
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer { translationY = dragOffsetY }
            .then(modifier),
    ) {
        ListItem(
            headlineContent = { Text(aisle.aisleName) },
            leadingContent = {
                Icon(
                    imageVector = Icons.Filled.DragHandle,
                    contentDescription = stringResource(R.string.stores_aisle_drag_handle),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingContent = {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            },
            colors = if (isDragging) {
                ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            } else {
                ListItemDefaults.colors()
            },
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
    }
}

@Composable
private fun NewStoreDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.stores_new)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.stores_name_label)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onCreate(name.trim()) },
                enabled = name.isNotBlank(),
            ) {
                Text(stringResource(R.string.shopping_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.recipe_delete_confirm_cancel))
            }
        },
    )
}
