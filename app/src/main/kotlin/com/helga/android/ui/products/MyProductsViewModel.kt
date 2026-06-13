package com.helga.android.ui.products

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.helga.android.data.local.dao.OffProductDao
import com.helga.android.data.local.entity.OffProductEntity
import com.helga.android.data.remote.SyncApiFactory
import com.helga.android.data.remote.dto.OffLookupBarcodeRequest
import com.helga.android.data.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class MyProductsViewModel @Inject constructor(
    private val offProductDao: OffProductDao,
    private val apiFactory: SyncApiFactory,
    private val syncScheduler: SyncScheduler,
) : ViewModel() {

    val products: StateFlow<List<OffProductEntity>> = offProductDao.observeFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _scannedProduct = MutableStateFlow<OffProductEntity?>(null)
    val scannedProduct: StateFlow<OffProductEntity?> = _scannedProduct.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun onBarcodeScanned(barcode: String) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val dto = apiFactory.api().lookupBarcode(OffLookupBarcodeRequest(barcode))
                val cached = offProductDao.getByBarcode(barcode)
                _scannedProduct.value = OffProductEntity(
                    id = dto.id.ifBlank { barcode },
                    barcode = dto.barcode.ifBlank { barcode },
                    name = dto.name,
                    brand = dto.brand,
                    categories = dto.categories,
                    kcalPerUnit = dto.kcalPerUnit,
                    proteins = dto.proteins,
                    fats = dto.fats,
                    carbs = dto.carbs,
                    nutriScore = dto.nutriScore,
                    nova = dto.nova,
                    ecoScore = dto.ecoScore,
                    allergenes = dto.allergenes,
                    additives = dto.additives,
                    isOrganic = dto.isOrganic,
                    vegan = dto.vegan,
                    vegetarian = dto.vegetarian,
                    imagePath = dto.imagePath,
                    isFavorite = maxOf(dto.isFavorite, cached?.isFavorite ?: 0),
                    updatedAt = dto.updatedAt,
                    deleted = dto.deleted,
                )
            } catch (e: Exception) {
                Timber.w(e, "Barcode lookup failed: $barcode")
                _scannedProduct.value = OffProductEntity(
                    id = barcode,
                    barcode = barcode,
                    name = "Barcode: $barcode (nicht gefunden)",
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun confirmAddToCatalog() {
        val product = _scannedProduct.value ?: return
        viewModelScope.launch {
            try {
                offProductDao.insert(
                    product.copy(isFavorite = 1, dirty = 1, updatedAt = System.currentTimeMillis())
                )
                syncScheduler.triggerOneShot()
                _scannedProduct.value = null
            } catch (e: Exception) {
                Timber.e(e, "Failed to add product to catalog")
            }
        }
    }

    fun dismissScanned() {
        _scannedProduct.value = null
    }

    fun removeFromCatalog(product: OffProductEntity) {
        viewModelScope.launch {
            offProductDao.setFavorite(product.id, 0, System.currentTimeMillis())
            syncScheduler.triggerOneShot()
        }
    }
}
