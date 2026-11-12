package com.helga.android.ui.receipts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.helga.android.R
import com.helga.android.data.local.entity.ReceiptLexiconEntity
import com.helga.android.data.model.ProductCategory
import com.helga.android.ui.common.asString
import com.helga.android.ui.components.PreviewSurface
import com.helga.android.ui.components.ThemePreviews

/** Das vom Server gelernte Bon-Lexikon (Kürzel je Markt → Artikel), einsehbar und löschbar. */
@Composable
fun ReceiptLexiconScreen(
    onBack: () -> Unit,
    viewModel: ReceiptLexiconViewModel = hiltViewModel(),
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.message.collect { snackbarHostState.showSnackbar(it.asString(context)) }
    }

    ReceiptLexiconContent(
        entries = entries,
        refreshing = refreshing,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onDelete = viewModel::delete,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReceiptLexiconContent(
    entries: List<ReceiptLexiconEntity>,
    refreshing: Boolean,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.receipt_lexicon_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.recipe_detail_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            if (entries.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.receipt_lexicon_empty),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            } else {
                val groups = entries.groupBy { it.storeName }
                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
                    groups.forEach { (storeName, storeEntries) ->
                        item(key = "store-$storeName") {
                            Text(
                                storeName.ifBlank { stringResource(R.string.receipt_lexicon_no_store) },
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                            )
                        }
                        items(storeEntries, key = { it.id }) { entry ->
                            LexiconRow(entry = entry, onDelete = { onDelete(entry.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LexiconRow(entry: ReceiptLexiconEntity, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.receipt_lexicon_mapping, entry.rawText, entry.name),
                fontWeight = FontWeight.Medium,
            )
            Text(
                stringResource(
                    R.string.receipt_lexicon_detail,
                    stringResource(ProductCategory.fromKey(entry.category).labelRes()),
                    entry.hits,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.receipt_lexicon_delete))
        }
    }
}

@ThemePreviews
@Composable
private fun ReceiptLexiconPreview() = PreviewSurface {
    ReceiptLexiconContent(
        entries = listOf(
            ReceiptLexiconEntity(id = "1", storeName = "REWE", rawText = "G&G H-MILCH", name = "H-Milch 3,5 %",
                category = "milch_eier", hits = 4),
            ReceiptLexiconEntity(id = "2", storeName = "REWE", rawText = "BIO BAN.", name = "Bananen",
                category = "obst_gemuese", hits = 2),
            ReceiptLexiconEntity(id = "3", storeName = "", rawText = "XYZ", name = "Unbekannt", hits = 1),
        ),
        refreshing = false,
        snackbarHostState = remember { SnackbarHostState() },
        onBack = {}, onRefresh = {}, onDelete = {},
    )
}
