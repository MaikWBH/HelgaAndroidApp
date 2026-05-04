package com.helga.android.ui.shopping

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.helga.android.R
import com.helga.android.data.local.entity.QuickEmojiEntity
import com.helga.android.data.local.entity.ShoppingItemEntity
import com.helga.android.data.local.entity.ShoppingListStapleEntity
import com.helga.android.data.local.entity.StoreAisleEntity
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListScreen(
    onBack: () -> Unit,
    viewModel: ShoppingListViewModel = hiltViewModel(),
) {
    val lists by viewModel.lists.collectAsStateWithLifecycle()
    val activeListId by viewModel.activeListId.collectAsStateWithLifecycle()
    val itemsByAisle by viewModel.itemsByAisle.collectAsStateWithLifecycle()
    val quickEmojis by viewModel.quickEmojis.collectAsStateWithLifecycle()
    val storeAisles by viewModel.storeAisles.collectAsStateWithLifecycle()
    val aisleSortMap by viewModel.aisleSortMap.collectAsStateWithLifecycle()
    val staples by viewModel.staples.collectAsStateWithLifecycle()

    val activeList = lists.find { it.id == activeListId }
    var showListDropdown by remember { mutableStateOf(false) }
    var showOverflow by remember { mutableStateOf(false) }
    var showNewListDialog by remember { mutableStateOf(false) }
    var aislePickerItem by remember { mutableStateOf<ShoppingItemEntity?>(null) }
    var showStaplesSheet by remember { mutableStateOf(false) }

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
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.recipe_detail_back),
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showOverflow = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = showOverflow,
                            onDismissRequest = { showOverflow = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.shopping_delete_checked)) },
                                leadingIcon = { Icon(Icons.Filled.Delete, null) },
                                onClick = {
                                    showOverflow = false
                                    viewModel.deleteCheckedItems()
                                },
                                enabled = itemsByAisle.values.any { items -> items.any { it.isChecked == 1 } },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.shopping_staples)) },
                                onClick = {
                                    showOverflow = false
                                    showStaplesSheet = true
                                },
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            QuickAddBar(
                activeListId = activeListId,
                quickEmojis = quickEmojis,
                onAdd = viewModel::addItem,
                onSuggest = viewModel::suggestItems,
                onEmojiClick = viewModel::addEmojiItem,
            )
        },
    ) { padding ->
        when {
            lists.isEmpty() -> EmptyListsState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                onCreateList = { showNewListDialog = true },
            )
            itemsByAisle.isEmpty() -> EmptyItemsState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(bottom = 8.dp),
                ) {
                    val sortedAisles = itemsByAisle.keys.sortedWith(
                        compareBy {
                            aisleSortMap[it] ?: if (it.isBlank()) Int.MAX_VALUE else Int.MAX_VALUE - 1
                        }
                    )
                    sortedAisles.forEach { aisle ->
                        val items = itemsByAisle[aisle] ?: return@forEach
                        if (aisle.isNotBlank()) {
                            item(key = "header_$aisle") {
                                AisleHeader(aisle = aisle)
                            }
                        }
                        items(items, key = { it.id }) { item ->
                            SwipeableShoppingItem(
                                item = item,
                                showAisleButton = item.aisle.isBlank() && storeAisles.isNotEmpty(),
                                onToggle = { viewModel.toggleChecked(item) },
                                onDelete = { viewModel.deleteItem(item) },
                                onAssignAisle = { aislePickerItem = item },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableShoppingItem(
    item: ShoppingItemEntity,
    showAisleButton: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onAssignAisle: () -> Unit,
) {
    var dismissed by remember { mutableStateOf(false) }
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                dismissed = true
                true
            } else false
        },
        positionalThreshold = { totalDistance -> totalDistance * 0.4f },
    )

    LaunchedEffect(dismissed) {
        if (dismissed) {
            delay(300)
            onDelete()
        }
    }

    AnimatedVisibility(
        visible = !dismissed,
        exit = shrinkVertically(tween(300)) + fadeOut(tween(300)),
    ) {
        SwipeToDismissBox(
            state = dismissState,
            backgroundContent = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.error)
                        .padding(end = 20.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onError,
                    )
                }
            },
            enableDismissFromStartToEnd = false,
        ) {
            ShoppingItemRow(
                item = item,
                showAisleButton = showAisleButton,
                onToggle = onToggle,
                onAssignAisle = onAssignAisle,
            )
        }
    }
}

@Composable
private fun ShoppingItemRow(
    item: ShoppingItemEntity,
    showAisleButton: Boolean,
    onToggle: () -> Unit,
    onAssignAisle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Checkbox(
            checked = item.isChecked == 1,
            onCheckedChange = { onToggle() },
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (item.isChecked == 1) TextDecoration.LineThrough else null,
                color = if (item.isChecked == 1)
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                else
                    MaterialTheme.colorScheme.onSurface,
            )
            if (item.quantity != 1.0 || item.unit.isNotBlank()) {
                val qDisplay = if (item.quantity % 1.0 == 0.0)
                    item.quantity.toInt().toString()
                else
                    item.quantity.toString()
                Text(
                    text = if (item.unit.isBlank()) qDisplay else "$qDisplay ${item.unit}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
) {
    val sheetState = rememberModalBottomSheetState(skipPartialExpansion = false)
    var newName by remember { mutableStateOf("") }

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
private fun QuickAddBar(
    activeListId: String?,
    quickEmojis: List<QuickEmojiEntity>,
    onAdd: (String) -> Unit,
    onSuggest: suspend (String) -> List<String>,
    onEmojiClick: (QuickEmojiEntity) -> Unit,
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
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(quickEmojis) { emoji ->
                    SuggestionChip(
                        onClick = { onEmojiClick(emoji) },
                        label = { Text("${emoji.emoji} ${emoji.food}") },
                    )
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
    }
}

@Composable
private fun EmptyListsState(modifier: Modifier = Modifier, onCreateList: () -> Unit) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
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

@Composable
private fun EmptyItemsState(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.shopping_empty),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
