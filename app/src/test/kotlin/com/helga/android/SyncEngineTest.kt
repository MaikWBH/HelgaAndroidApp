package com.helga.android

import androidx.room.withTransaction
import com.helga.android.data.local.AppDatabase
import com.helga.android.data.local.dao.MonthlyBudgetDao
import com.helga.android.data.local.dao.OffProductDao
import com.helga.android.data.local.dao.QuickEmojiDao
import com.helga.android.data.local.dao.ReceiptDao
import com.helga.android.data.local.dao.RecipeDao
import com.helga.android.data.local.dao.RecipeFeedbackDao
import com.helga.android.data.local.dao.RecipeHistoryDao
import com.helga.android.data.local.dao.ShoppingDao
import com.helga.android.data.local.dao.StoreDao
import com.helga.android.data.local.dao.SyncDao
import com.helga.android.data.local.dao.TimestampRow
import com.helga.android.data.local.dao.WeekplanConstraintsDao
import com.helga.android.data.local.dao.WeekplanDao
import com.helga.android.data.local.dao.WeekplanSettingsDao
import com.helga.android.data.local.entity.RecipeEntity
import com.helga.android.data.preferences.AppPreferences
import com.helga.android.data.remote.SyncApi
import com.helga.android.data.remote.SyncApiFactory
import com.helga.android.data.remote.dto.RecipeDto
import com.helga.android.data.remote.dto.RecipeFeedbackDto
import com.helga.android.data.remote.dto.SyncPullResponse
import com.helga.android.data.sync.SyncEngine
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * Tests für Konfliktfälle im SyncEngine (sync A2): gleicher Zeitstempel (Ergänzung zu
 * [SyncLwwTest], das die LWW-Formel isoliert testet), Teilfehler beim Push (Server lehnt
 * einzelne Records ab, akzeptiert andere), Abbruch mitten im Sync (Push schlägt fehl, nachdem
 * der Pull schon angewendet wurde).
 *
 * Anders als bei den zuvor nachgezogenen Tests (wochenplan A5, statistik A1, ...) lässt sich
 * hier keine reine Formel extrahieren — die drei Fälle sind Eigenschaften der Ablaufsteuerung
 * in [SyncEngine.runFullSync] selbst (Reihenfolge Pull→Push→Cursor speichern, was bei einem
 * Fehler dazwischen bereits committet ist). Deshalb mit MockK direkt gegen die echte Klasse statt
 * gegen eine weitere Kopie der Formel. `database.withTransaction {}` (Room-KTX-Extension) wird
 * über [mockkStatic] auf die von Kotlin generierte Multifile-Facade-Klasse `RoomDatabaseKt`
 * gemockt, damit der Transaktions-Block ohne echte Room-Instanz einfach direkt ausgeführt wird.
 */
class SyncEngineTest {

    private val database = mockk<AppDatabase>()
    private val recipeDao = mockk<RecipeDao>(relaxed = true)
    private val syncDao = mockk<SyncDao>(relaxed = true)
    private val shoppingDao = mockk<ShoppingDao>(relaxed = true)
    private val storeDao = mockk<StoreDao>(relaxed = true)
    private val quickEmojiDao = mockk<QuickEmojiDao>(relaxed = true)
    private val weekplanDao = mockk<WeekplanDao>(relaxed = true)
    private val weekplanSettingsDao = mockk<WeekplanSettingsDao>(relaxed = true)
    private val weekplanConstraintsDao = mockk<WeekplanConstraintsDao>(relaxed = true)
    private val recipeHistoryDao = mockk<RecipeHistoryDao>(relaxed = true)
    private val recipeFeedbackDao = mockk<RecipeFeedbackDao>(relaxed = true)
    private val receiptDao = mockk<ReceiptDao>(relaxed = true)
    private val monthlyBudgetDao = mockk<MonthlyBudgetDao>(relaxed = true)
    private val offProductDao = mockk<OffProductDao>(relaxed = true)
    private val apiFactory = mockk<SyncApiFactory>()
    private val preferences = mockk<AppPreferences>(relaxed = true)
    private val api = mockk<SyncApi>()

    private lateinit var engine: SyncEngine

    @Before
    fun setUp() {
        mockkStatic("androidx.room.RoomDatabaseKt")
        coEvery { database.withTransaction<Any?>(any()) } coAnswers {
            val block = secondArg<suspend () -> Any?>()
            block()
        }
        coEvery { apiFactory.api() } returns api
        coEvery { preferences.ensureSyncProtocol() } just Runs
        coEvery { preferences.currentLastSyncTs() } returns 0L

        engine = SyncEngine(
            database, recipeDao, syncDao, shoppingDao, storeDao, quickEmojiDao,
            weekplanDao, weekplanSettingsDao, weekplanConstraintsDao,
            recipeHistoryDao, recipeFeedbackDao, receiptDao, monthlyBudgetDao,
            offProductDao, apiFactory, preferences,
        )
    }

    @After
    fun tearDown() {
        unmockkStatic("androidx.room.RoomDatabaseKt")
    }

    @Test
    fun `equal timestamp keeps the local record, server version is not applied`() = runTest {
        coEvery { syncDao.recipeTimestamps() } returns listOf(TimestampRow("r1", 100L))
        coEvery { api.pull(any()) } returns SyncPullResponse(
            serverTs = 500L,
            recipes = listOf(RecipeDto(id = "r1", updatedAt = 100L, name = "Server-Version")),
        )
        coEvery { api.push(any()) } returns SyncPullResponse(serverTs = 500L)

        engine.runFullSync()

        coVerify(exactly = 0) { recipeDao.upsertRecipes(any()) }
    }

