package com.helga.android

import androidx.room.withTransaction
import com.helga.android.data.local.AppDatabase
import com.helga.android.data.local.dao.MonthlyBudgetDao
import com.helga.android.data.local.dao.OffProductDao
import com.helga.android.data.local.dao.QuickEmojiDao
import com.helga.android.data.local.dao.RecipeDao
import com.helga.android.data.local.dao.RecipeFeedbackDao
import com.helga.android.data.local.dao.RecipeHistoryDao
import com.helga.android.data.local.dao.ShoppingDao
import com.helga.android.data.local.dao.StoreDao
import com.helga.android.data.local.dao.SyncDao
import com.helga.android.data.local.dao.WeekplanConstraintsDao
import com.helga.android.data.local.dao.WeekplanDao
import com.helga.android.data.local.dao.WeekplanSettingsDao
import com.helga.android.data.local.entity.StoreAisleEntity
import com.helga.android.data.preferences.AppPreferences
import com.helga.android.data.remote.SyncApi
import com.helga.android.data.remote.SyncApiFactory
import com.helga.android.data.remote.dto.StoreAisleDto
import com.helga.android.data.remote.dto.SyncPullResponse
import com.helga.android.data.sync.SyncEngine
import com.squareup.moshi.Moshi
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Der Kategorie-Schlüssel eines Gangs (Phase 3b) läuft durch DTO, Moshi und SyncEngine. */
class StoreAisleSyncTest {

    private val adapter = Moshi.Builder().build().adapter(StoreAisleDto::class.java)

    @Test
    fun `dto without category_key defaults to empty`() {
        val dto = adapter.fromJson("""{"id":"a","updated_at":1,"store_id":"s","aisle_name":"Obst"}""")!!
        assertEquals("", dto.categoryKey)
    }

    @Test
    fun `dto with category_key reads and writes it`() {
        val dto = adapter.fromJson("""{"id":"a","updated_at":1,"store_id":"s","category_key":"obst_gemuese"}""")!!
        assertEquals("obst_gemuese", dto.categoryKey)
        assertTrue(adapter.toJson(dto).contains(""""category_key":"obst_gemuese""""))
    }

    // ── über die echte SyncEngine (Aufbau wie SyncEngineTest) ──────────────────────────────

    private val database = mockk<AppDatabase>()
    private val storeDao = mockk<StoreDao>(relaxed = true)
    private val syncDao = mockk<SyncDao>(relaxed = true)
    private val apiFactory = mockk<SyncApiFactory>()
    private val preferences = mockk<AppPreferences>(relaxed = true)
    private val api = mockk<SyncApi>()

    @Before
    fun setUp() {
        mockkStatic("androidx.room.RoomDatabaseKt")
        coEvery { database.withTransaction<Any?>(any()) } coAnswers { secondArg<suspend () -> Any?>().invoke() }
        coEvery { apiFactory.api() } returns api
        coEvery { preferences.ensureSyncProtocol() } just Runs
        coEvery { preferences.currentLastSyncTs() } returns 0L
    }

    @After
    fun tearDown() {
        unmockkStatic("androidx.room.RoomDatabaseKt")
    }

    private fun engine() = SyncEngine(
        database, mockk<RecipeDao>(relaxed = true), syncDao, mockk<ShoppingDao>(relaxed = true), storeDao,
        mockk<QuickEmojiDao>(relaxed = true), mockk<WeekplanDao>(relaxed = true),
        mockk<WeekplanSettingsDao>(relaxed = true), mockk<WeekplanConstraintsDao>(relaxed = true),
        mockk<RecipeHistoryDao>(relaxed = true), mockk<RecipeFeedbackDao>(relaxed = true),
        mockk<MonthlyBudgetDao>(relaxed = true), mockk<OffProductDao>(relaxed = true), apiFactory, preferences,
    )

    @Test
    fun `pulled aisle keeps its category key in the local entity`() = runTest {
        coEvery { api.pull(any()) } returns SyncPullResponse(
            serverTs = 5L,
            storeAisles = listOf(StoreAisleDto(id = "a", updatedAt = 10, storeId = "s", aisleName = "Obst", categoryKey = "obst_gemuese")),
        )
        coEvery { api.push(any()) } returns SyncPullResponse(serverTs = 5L)

        engine().runFullSync()

        coVerify { storeDao.upsertAisles(match<List<StoreAisleEntity>> { it.size == 1 && it[0].categoryKey == "obst_gemuese" }) }
    }

    @Test
    fun `dirty aisle is pushed with its category key`() = runTest {
        coEvery { api.pull(any()) } returns SyncPullResponse(serverTs = 5L)
        coEvery { storeDao.dirtyAisles() } returns listOf(
            StoreAisleEntity(id = "a", storeId = "s", aisleName = "Fisch", categoryKey = "fisch", updatedAt = 10, dirty = 1)
        )
        val pushed = mutableListOf<List<StoreAisleDto>>()
        coEvery { api.push(any()) } answers { pushed += firstArg<com.helga.android.data.remote.dto.SyncPushRequest>().storeAisles; SyncPullResponse(serverTs = 5L) }

        engine().runFullSync()

        assertEquals("fisch", pushed.single().single().categoryKey)
    }
}
