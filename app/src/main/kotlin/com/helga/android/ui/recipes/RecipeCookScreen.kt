package com.helga.android.ui.recipes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.helga.android.R
import com.helga.android.data.local.entity.IngredientEntity
import com.helga.android.data.local.entity.InstructionEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class DetectedTimer(val label: String, val totalSeconds: Int)

private val TIMER_REGEX = Regex(
    """(\d+)\s*(Stunden?|Std\.?|Minuten?|Min\.?|min|Sekunden?|Sek\.?)""",
    RegexOption.IGNORE_CASE,
)

fun extractTimers(text: String): List<DetectedTimer> =
    TIMER_REGEX.findAll(text).map { match ->
        val value = match.groupValues[1].toInt()
        val unit = match.groupValues[2].lowercase().trimEnd('.')
        val seconds = when {
            unit.startsWith("stund") || unit == "std" -> value * 3600
            unit.startsWith("sek") -> value
            else -> value * 60  // Minuten / Min / min
        }
        DetectedTimer(label = match.value, totalSeconds = seconds)
    }.filter { it.totalSeconds > 0 }.distinctBy { it.totalSeconds }.toList()

private fun formatTimer(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

@Composable
private fun IngredientCheckRow(
    ingredient: IngredientEntity,
    checked: Boolean,
    onToggle: () -> Unit,
    scaleFactor: Float = 1f,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        val label = buildString {
            if (ingredient.quantity > 0) {
                val q = ingredient.quantity * scaleFactor
                append(if (q % 1.0 < 0.01) q.toInt().toString() else "%.1f".format(q))
                append(" ")
            }
            if (ingredient.unit.isNotBlank()) { append(ingredient.unit); append(" ") }
            append(ingredient.food)
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(
                textDecoration = if (checked) TextDecoration.LineThrough else TextDecoration.None,
            ),
            color = if (checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeCookScreen(
    onBack: () -> Unit,
    viewModel: RecipeCookViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val instructions = uiState.instructions
    val ingredients = uiState.ingredients
    val checkedIds = uiState.checkedIngredientIds
    val completedSteps by viewModel.completedSteps.collectAsStateWithLifecycle()
    val servings by viewModel.servings.collectAsStateWithLifecycle()
    val baseServings by viewModel.baseServings.collectAsStateWithLifecycle()
    val scaleFactor by viewModel.scaleFactor.collectAsStateWithLifecycle()
    var ingredientsExpanded by remember { mutableStateOf(false) }
    var activeTimer by remember { mutableStateOf<DetectedTimer?>(null) }
    var timerSeconds by remember { mutableIntStateOf(0) }
    var timerRunning by remember { mutableStateOf(false) }
    var focusMode by remember { mutableStateOf(false) }

    LaunchedEffect(timerRunning) {
        while (timerRunning && timerSeconds > 0) {
            delay(1_000L)
            timerSeconds--
        }
        if (timerSeconds == 0) timerRunning = false
    }

    if (activeTimer != null) {
        TimerDialog(
            label = activeTimer!!.label,
            totalSeconds = activeTimer!!.totalSeconds,
            remainingSeconds = timerSeconds,
            running = timerRunning,
            onToggle = { timerRunning = !timerRunning },
            onReset = { timerSeconds = activeTimer!!.totalSeconds; timerRunning = false },
            onDismiss = { activeTimer = null; timerRunning = false },
        )
    }

    // Bildschirm während der Kochansicht eingeschaltet lassen
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.recipe?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.recipe_detail_back))
                    }
                },
                actions = {
                    if (instructions.isNotEmpty()) {
                        IconButton(onClick = { focusMode = !focusMode }) {
                            Icon(
                                imageVector = if (focusMode) Icons.Filled.FormatListBulleted else Icons.Filled.ViewCarousel,
                                contentDescription = stringResource(
                                    if (focusMode) R.string.cook_focus_mode_off else R.string.cook_focus_mode_on
                                ),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (instructions.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                if (uiState.recipe == null) {
                    CircularProgressIndicator()
                } else {
                    Text(
                        stringResource(R.string.cook_no_steps),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            return@Scaffold
        }

        val total = instructions.size

        if (focusMode) {
            CookFocusView(
                instructions = instructions,
                completedSteps = completedSteps,
                onToggleStep = viewModel::toggleStep,
                onStartTimer = { timer ->
                    activeTimer = timer
                    timerSeconds = timer.totalSeconds
                    timerRunning = false
                },
                onDone = {
                    viewModel.confirmCooked()
                    onBack()
                },
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
        ) {
            val personalNotes = uiState.recipe?.personalNotes.orEmpty()
            if (personalNotes.isNotBlank()) {
                item(key = "personal_notes") {
                    androidx.compose.material3.Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = androidx.compose.material3.CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        ),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "📝 ${stringResource(R.string.recipe_personal_notes)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = personalNotes,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                    }
                }
            }
            if (ingredients.isNotEmpty()) {
                item(key = "ingredients_header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { ingredientsExpanded = !ingredientsExpanded }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.recipe_detail_ingredients),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                        val checked = checkedIds.size
                        if (checked > 0) {
                            Text(
                                text = "$checked/${ingredients.size}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Icon(
                            imageVector = if (ingredientsExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = null,
                        )
                    }
                    HorizontalDivider()
                }
                if (ingredientsExpanded && baseServings > 0) {
                    item(key = "servings_control") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            IconButton(
                                onClick = { viewModel.setServings(servings - 1) },
                                enabled = servings > 1,
                            ) {
                                Icon(Icons.Filled.Remove, contentDescription = null)
                            }
                            Text(
                                text = "$servings ${stringResource(R.string.recipe_detail_yield)}",
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.widthIn(min = 100.dp),
                            )
                            IconButton(onClick = { viewModel.setServings(servings + 1) }) {
                                Icon(Icons.Filled.Add, contentDescription = null)
                            }
                        }
                    }
                }
                if (ingredientsExpanded) {
                    items(ingredients, key = { it.id }) { ingredient ->
                        IngredientCheckRow(
                            ingredient = ingredient,
                            checked = ingredient.id in checkedIds,
                            onToggle = { viewModel.toggleIngredient(ingredient.id) },
                            scaleFactor = scaleFactor,
                        )
                    }
                }
            }

            item(key = "steps_header") {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.recipe_detail_instructions),
                    style = MaterialTheme.typography.titleSmall,
                )
                HorizontalDivider()
            }

            items(total, key = { "step_$it" }) { index ->
                val instruction = instructions[index]
                val done = index in completedSteps
                val timers = remember(instruction.text) { extractTimers(instruction.text) }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.toggleStep(index) }
                        .padding(vertical = 8.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "${index + 1}.",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (done) MaterialTheme.colorScheme.onSurfaceVariant
                                   else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 1.dp),
                        )
                        Text(
                            text = instruction.text,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None,
                            ),
                            color = if (done) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                   else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (timers.isNotEmpty() && !done) {
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(start = 28.dp, top = 4.dp),
                        ) {
                            timers.forEach { timer ->
                                SuggestionChip(
                                    onClick = {
                                        activeTimer = timer
                                        timerSeconds = timer.totalSeconds
                                        timerRunning = false
                                    },
                                    label = { Text(timer.label) },
                                    icon = {
                                        Icon(
                                            Icons.Filled.Timer,
                                            contentDescription = null,
                                            modifier = Modifier.size(SuggestionChipDefaults.IconSize),
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
                if (index < total - 1) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 4.dp))
                }
            }

            item(key = "done_button") {
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        viewModel.confirmCooked()
                        onBack()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.cook_done))
                }
            }
        }
    }
}

