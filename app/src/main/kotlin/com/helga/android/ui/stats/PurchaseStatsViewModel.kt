package com.helga.android.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.helga.android.data.local.dao.OffProductDao
import com.helga.android.data.local.dao.ProductPurchaseDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class PurchaseStatsViewModel @Inject constructor(
    private val productPurchaseDao: ProductPurchaseDao,
    private val offProductDao: OffProductDao,
) : ViewModel() {

    data class ProductWithCount(
        val offProductId: String,
        val name: String,
        val nutriScore: String = "",
        val count: Int,
    )

    data class PurchaseStats(
        val topProducts: List<ProductWithCount>,
    )

    val stats: StateFlow<PurchaseStats> = productPurchaseDao.observeAll()
        .map { purchases ->
            val productCounts = purchases
                .filter { it.deleted == 0 && it.offProductId.isNotBlank() }
                .groupingBy { it.offProductId }
                .eachCount()
                .entries
                .sortedByDescending { it.value }
                .take(10)

            val topProducts = productCounts.mapNotNull { (productId, count) ->
                val product = offProductDao.getById(productId)
                if (product != null) {
                    ProductWithCount(
                        offProductId = productId,
                        name = product.name,
                        nutriScore = product.nutriScore,
                        count = count,
                    )
                } else null
            }

            PurchaseStats(topProducts = topProducts)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PurchaseStats(emptyList()))
}
