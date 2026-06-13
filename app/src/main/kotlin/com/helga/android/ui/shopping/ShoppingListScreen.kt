package com.helga.android.ui.shopping

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.helga.android.R
import com.helga.android.data.local.entity.QuickEmojiEntity
import com.helga.android.data.model.ItemOrigin
import com.helga.android.data.model.ItemOrigins
import com.helga.android.data.local.entity.ShoppingItemEntity
import com.helga.android.data.local.entity.ShoppingListStapleEntity
import com.helga.android.data.local.entity.StoreAisleEntity
import com.helga.android.data.local.entity.StoreEntity
import com.helga.android.data.local.entity.OffProductEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListScreen(
    bottomPadding: Dp = 0.dp,
    onNavigateToWeekplan: () -> Unit = {},
    viewModel: ShoppingListViewModel = hiltViewModel(),
) {
    val lists by viewModel.lists.collectAsStateWithLifecycle()
    val activeListId by viewModel.activeListId.collectAsStateWithLifecycle()
    val itemsByAisle by viewModel.itemsByAisle.collectAsStateWithLifecycle()
    val quickEmojis by viewModel.quickEmojis.collectAsStateWithLifecycle()
    val storeAisles by viewModel.storeAisles.collectAsStateWithLifecycle()
    val aisleSortMap by viewModel.aisleSortMap.collectAsStateWithLifecycle()
    val staples by viewModel.staples.collectAsStateWithLifecycle()
    val checkMode by viewModel.checkMode.collectAsStateWithLifecycle()
    val activeStore by viewModel.activeStore.collectAsStateWithLifecycle()
    val allStores by viewModel.allStores.collectAsStateWithLifecycle()
    val weekplanHasRecipes by viewModel.weekplanHasRecipes.collectAsStateWithLifecycle()
    val currentListEmpty by viewModel.currentListEmpty.collectAsStateWithLifecycle()
    val costEstimate by viewModel.costEstimate.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val scannedProduct by viewModel.scannedProduct.collectAsStateWithLifecycle()

    val activeList = lists.find { it.id == activeListId }
    var showListDropdown by remember { mutableStateOf(false) }
    var showOverflow by remember { mutableStateOf(false) }
    var showNewListDialog by remember { mutableStateOf(false) }
    var aislePickerItem by remember { mutableStateOf<ShoppingItemEntity?>(null) }
    var showStaplesSheet by remember { mutableStateOf(false) }
    var editItem by remember { mutableStateOf<ShoppingItemEntity?>(null) }
    var showStoreDropdown by remember { mutableStateOf(false) }
    var showBarcodeScanner by remember { mutableStateOf(false) }

    if (showNewListDialog) {
        NewListDialog(
            onDismiss = { showNewListDialog = false },
            onCreate = { name ->
                viewModel.createList(name)
                showNewListDialog = false
            },
        )
    }

    aislePickerItem?.let { item ->
        AislePickerDialog(
            aisles = storeAisles,
            onDismiss = { aislePickerItem = null },
            onPick = { aisleName ->
                viewModel.assignAisle(item, aisleName)
                aislePickerItem = null
            },
        )
    }

    if (showStaplesSheet) {
        StaplesSheet(
            staples = staples,
            onDismiss = { showStaplesSheet = false },
            onAddAll = {
                viewModel.addStaplesToList()
                showStaplesSheet = false
            },
            onAddStaple = viewModel::addStaple,
            onDeleteStaple = viewModel::deleteStaple,
            onSuggest = viewModel::suggestItems,
        )
    }

    if (showBarcodeScanner) {
        com.helga.android.ui.components.BarcodeScanner(
            onBarcodeDetected = { barcode ->
                viewModel.addItemFromBarcode(barcode)
                showBarcodeScanner = false
            },
            onDismiss = { showBarcodeScanner = false },
            modifier = Modifier.fillMaxSize(),
        )
    }

    // Product Detail Dialog nach dem Scannen
    scannedProduct?.let { product ->
        ScannedProductDialog(
            product = product,
            activeStore = activeStore,
            onAddToList = { viewModel.confirmScannedProduct() },
            onSaveToCatalog = { viewModel.saveScannedToCatalog() },
            onDismiss = { viewModel.clearScannedProduct() },
        )
    }

    editItem?.let { item ->
        EditItemDialog(
            item = item,
            onDismiss = { editItem = null },
            onSave = { quantity, unit, name ->
                viewModel.updateItem(item.id, quantity, unit, name)
                editItem = null
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { showListDropdown = true },
                        ),
                    ) {
                        Text(activeList?.name ?: stringResource(R.string.shopping_title))
                        Icon(imageVector = Icons.Filled.ExpandMore, contentDescription = null)
                        DropdownMenu(
                            expanded = showListDropdown,
                            onDismissRequest = { showListDropdown = false },
                        ) {
                            lists.forEach { list ->
                                DropdownMenuItem(
                                    text = { Text(list.name) },
                                    onClick = {
                                        viewModel.selectList(list.id)
                                        showListDropdown = false
                                    },
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.shopping_new_list)) },
                                leadingIcon = { Icon(Icons.Filled.Add, null) },
                                onClick = {
                                    showListDropdown = false
                                    showNewListDialog = true
                                },
                            )
                        }
                    }
                },
                actions = {
                    val hasChecked = itemsByAisle.values.any { items -> items.any { it.isChecked == 1 } }
                    if (checkMode == "move" && hasChecked) {
                        IconButton(onClick = { viewModel.deleteCheckedItems() }) {
                            Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.shopping_finish))
                        }
                    }
                    Box {
                        IconButton(onClick = { showOverflow = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = showOverflow,
                            onDismissRequest = { showOverflow = false },
                        ) {
                            if (checkMode == "keep") {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.shopping_delete_checked)) },
                                    leadingIcon = { Icon(Icons.Filled.Delete, null) },
                                    onClick = {
                                        showOverflow = false
                                        viewModel.deleteCheckedItems()
                                    },
                                    enabled = hasChecked,
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.shopping_staples)) },
                                onClick = {
                                    showOverflow = false
                                    showStaplesSheet = true
                                },
                            )
                            if (staples.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.shopping_staples_add_all)) },
                                    leadingIcon = { Icon(Icons.Filled.Add, null) },
                                    onClick = {
                                        showOverflow = false
                                        viewModel.addStaplesToList()
                                    },
                                    enabled = activeListId != null,
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding),
        ) {
            // Schnellstart-Banner
            if (weekplanHasRecipes && currentListEmpty && lists.isNotEmpty()) {
                Card(
                    onClick = {
                        val listId = activeListId ?: return@Card
                        viewModel.exportWeekToShoppingList(listId)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("📋", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.widthIn(min = 8.dp))
                        Text(
                            text = stringResource(R.string.shopping_weekplan_export_banner),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            } else if (!weekplanHasRecipes && currentListEmpty) {
                Card(
                    onClick = onNavigateToWeekplan,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("📅", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.widthIn(min = 8.dp))
                        Text(
                            text = stringResource(R.string.shopping_weekplan_create_banner),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            if (allStores.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Store,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.widthIn(min = 6.dp))
                    Box {
                        Row(
                            modifier = Modifier.clickable { showStoreDropdown = true },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = activeStore?.name
                                    ?: stringResource(R.string.shopping_no_store),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (activeStore != null)
                                    MaterialTheme.colorScheme.onSurface
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Icon(
                                imageVector = Icons.Filled.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        DropdownMenu(
                            expanded = showStoreDropdown,
                            onDismissRequest = { showStoreDropdown = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.shopping_no_store)) },
                                onClick = {
                                    viewModel.selectStore(null)
                                    showStoreDropdown = false
                                },
                                leadingIcon = if (activeStore == null) {
                                    { Icon(Icons.Filled.Check, contentDescription = null) }
                                } else null,
                            )
                            HorizontalDivider()
                            allStores.forEach { store ->
                                DropdownMenuItem(
                                    text = { Text(store.name) },
                                    onClick = {
                                        viewModel.selectStore(store.id)
                                        showStoreDropdown = false
                                    },
                                    leadingIcon = if (store.id == activeStore?.id) {
                                        { Icon(Icons.Filled.Check, contentDescription = null) }
                                    } else null,
                                )
                            }
                        }
                    }
                }
            }
            // Cost estimate card
            if (costEstimate != null && costEstimate!!.totalCost > 0) {
                CostEstimateCard(estimate = costEstimate!!)
            }
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier.weight(1f),
            ) {
                when {
                    lists.isEmpty() -> EmptyListsState(
                        modifier = Modifier.fillMaxSize(),
                        onCreateList = { showNewListDialog = true },
                    )
                    itemsByAisle.isEmpty() -> EmptyItemsState(modifier = Modifier.fillMaxSize())
                    else -> {
                        val allItems = remember(itemsByAisle) { itemsByAisle.values.flatten() }
                        val sortedAisles = remember(itemsByAisle, aisleSortMap) {
                            itemsByAisle.keys.sortedWith(
                                compareBy {
                                    aisleSortMap[it] ?: if (it.isBlank()) Int.MAX_VALUE else Int.MAX_VALUE - 1
                                }
                            )
                        }
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 8.dp),
                        ) {

                            if (checkMode == "move") {
                                // Offene Items nach Gang
                                sortedAisles.forEach { aisle ->
                                    val unchecked = (itemsByAisle[aisle] ?: emptyList()).filter { it.isChecked == 0 }
                                    if (unchecked.isEmpty()) return@forEach
                                    item(key = "header_$aisle") {
                                        AisleHeader(
                                            aisle = aisle.ifBlank { stringResource(R.string.shopping_no_aisle) },
                                        )
                                    }
                                    items(unchecked, key = { it.id }) { item ->
                                        SwipeableShoppingItem(
                                            item = item,
                                            showAisleButton = item.aisle.isBlank() && storeAisles.isNotEmpty(),
                                            onToggle = { viewModel.toggleChecked(item) },
                                            onDelete = { viewModel.deleteItem(item) },
                                            onAssignAisle = { aislePickerItem = item },
                                            onEdit = { editItem = item },
                                        )
                                    }
                                }
                                // Abgehakt-Sektion
                                val checkedItems = allItems.filter { it.isChecked == 1 }
                                if (checkedItems.isNotEmpty()) {
                                    item(key = "checked_header") {
                                        Column {
                                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                            Text(
                                                text = stringResource(R.string.shopping_checked_section),
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                            )
                                        }
                                    }
                                    items(checkedItems, key = { "checked_${it.id}" }) { item ->
                                        SwipeableShoppingItem(
                                            item = item,
                                            showAisleButton = false,
                                            onToggle = { viewModel.toggleChecked(item) },
                                            onDelete = { viewModel.deleteItem(item) },
                                            onAssignAisle = { aislePickerItem = item },
                                            onEdit = { editItem = item },
                                        )
                                    }
                                }
                            } else {
                                // Standard KEEP-Modus
                                sortedAisles.forEach { aisle ->
                                    val items = itemsByAisle[aisle] ?: return@forEach
                                    item(key = "header_$aisle") {
                                        AisleHeader(
                                            aisle = aisle.ifBlank { stringResource(R.string.shopping_no_aisle) },
                                        )
                                    }
                                    items(items, key = { it.id }) { item ->
                                        SwipeableShoppingItem(
                                            item = item,
                                            showAisleButton = item.aisle.isBlank() && storeAisles.isNotEmpty(),
                                            onToggle = { viewModel.toggleChecked(item) },
                                            onDelete = { viewModel.deleteItem(item) },
                                            onAssignAisle = { aislePickerItem = item },
                                            onEdit = { editItem = item },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            QuickAddBar(
                activeListId = activeListId,
                quickEmojis = quickEmojis,
                onAdd = viewModel::addItem,
                onSuggest = viewModel::suggestItems,
                onEmojiClick = viewModel::addEmojiItem,
                onScanClick = { showBarcodeScanner = true },
                bottomPadding = bottomPadding,
            )
        }
    }
}

@Composable
private fun SwipeableShoppingItem(
    item: ShoppingItemEntity,
    showAisleButton: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onAssignAisle: () -> Unit,
    onEdit: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val offsetX = remember { Animatable(0f) }
    var rowWidthPx by remember { mutableFloatStateOf(0f) }
    var dismissed by remember { mutableStateOf(false) }

    // Aktion löst erst ab 35 % der Zeilenbreite aus – versehentliches Antippen wird so vermieden
    val threshold = rowWidthPx * 0.35f

    LaunchedEffect(dismissed) {
        if (dismissed) {
            delay(250)
            onDelete()
        }
    }

    AnimatedVisibility(
        visible = !dismissed,
        exit = shrinkVertically(tween(300)) + fadeOut(tween(300)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { rowWidthPx = it.width.toFloat() },
        ) {
            // Hintergrund nur sichtbar, sobald gewischt wird
            if (offsetX.value != 0f) {
                val isCheckSwipe = offsetX.value > 0
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            if (isCheckSwipe) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.error
                        )
                        .padding(horizontal = 20.dp),
                    contentAlignment = if (isCheckSwipe) Alignment.CenterStart else Alignment.CenterEnd,
                ) {
                    Icon(
                        imageVector = if (isCheckSwipe) Icons.Filled.Check else Icons.Filled.Delete,
                        contentDescription = null,
                        tint = if (isCheckSwipe) MaterialTheme.colorScheme.onPrimaryContainer
                               else MaterialTheme.colorScheme.onError,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                    .pointerInput(item.id) {
                        // Wischen erst NACH langem Drücken – normales Scrollen löst nichts aus
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                scope.launch { offsetX.snapTo(offsetX.value + dragAmount.x) }
                            },
                            onDragEnd = {
                                when {
                                    threshold > 0f && offsetX.value <= -threshold -> {
                                        // nach links → löschen
                                        scope.launch {
                                            offsetX.animateTo(-rowWidthPx, tween(200))
                                            dismissed = true
                                        }
                                    }
                                    threshold > 0f && offsetX.value >= threshold -> {
                                        // nach rechts → abhaken
                                        onToggle()
                                        scope.launch { offsetX.animateTo(0f, tween(200)) }
                                    }
                                    else -> scope.launch { offsetX.animateTo(0f, tween(200)) }
                                }
                            },
                            onDragCancel = {
                                scope.launch { offsetX.animateTo(0f, tween(200)) }
                            },
                        )
                    },
            ) {
                ShoppingItemRow(
                    item = item,
                    showAisleButton = showAisleButton,
                    onToggle = onToggle,
                    onAssignAisle = onAssignAisle,
                    onEdit = onEdit,
                )
            }
        }
    }
}

@Composable
private fun ShoppingItemRow(
    item: ShoppingItemEntity,
    showAisleButton: Boolean,
    onToggle: () -> Unit,
    onAssignAisle: () -> Unit,
    onEdit: () -> Unit,
) {
    val origins = remember(item.id, item.origins) { ItemOrigins.decode(item.origins) }
    val breakdown = remember(origins) { ItemOrigins.aggregateByRecipe(origins) }
    val recipes = remember(breakdown) { breakdown.map { it.recipe }.filter { it.isNotBlank() }.distinct() }
    val canExpand = breakdown.size >= 2
    var expanded by remember(item.id) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Checkbox(
                checked = item.isChecked == 1,
                onCheckedChange = { onToggle() },
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onEdit)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.bodyLarge,
                        textDecoration = if (item.isChecked == 1) TextDecoration.LineThrough else null,
                        color = if (item.isChecked == 1)
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        else
                            MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    SourceBadge(item = item, recipes = recipes)
                }
                if (item.quantity != 1.0 || item.unit.isNotBlank()) {
                    Text(
                        text = if (item.unit.isBlank()) formatQty(item.quantity)
                               else "${formatQty(item.quantity)} ${item.unit}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (canExpand) {
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = "Rezept-Aufschlüsselung",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (showAisleButton) {
                IconButton(onClick = onAssignAisle) {
                    Icon(
                        imageVector = Icons.Filled.LocalOffer,
                        contentDescription = stringResource(R.string.shopping_assign_aisle),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        AnimatedVisibility(visible = expanded && canExpand) {
            OriginBreakdown(
                breakdown = breakdown,
                modifier = Modifier.padding(start = 56.dp, end = 16.dp, bottom = 8.dp),
            )
        }
    }
}

/** Herkunfts-Badge: Rezeptname, "N Rezepte" oder Quelle (Manuell/Vorrat/…). */
@Composable
private fun SourceBadge(item: ShoppingItemEntity, recipes: List<String>) {
    val label = when {
        recipes.size >= 2 -> "${recipes.size} Rezepte"
        recipes.size == 1 -> recipes.first()
        item.source == "staple" -> "Vorrat"
        item.source == "weekplan" -> "Wochenplan"
        item.source == "recipe" -> "Rezept"
        else -> "Manuell"
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
            .widthIn(max = 140.dp)
            .background(
                MaterialTheme.colorScheme.secondaryContainer,
                RoundedCornerShape(4.dp),
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** Aufgeklappte Aufschlüsselung: pro Rezept (bzw. "Manuell") die benötigte Menge. */
@Composable
private fun OriginBreakdown(breakdown: List<ItemOrigin>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        breakdown.forEach { origin ->
            val label = origin.recipe.ifBlank { "Manuell" }
            val qtyText = if (origin.unit.isBlank()) formatQty(origin.quantity)
                          else "${formatQty(origin.quantity)} ${origin.unit}"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "• $label",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    text = qtyText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun formatQty(q: Double): String =
    if (q % 1.0 == 0.0) q.toInt().toString() else q.toString()

@Composable
private fun EditItemDialog(
    item: ShoppingItemEntity,
    onDismiss: () -> Unit,
    onSave: (quantity: Double, unit: String, name: String) -> Unit,
) {
    var quantityText by remember(item.id) { mutableStateOf(item.quantity.toString()) }
    var unit by remember(item.id) { mutableStateOf(item.unit) }
    var name by remember(item.id) { mutableStateOf(item.name) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.shopping_edit_item_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.recipe_form_food)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { quantityText = it },
                    label = { Text(stringResource(R.string.recipe_form_quantity)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = unit,
                    onValueChange = { unit = it },
                    label = { Text(stringResource(R.string.recipe_form_unit)) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val quantity = quantityText.replace(',', '.').toDoubleOrNull() ?: item.quantity
                    onSave(quantity, unit.trim(), name.trim())
                },
                enabled = name.isNotBlank(),
            ) {
                Text(stringResource(R.string.recipe_form_save))
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
private fun AisleHeader(aisle: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = aisle,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
    }
}

@Composable
private fun AislePickerDialog(
    aisles: List<StoreAisleEntity>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.shopping_assign_aisle)) },
        text = {
            LazyColumn {
                items(aisles) { aisle ->
                    TextButton(
                        onClick = { onPick(aisle.aisleName) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(aisle.aisleName)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.recipe_delete_confirm_cancel))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StaplesSheet(
    staples: List<ShoppingListStapleEntity>,
    onDismiss: () -> Unit,
    onAddAll: () -> Unit,
    onAddStaple: (String) -> Unit,
    onDeleteStaple: (ShoppingListStapleEntity) -> Unit,
    onSuggest: suspend (String) -> List<String>,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var newName by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(newName) {
        if (newName.length >= 2) {
            delay(300)
            suggestions = onSuggest(newName)
        } else {
            suggestions = emptyList()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.shopping_staples),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (staples.isNotEmpty()) {
                    TextButton(onClick = onAddAll) {
                        Text(stringResource(R.string.shopping_staples_add_all))
                    }
                }
            }
            HorizontalDivider()
            if (suggestions.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(suggestions) { suggestion ->
                        SuggestionChip(
                            onClick = {
                                onAddStaple(suggestion)
                                newName = ""
                                suggestions = emptyList()
                            },
                            label = { Text(suggestion) },
                        )
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.shopping_staple_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (newName.isNotBlank()) {
                            onAddStaple(newName)
                            newName = ""
                        }
                    }),
                )
                IconButton(onClick = {
                    if (newName.isNotBlank()) {
                        onAddStaple(newName)
                        newName = ""
                    }
                }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                }
            }
            if (staples.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.shopping_staples_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
                    items(staples, key = { it.id }) { staple ->
                        ListItem(
                            headlineContent = { Text(staple.name) },
                            trailingContent = {
                                IconButton(onClick = { onDeleteStaple(staple) }) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            },
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun EmojiQuickButton(emoji: QuickEmojiEntity, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.widthIn(min = 48.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            Text(text = emoji.emoji, fontSize = 24.sp)
        }
    }
}

@Composable
private fun QuickAddBar(
    activeListId: String?,
    quickEmojis: List<QuickEmojiEntity>,
    onAdd: (String) -> Unit,
    onSuggest: suspend (String) -> List<String>,
    onEmojiClick: (QuickEmojiEntity) -> Unit,
    onScanClick: () -> Unit = {},
    bottomPadding: Dp = 0.dp,
) {
    var text by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(text) {
        if (text.length >= 2) {
            delay(300)
            suggestions = onSuggest(text)
        } else {
            suggestions = emptyList()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        if (quickEmojis.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(quickEmojis, key = { it.id }) { emoji ->
                    EmojiQuickButton(emoji = emoji, onClick = { onEmojiClick(emoji) })
                }
            }
        }
        if (suggestions.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(suggestions) { suggestion ->
                    SuggestionChip(
                        onClick = {
                            text = ""
                            suggestions = emptyList()
                            onAdd(suggestion)
                        },
                        label = { Text(suggestion) },
                    )
                }
            }
        }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            placeholder = { Text(stringResource(R.string.shopping_add_hint)) },
            singleLine = true,
            enabled = activeListId != null,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (text.isNotBlank()) {
                        val name = text
                        text = ""
                        suggestions = emptyList()
                        onAdd(name)
                    }
                }
            ),
            leadingIcon = {
                IconButton(onClick = onScanClick, enabled = activeListId != null) {
                    Icon(
                        imageVector = Icons.Filled.QrCodeScanner,
                        contentDescription = stringResource(R.string.shopping_scanner_button),
                        tint = if (activeListId != null) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            trailingIcon = {
                if (text.isNotBlank()) {
                    IconButton(onClick = {
                        val name = text
                        text = ""
                        suggestions = emptyList()
                        onAdd(name)
                    }) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                    }
                }
            },
        )
        if (bottomPadding > 0.dp) {
            Spacer(Modifier.height(bottomPadding))
        }
    }
}

@Composable
private fun EmptyListsState(modifier: Modifier = Modifier, onCreateList: () -> Unit) {
    // LazyColumn statt Box, damit Pull-to-Refresh die Scroll-Geste auch ohne Inhalt erkennt.
    LazyColumn(modifier = modifier) {
        item {
            Box(
                modifier = Modifier.fillParentMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.shopping_no_lists),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    TextButton(onClick = onCreateList) {
                        Text(stringResource(R.string.shopping_new_list))
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyItemsState(modifier: Modifier = Modifier) {
    // LazyColumn statt Box, damit Pull-to-Refresh die Scroll-Geste auch ohne Inhalt erkennt.
    LazyColumn(modifier = modifier) {
        item {
            Box(
                modifier = Modifier.fillParentMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.shopping_empty),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun NewListDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.shopping_new_list)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.shopping_list_name)) },
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

@Composable
private fun CostEstimateCard(estimate: com.helga.android.data.model.ListCostEstimate) {
    var showDetails by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable { showDetails = !showDetails },
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("💶", style = MaterialTheme.typography.titleMedium)
                    Column {
                        Text(
                            text = String.format("%.2f €", estimate.totalCost),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "${(estimate.estimatedAccuracy * 100).toInt()}% mit Preisen",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (estimate.storeComparison.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Filled.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = if (showDetails) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (showDetails && estimate.storeComparison.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Column(modifier = Modifier.fillMaxWidth()) {
                    estimate.storeComparison.forEachIndexed { index, store ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = store.storeName,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                text = String.format("%.2f €", store.totalCost),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (index == 0) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ScannedProductDialog(
    product: OffProductEntity,
    activeStore: StoreEntity?,
    onAddToList: () -> Unit,
    onSaveToCatalog: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "📦 ${product.name}") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // NutriScore
                if (product.nutriScore.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "NutriScore:",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Surface(
                            modifier = Modifier.size(32.dp),
                            shape = RoundedCornerShape(4.dp),
                            color = when (product.nutriScore.uppercase()) {
                                "A" -> Color(0xFF4CAF50)
                                "B" -> Color(0xFF8BC34A)
                                "C" -> Color(0xFFFFC107)
                                "D" -> Color(0xFFFF9800)
                                "E" -> Color(0xFFF44336)
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            },
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = product.nutriScore.uppercase(),
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                )
                            }
                        }
                    }
                }

                // Kcal
                if (product.kcalPerUnit > 0) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Kcal/100g:",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            text = "${product.kcalPerUnit.toInt()} kcal",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Nährwert-Übersicht (pro 100 g)
                if (product.proteins > 0 || product.fats > 0 || product.carbs > 0) {
                    NutrientRow(label = "🥚 Eiweiß", value = "${product.proteins.toInt()} g")
                    NutrientRow(label = "🧈 Fett", value = "${product.fats.toInt()} g")
                    NutrientRow(label = "🍞 Kohlenhydrate", value = "${product.carbs.toInt()} g")
                }

                // Brand
                if (product.brand.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Marke:",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            text = product.brand,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Badges
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (product.vegan == 1) {
                        SuggestionChip(
                            onClick = {},
                            label = { Text("🌱 Vegan") },
                        )
                    }
                    if (product.vegetarian == 1) {
                        SuggestionChip(
                            onClick = {},
                            label = { Text("🥬 Vegetarisch") },
                        )
                    }
                    if (product.isOrganic == 1) {
                        SuggestionChip(
                            onClick = {},
                            label = { Text("🌿 Bio") },
                        )
                    }
                }

                // Allergen Warning
                if (product.allergenes.isNotBlank() && product.allergenes != "[]") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(12.dp),
                        ) {
                            Text(
                                text = "⚠️ Allergene enthalten",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            )
                            Text(
                                text = product.allergenes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSaveToCatalog) {
                Text(if (product.isFavorite == 1) "⭐ Im Katalog" else "⭐ Zu meinen Produkten")
            }
        },
        dismissButton = {
            TextButton(onClick = onAddToList) {
                Text("✅ Zur Liste")
            }
        },
        modifier = Modifier.widthIn(max = 400.dp),
    )
}

@Composable
private fun NutrientRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
