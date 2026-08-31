package com.helga.android.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.helga.android.ui.theme.accentPrimaryColors
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.helga.android.R
import com.helga.android.data.local.entity.QuickEmojiEntity
import com.helga.android.data.local.entity.ShoppingListEntity
import java.text.DateFormat
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLoggedOut: () -> Unit,
    onStoresClick: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lastSyncTs by viewModel.lastSyncTs.collectAsStateWithLifecycle()
    val syncError by viewModel.syncError.collectAsStateWithLifecycle()
    val shoppingLists by viewModel.shoppingLists.collectAsStateWithLifecycle()
    val quickEmojis by viewModel.quickEmojis.collectAsStateWithLifecycle()
    val exportJson by viewModel.exportJson.collectAsStateWithLifecycle()
    val bulkAiState by viewModel.bulkAiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showLogoutDialog by remember { mutableStateOf(false) }
    var deleteList by remember { mutableStateOf<ShoppingListEntity?>(null) }
    var editEmoji by remember { mutableStateOf<QuickEmojiEntity?>(null) }
    var showAddEmoji by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(exportJson) {
        exportJson?.let { json ->
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_TEXT, json)
                putExtra(Intent.EXTRA_SUBJECT, "Helga Export")
            }
            context.startActivity(Intent.createChooser(intent, "Export"))
            viewModel.clearExport()
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text(stringResource(R.string.settings_logout_confirm_title)) },
            text = { Text(stringResource(R.string.settings_logout_confirm_text)) },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    viewModel.logout(onLoggedOut)
                }) {
                    Text(stringResource(R.string.settings_logout_confirm_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(stringResource(R.string.recipe_delete_confirm_cancel))
                }
            },
        )
    }

    deleteList?.let { list ->
        AlertDialog(
            onDismissRequest = { deleteList = null },
            title = { Text(stringResource(R.string.settings_delete_list_title)) },
            text = { Text(stringResource(R.string.settings_delete_list_text, list.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteShoppingList(list)
                    deleteList = null
                }) { Text(stringResource(R.string.recipe_delete_confirm_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteList = null }) { Text(stringResource(R.string.recipe_delete_confirm_cancel)) }
            },
        )
    }

    if (showAddEmoji) {
        QuickEmojiDialog(
            onDismiss = { showAddEmoji = false },
            onSave = { emoji, food, quantity, unit ->
                viewModel.addQuickEmoji(emoji, food, quantity, unit)
                showAddEmoji = false
            },
        )
    }
    editEmoji?.let { item ->
        QuickEmojiDialog(
            item = item,
            onDismiss = { editEmoji = null },
            onSave = { emoji, food, quantity, unit ->
                viewModel.updateQuickEmoji(item, emoji, food, quantity, unit)
                editEmoji = null
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
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
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_appearance_section),
                style = MaterialTheme.typography.titleMedium,
            )

            Text(
                text = stringResource(R.string.settings_theme_mode),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val themeModes = listOf("system", "light", "dark")
            val themeModeLabels = listOf(
                stringResource(R.string.settings_theme_system),
                stringResource(R.string.settings_theme_light),
                stringResource(R.string.settings_theme_dark),
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                themeModes.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = state.themeMode == mode,
                        onClick = { viewModel.setThemeMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index, themeModes.size),
                        label = { Text(themeModeLabels[index]) },
                    )
                }
            }

            Text(
                text = stringResource(R.string.settings_accent_color),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(accentPrimaryColors) { index, color ->
                    val isSelected = state.accentColor == index
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .border(
                                width = if (isSelected) 3.dp else 0.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.onBackground else Color.Transparent,
                                shape = CircleShape,
                            )
                            .padding(if (isSelected) 3.dp else 0.dp)
                            .clip(CircleShape),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(color)
                                .clickable { viewModel.setAccentColor(index) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.settings_notify_section),
                style = MaterialTheme.typography.titleMedium,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.settings_notify_shopping),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Switch(
                    checked = state.notifyShoppingDay,
                    onCheckedChange = {
                        viewModel.setNotifyShoppingDay(it)
                        if (it) requestNotificationPermissionIfNeeded()
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.settings_notify_cook),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Switch(
                    checked = state.notifyCookReminder,
                    onCheckedChange = {
                        viewModel.setNotifyCookReminder(it)
                        if (it) requestNotificationPermissionIfNeeded()
                    },
                )
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.settings_server_section),
                style = MaterialTheme.typography.titleMedium,
            )

            OutlinedTextField(
                value = state.serverUrl,
                onValueChange = viewModel::setServerUrl,
                label = { Text(stringResource(R.string.onboarding_server_url)) },
                placeholder = { Text(stringResource(R.string.onboarding_server_url_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.apiKey,
                onValueChange = viewModel::setApiKey,
                label = { Text(stringResource(R.string.onboarding_api_key)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )

            ValidationFeedback(state.validation)

            Button(
                onClick = { viewModel.testAndSave() },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.validation != SettingsValidation.Testing &&
                    state.serverUrl.isNotBlank() && state.apiKey.isNotBlank(),
            ) {
                if (state.validation == SettingsValidation.Testing) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(stringResource(R.string.settings_save_and_test))
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.settings_sync_section),
                style = MaterialTheme.typography.titleMedium,
            )

            Text(
                text = stringResource(R.string.settings_last_sync, formatTimestamp(lastSyncTs)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (syncError != null) {
                Text(
                    text = stringResource(R.string.sync_error_detail, syncError!!),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            OutlinedButton(
                onClick = { viewModel.syncNow() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.sync_now))
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.settings_ai_bulk_title),
                style = MaterialTheme.typography.titleMedium,
            )

            OutlinedButton(
                onClick = { viewModel.runBulkAi(BulkAiMode.NUTRITION) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !bulkAiState.isRunning,
            ) {
                Text(stringResource(R.string.settings_ai_bulk_nutrition))
            }
            OutlinedButton(
                onClick = { viewModel.runBulkAi(BulkAiMode.CLASSIFY) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !bulkAiState.isRunning,
            ) {
                Text(stringResource(R.string.settings_ai_bulk_classify))
            }
            Button(
                onClick = { viewModel.runBulkAi(BulkAiMode.BOTH) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !bulkAiState.isRunning,
            ) {
                if (bulkAiState.isRunning && bulkAiState.mode == BulkAiMode.BOTH) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(stringResource(R.string.settings_ai_bulk_both))
                }
            }
            if (bulkAiState.isRunning) {
                Text(
                    text = stringResource(R.string.settings_ai_bulk_progress, bulkAiState.processed, bulkAiState.total),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (bulkAiState.total > 0) {
                Text(
                    text = stringResource(R.string.settings_ai_bulk_done, bulkAiState.updated, bulkAiState.failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { viewModel.dismissBulkAiResult() },
                )
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.settings_shopping_section),
                style = MaterialTheme.typography.titleMedium,
            )

            OutlinedButton(
                onClick = onStoresClick,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_manage_stores))
            }

            OutlinedButton(
                onClick = { viewModel.exportAllData() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_export_data))
            }

            SettingsPlanDaysDropdown(
                value = state.weekplanDays,
                onSelected = viewModel::setWeekplanDays,
            )
            SettingsShoppingDayDropdown(
                value = state.shoppingDay,
                onSelected = viewModel::setShoppingDay,
            )
            SettingsDefaultListDropdown(
                lists = shoppingLists,
                selectedId = state.defaultShoppingListId,
                onSelected = viewModel::setDefaultShoppingListId,
            )

            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.settings_check_mode),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val checkModes = listOf("keep", "move")
            val checkModeLabels = listOf(
                stringResource(R.string.settings_check_mode_keep),
                stringResource(R.string.settings_check_mode_move),
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                checkModes.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = state.checkMode == mode,
                        onClick = { viewModel.setCheckMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index, checkModes.size),
                        label = { Text(checkModeLabels[index]) },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(
                    R.string.settings_scan_reminder_threshold,
                    (state.scanReminderThreshold * 100).toInt(),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            androidx.compose.material3.Slider(
                value = state.scanReminderThreshold,
                onValueChange = viewModel::setScanReminderThreshold,
                valueRange = 0.1f..1f,
                steps = 8,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_receipt_reconcile),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(R.string.settings_receipt_reconcile_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.receiptReconciliationEnabled,
                    onCheckedChange = { viewModel.setReceiptReconciliationEnabled(it) },
                )
            }

            OutlinedButton(
                onClick = viewModel::saveFeatureSettings,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_save_feature_settings))
            }

            Text(
                text = stringResource(R.string.settings_shopping_lists_manage),
                style = MaterialTheme.typography.titleSmall,
            )
            shoppingLists.forEach { list ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(list.name, modifier = Modifier.weight(1f))
                    IconButton(onClick = { deleteList = list }) {
                        Icon(Icons.Filled.Delete, contentDescription = null)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.settings_quick_buttons),
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedButton(onClick = { showAddEmoji = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(stringResource(R.string.settings_quick_button_add))
            }
            quickEmojis.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("${item.emoji} ${item.food}")
                    Row {
                        IconButton(onClick = { editEmoji = item }) {
                            Icon(Icons.Filled.Edit, contentDescription = null)
                        }
                        IconButton(onClick = { viewModel.deleteQuickEmoji(item) }) {
                            Icon(Icons.Filled.Delete, contentDescription = null)
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.settings_account_section),
                style = MaterialTheme.typography.titleMedium,
            )

            OutlinedButton(
                onClick = { showLogoutDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_logout))
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ValidationFeedback(validation: SettingsValidation) {
    val (text, color) = when (validation) {
        SettingsValidation.Idle -> return
        SettingsValidation.Testing -> stringResource(R.string.onboarding_testing) to
            MaterialTheme.colorScheme.onSurfaceVariant
        SettingsValidation.Success -> stringResource(R.string.onboarding_success) to
            MaterialTheme.colorScheme.primary
        SettingsValidation.InvalidUrl -> stringResource(R.string.onboarding_error_invalid_url) to
            MaterialTheme.colorScheme.error
        SettingsValidation.Unreachable -> stringResource(R.string.onboarding_error_unreachable) to
            MaterialTheme.colorScheme.error
        SettingsValidation.Unauthorized -> stringResource(R.string.onboarding_error_unauthorized) to
            MaterialTheme.colorScheme.error
    }
    Text(text = text, color = color, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun formatTimestamp(ts: Long): String {
    if (ts <= 0L) return stringResource(R.string.settings_last_sync_never)
    val df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    return df.format(Date(ts))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsPlanDaysDropdown(value: Int, onSelected: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = stringResource(R.string.settings_plan_days_value, value),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.settings_plan_days)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf(7, 10, 14).forEach { days ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.settings_plan_days_value, days)) },
                    onClick = {
                        onSelected(days)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsShoppingDayDropdown(value: Int, onSelected: (Int) -> Unit) {
    val dayNames = remember {
        // DayOfWeek: MONDAY=1..SUNDAY=7, mapped to index 0..6
        (0..6).map { idx ->
            DayOfWeek.of(idx + 1)
                .getDisplayName(TextStyle.FULL, Locale.getDefault())
                .replaceFirstChar { it.uppercase() }
        }
    }
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = dayNames[value.coerceIn(0, 6)],
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.settings_shopping_day)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            dayNames.forEachIndexed { idx, day ->
                DropdownMenuItem(
                    text = { Text(day) },
                    onClick = {
                        onSelected(idx)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsDefaultListDropdown(
    lists: List<ShoppingListEntity>,
    selectedId: String,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = lists.firstOrNull { it.id == selectedId }?.name.orEmpty()
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = if (selected.isBlank()) stringResource(R.string.settings_default_list_none) else selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.settings_default_list)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            lists.forEach { list ->
                DropdownMenuItem(
                    text = { Text(list.name) },
                    onClick = {
                        onSelected(list.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun QuickEmojiDialog(
    item: QuickEmojiEntity? = null,
    onDismiss: () -> Unit,
    onSave: (emoji: String, food: String, quantity: Double, unit: String) -> Unit,
) {
    var emoji by remember(item?.id) { mutableStateOf(item?.emoji ?: "") }
    var food by remember(item?.id) { mutableStateOf(item?.food ?: "") }
    var quantityText by remember(item?.id) { mutableStateOf((item?.quantity ?: 1.0).toString()) }
    var unit by remember(item?.id) { mutableStateOf(item?.unit ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (item == null) stringResource(R.string.settings_quick_button_add)
                else stringResource(R.string.settings_quick_button_edit)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = emoji, onValueChange = { emoji = it }, label = { Text("Emoji") }, singleLine = true)
                OutlinedTextField(value = food, onValueChange = { food = it }, label = { Text(stringResource(R.string.recipe_form_food)) }, singleLine = true)
                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { quantityText = it },
                    label = { Text(stringResource(R.string.recipe_form_quantity)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                OutlinedTextField(value = unit, onValueChange = { unit = it }, label = { Text(stringResource(R.string.recipe_form_unit)) }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val quantity = quantityText.replace(',', '.').toDoubleOrNull() ?: 1.0
                    onSave(emoji.trim(), food.trim(), quantity, unit.trim())
                },
                enabled = emoji.isNotBlank() && food.isNotBlank(),
            ) { Text(stringResource(R.string.recipe_form_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.recipe_delete_confirm_cancel)) }
        },
    )
}