    @Test
    fun `server record strictly newer than local is applied`() = runTest {
        coEvery { syncDao.recipeTimestamps() } returns listOf(TimestampRow("r1", 100L))
        coEvery { api.pull(any()) } returns SyncPullResponse(
            serverTs = 500L,
            recipes = listOf(RecipeDto(id = "r1", updatedAt = 200L, name = "Server-Version")),
        )
        coEvery { api.push(any()) } returns SyncPullResponse(serverTs = 500L)

        engine.runFullSync()

        coVerify {
            recipeDao.upsertRecipes(match { it.size == 1 && it[0].id == "r1" && it[0].name == "Server-Version" })
        }
    }

    @Test
    fun `partial push failure - accepted record's dirty flag is cleared, rejected record is overwritten instead`() = runTest {
        coEvery { syncDao.recipeTimestamps() } returns emptyList()
        coEvery { recipeDao.dirtyRecipes() } returns listOf(
            RecipeEntity(id = "accepted", name = "Mine", updatedAt = 100L, dirty = 1),
            RecipeEntity(id = "rejected", name = "Meine veraltete Version", updatedAt = 100L, dirty = 1),
        )
        coEvery { api.pull(any()) } returns SyncPullResponse(serverTs = 0L)
        // Der Server akzeptiert "accepted" (kommt nicht in der Antwort zurück) und lehnt
        // "rejected" ab, weil er zwischenzeitlich eine neuere Version hat.
        coEvery { api.push(any()) } returns SyncPullResponse(
            serverTs = 500L,
            recipes = listOf(RecipeDto(id = "rejected", updatedAt = 999L, name = "Server-Version")),
        )

        engine.runFullSync()

        coVerify { recipeDao.clearRecipeDirty(listOf("accepted")) }
        coVerify(exactly = 0) { recipeDao.clearRecipeDirty(match { "rejected" in it }) }
        coVerify {
            recipeDao.upsertRecipes(match { it.any { r -> r.id == "rejected" && r.name == "Server-Version" } })
        }
    }

    @Test
    fun `aborted sync - the already-pulled data is kept but the cursor does not advance`() = runTest {
        coEvery { syncDao.recipeTimestamps() } returns emptyList()
        coEvery { api.pull(any()) } returns SyncPullResponse(
            serverTs = 500L,
            recipes = listOf(RecipeDto(id = "r1", updatedAt = 100L, name = "Vom Server")),
        )
        coEvery { api.push(any()) } throws IOException("Verbindung verloren")

        var thrown: Throwable? = null
        try {
            engine.runFullSync()
        } catch (e: IOException) {
            thrown = e
        }
        assertNotNull("runFullSync() sollte den Push-Fehler nicht verschlucken", thrown)

        // Der Pull wurde trotzdem schon übernommen ...
        coVerify { recipeDao.upsertRecipes(match { it.any { r -> r.id == "r1" } }) }
        // ... aber der Sync-Cursor rückt nicht vor, sonst würde der nächste Versuch denselben
        // Pull-Stand nie wieder anfragen und der fehlgeschlagene Push wäre verloren.
        coVerify(exactly = 0) { preferences.saveLastSyncTs(any()) }
    }

    @Test
    fun `recipeFeedback respects LWW like every other entity - a stale server record does not clobber a newer local one`() = runTest {
        // rezepte A4: recipeHistory/recipeFeedback filterten früher nur "dto.updatedAt > 0" statt
        // wie jede andere Entity über syncDao.feedbackTimestamps() gegen den lokalen Stand zu
        // vergleichen — ein älterer Server-Datensatz hätte eine bereits neuere, noch nicht
        // gepushte lokale Änderung stillschweigend überschrieben (inkl. Verlust des
        // dirty-Flags). Jetzt vereinheitlicht auf dasselbe filterServerWins()-Muster wie alle
        // anderen Tabellen.
        coEvery { syncDao.feedbackTimestamps() } returns listOf(TimestampRow("f1", 200L))
        coEvery { api.pull(any()) } returns SyncPullResponse(
            serverTs = 500L,
            recipeFeedback = listOf(RecipeFeedbackDto(id = "f1", recipeId = "r1", liked = -1, updatedAt = 100L)),
        )
        coEvery { api.push(any()) } returns SyncPullResponse(serverTs = 500L)

        engine.runFullSync()

        coVerify(exactly = 0) { recipeFeedbackDao.upsertAll(any()) }
    }

    @Test
    fun `recipeFeedback still applies a genuinely newer server record`() = runTest {
        coEvery { syncDao.feedbackTimestamps() } returns listOf(TimestampRow("f1", 100L))
        coEvery { api.pull(any()) } returns SyncPullResponse(
            serverTs = 500L,
            recipeFeedback = listOf(RecipeFeedbackDto(id = "f1", recipeId = "r1", liked = 1, updatedAt = 200L)),
        )
        coEvery { api.push(any()) } returns SyncPullResponse(serverTs = 500L)

        engine.runFullSync()

        coVerify {
            recipeFeedbackDao.upsertAll(match { it.size == 1 && it[0].id == "f1" && it[0].liked == 1 })
        }
    }
}