/**
 * Fokusansicht: ein Zubereitungsschritt pro Seite in Großschrift, per Wisch oder
 * Pfeiltasten navigierbar. Nutzt denselben [completedSteps]-State wie die
 * Listenansicht, damit beide Modi synchron bleiben.
 */
@Composable
private fun CookFocusView(
    instructions: List<InstructionEntity>,
    completedSteps: Set<Int>,
    onToggleStep: (Int) -> Unit,
    onStartTimer: (DetectedTimer) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val total = instructions.size
    val pagerState = rememberPagerState(pageCount = { total })
    val scope = rememberCoroutineScope()

    Column(modifier = modifier.fillMaxSize()) {
        LinearProgressIndicator(
            progress = { (pagerState.currentPage + 1).toFloat() / total },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(R.string.cook_step_of, pagerState.currentPage + 1, total),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { page ->
            val instruction = instructions[page]
            val done = page in completedSteps
            val timers = remember(instruction.text) { extractTimers(instruction.text) }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onToggleStep(page) }
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = instruction.text,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None,
                    ),
                    textAlign = TextAlign.Center,
                    color = if (done) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                           else MaterialTheme.colorScheme.onSurface,
                )
                if (timers.isNotEmpty()) {
                    Spacer(Modifier.height(24.dp))
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        timers.forEach { timer ->
                            SuggestionChip(
                                onClick = { onStartTimer(timer) },
                                label = { Text(timer.label) },
                                icon = {
                                    Icon(
                                        Icons.Filled.Timer,
                                        contentDescription = null,
                                        modifier = Modifier.size(SuggestionChipDefaults.IconSize),
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                enabled = pagerState.currentPage > 0,
            ) {
                Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = stringResource(R.string.cook_prev))
            }
            if (pagerState.currentPage == total - 1) {
                Button(onClick = onDone) { Text(stringResource(R.string.cook_done)) }
            } else {
                FilledTonalButton(
                    onClick = {
                        onToggleStep(pagerState.currentPage)
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    },
                ) { Text(stringResource(R.string.cook_next)) }
            }
            IconButton(
                onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                enabled = pagerState.currentPage < total - 1,
            ) {
                Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = stringResource(R.string.cook_next))
            }
        }
    }
}

@Composable
private fun TimerDialog(
    label: String,
    totalSeconds: Int,
    remainingSeconds: Int,
    running: Boolean,
    onToggle: () -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val finished = remainingSeconds == 0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Timer, contentDescription = null)
                Text(label)
            }
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = if (finished) stringResource(R.string.cook_timer_done)
                           else formatTimer(remainingSeconds),
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (finished) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary,
                )
                if (!finished) {
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { remainingSeconds.toFloat() / totalSeconds },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            if (!finished) {
                Button(onClick = onToggle) {
                    Text(
                        stringResource(
                            if (running) R.string.cook_timer_pause else R.string.cook_timer_start
                        )
                    )
                }
            } else {
                Button(onClick = onDismiss) { Text(stringResource(R.string.cook_done)) }
            }
        },
        dismissButton = {
            if (!finished) {
                OutlinedButton(onClick = onReset) { Text(stringResource(R.string.cook_timer_reset)) }
            }
        },
    )
}
