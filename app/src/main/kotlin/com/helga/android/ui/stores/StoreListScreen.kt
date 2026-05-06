package com.helga.android.ui.stores

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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
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
            onMoveUp = viewModel::moveAisleUp,
            onMoveDown = viewModel::moveAisleDown,
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
    onMoveUp: (StoreAisleEntity) -> Unit,
    onMoveDown: (StoreAisleEntity) -> Unit,
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
            LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
                items(aisles, key = { it.id }) { aisle ->
                    AisleRow(
                        aisle = aisle,
                        isFirst = aisles.first().id == aisle.id,
                        isLast = aisles.last().id == aisle.id,
                        onMoveUp = { onMoveUp(aisle) },
                        onMoveDown = { onMoveDown(aisle) },
                        onDelete = { onDeleteAisle(aisle) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AisleRow(
    aisle: StoreAisleEntity,
    isFirst: Boolean,
    isLast: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(aisle.aisleName) },
        trailingContent = {
            Row {
                IconButton(onClick = onMoveUp, enabled = !isFirst) {
                    Icon(Icons.Filled.ArrowUpward, contentDescription = null)
                }
                IconButton(onClick = onMoveDown, enabled = !isLast) {
                    Icon(Icons.Filled.ArrowDownward, contentDescription = null)
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
    )
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
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
