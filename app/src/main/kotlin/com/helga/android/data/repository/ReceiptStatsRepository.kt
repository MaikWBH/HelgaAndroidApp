package com.helga.android.data.repository

import com.helga.android.data.local.dao.ReceiptAggregateDao
import com.helga.android.data.local.dao.ReceiptDao
import com.helga.android.data.local.entity.ReceiptAggregateEntity
import com.helga.android.data.receipts.ReceiptSyncer
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** Quelle der Kostenübersicht: der lokale Abzug der Server-Auswertung (offline verfügbar). */
@Singleton
class ReceiptStatsRepository @Inject constructor(
    private val aggregateDao: ReceiptAggregateDao,
    private val receiptDao: ReceiptDao,
    private val receiptSyncer: ReceiptSyncer,
) {
    fun observeAggregates(): Flow<List<ReceiptAggregateEntity>> = aggregateDao.observeAll()

    /** Holt die Auswertung neu vom Server; `false` bei Fehler (der alte Abzug bleibt). */
    suspend fun refresh(): Boolean = receiptSyncer.refreshAggregates()

    /** Anzahl vorläufiger Bons, die in den Diagrammen noch fehlen. */
    fun observeProvisionalCount(): Flow<Int> = receiptDao.observeProvisionalCount()
}
