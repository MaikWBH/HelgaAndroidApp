package com.helga.android.ui.stores

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.material3.RadioButton
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
import com.helga.android.data.model.ProductCategory
import com.helga.android.ui.receipts.labelRes
import com.helga.android.data.local.entity.StoreAisleEntity
import com.helga.android.data.local.entity.StoreEntity
import com.helga.android.data.repository.StorePrefill

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreListScreen(
    onBack: () -> Unit,
    viewModel: StoreListViewModel = hiltViewModel(),
) {
    val stores by viewModel.stores.collectAsStateWithLifecycle()
    val selectedStoreAisles by viewModel.selectedStoreAisles.collectAsStateWithLifecycle()

    var showNewStoreDialog by rememberSaveable { mutableStateOf(false) }
    var aisleSheetStore by remember { mutableStateOf<StoreEntity?>(null) }

    if (showNewStoreDialog) {
        NewStoreDialog(
            existingStores = stores,
            onDismiss = { showNewStoreDialog = false },
            onCreate = { name, prefill ->
                viewModel.createStore(name, prefill)
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
            onSetCategory = viewModel::setAisleCategory,
            onAddMissingDefaults = viewModel::addMissingDefaultAisles,
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
                    contentDescription = stringResource(R.string.stores_set_active),
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
    onSetCategory: (StoreAisleEntity, String) -> Unit,
    onAddMissingDefaults: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var newAisleName by rememberSaveable { mutableStateOf("") }

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
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.stores_add_aisle))
                }
            }
            HorizontalDivider()
            Text(
                text = stringResource(R.string.stores_aisles_drag_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            Text(
                text = stringResource(R.string.stores_aisle_category_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            TextButton(
                onClick = onAddMissingDefaults,
                modifier = Modifier.padding(horizontal = 8.dp),
            ) {
                Text(stringResource(R.string.stores_add_missing_aisles))
            }
            ReorderableAisleList(
                aisles = aisles,
                onReorder = onReorder,
                onDeleteAisle = onDeleteAisle,
                onSetCategory = onSetCategory,
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
    onSetCategory: (StoreAisleEntity, String) -> Unit,
) {
    var localOrder by remember { mutableStateOf(aisles) }
    var draggedId by rememberSaveable { mutableStateOf<String?>(null) }
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
                onSetCategory = { key -> onSetCategory(aisle, key) },
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
    onSetCategory: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    val category = ProductCategory.fromKey(aisle.categoryKey)
    val categoryName = stringResource(category.labelRes())
    Column(
        modifier = Modifier
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer { translationY = dragOffsetY }
            .then(modifier),
    ) {
        ListItem(
            headlineContent = { Text(aisle.aisleName) },
            supportingContent = {
                Text(categoryName, color = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            leadingContent = {
                Icon(
                    imageVector = Icons.Filled.DragHandle,
                    contentDescription = stringResource(R.string.stores_aisle_drag_handle),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(
                                Icons.Filled.Category,
                                contentDescription = stringResource(R.string.stores_aisle_choose_category),
                            )
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            CategoryMenuItem(
                                label = stringResource(R.string.category_none),
                                selected = category == null,
                                onClick = { menuOpen = false; onSetCategory("") },
                            )
                            ProductCategory.aisleCategories.forEach { option ->
                                CategoryMenuItem(
                                    label = stringResource(option.labelRes()),
                                    selected = option == category,
                                    onClick = { menuOpen = false; onSetCategory(option.key) },
                                )
                            }
                        }
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
private fun CategoryMenuItem(label: String, selected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        onClick = onClick,
        leadingIcon = {
            if (selected) Icon(Icons.Filled.Check, contentDescription = null)
        },
    )
}

@Composable
private fun NewStoreDialog(
    existingStores: List<StoreEntity>,
    onDismiss: () -> Unit,
    onCreate: (name: String, prefill: StorePrefill) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var prefill by remember { mutableStateOf<StorePrefill>(StorePrefill.DefaultTemplate) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.stores_new)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.stores_name_label)) },
                    singleLine = true,
                )
                Text(
                    text = stringResource(R.string.stores_prefill_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                PrefillOption(
                    label = stringResource(R.string.stores_prefill_default),
                    selected = prefill == StorePrefill.DefaultTemplate,
                    onClick = { prefill = StorePrefill.DefaultTemplate },
                )
                existingStores.forEach { store ->
                    PrefillOption(
                        label = stringResource(R.string.stores_prefill_copy, store.name),
                        selected = (prefill as? StorePrefill.CopyFrom)?.storeId == store.id,
                        onClick = { prefill = StorePrefill.CopyFrom(store.id) },
                    )
                }
                PrefillOption(
                    label = stringResource(R.string.stores_prefill_empty),
                    selected = prefill == StorePrefill.None,
                    onClick = { prefill = StorePrefill.None },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onCreate(name.trim(), prefill) },
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

@Composable
private fun PrefillOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
