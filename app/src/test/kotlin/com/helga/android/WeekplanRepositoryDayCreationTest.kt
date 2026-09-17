package com.helga.android

import com.helga.android.data.local.dao.RecipeDao
import com.helga.android.data.local.dao.WeekplanDao
import com.helga.android.data.local.entity.WeekplanDayEntity
import com.helga.android.data.repository.RecipeRepository
import com.helga.android.data.repository.ShoppingRepository
import com.helga.android.data.repository.WeekplanRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regressionstest für den Doppelkarten-Bug aus wochenplan A16 (Nutzermeldung nach der
 * Zeitraum-Erweiterung): `WeekplanScreen` löst `ensureWeek()` über
 * `LaunchedEffect(currentPeriod)` erneut aus, sobald `savePeriod()` eine Zeitraum-Ausnahme
 * speichert — beide Coroutinen riefen `getOrCreateDay()` für dieselben neuen Tage parallel auf.
 * Ohne Sperre konnten beide `findDayByDate() == null` sehen, bevor eine von beiden den Tag
 * anlegt, wodurch zwei `WeekplanDayEntity`-Zeilen mit demselben `planDate` entstanden.
 */
class WeekplanRepositoryDayCreationTest {

    @Test
    fun `gleichzeitige getOrCreateDay-Aufrufe fuer denselben Tag legen nur eine Zeile an`() = runTest {
        val weekplanDao = mockk<WeekplanDao>(relaxed = true)
        val storedDays = mutableListOf<WeekplanDayEntity>()
        coEvery { weekplanDao.findDayByDate("2026-09-28") } coAnswers {
            // Erzwingt einen echten Suspension-Point, damit der TestCoroutineScheduler die 20
            // Aufrufe tatsächlich verschränkt ausführt statt jeden synchron durchlaufen zu lassen
            // — sonst würde der Test auch ohne die Mutex-Sperre zufällig grün sein.
            delay(1)
            storedDays.firstOrNull { it.planDate == "2026-09-28" }
        }
        coEvery { weekplanDao.upsertDay(any()) } answers {
            storedDays.add(firstArg())
        }

        val repository = WeekplanRepository(
            weekplanDao = weekplanDao,
            recipeDao = mockk<RecipeDao>(relaxed = true),
            recipeRepository = mockk<RecipeRepository>(relaxed = true),
            shoppingRepository = mockk<ShoppingRepository>(relaxed = true),
        )

        // Simuliert savePeriod() und die parallel durch currentPeriod erneut ausgelöste
        // ensureWeek(), die beide getOrCreateDay() für denselben neuen Tag aufrufen.
        List(20) { async { repository.getOrCreateDay("2026-09-28") } }.awaitAll()

        assertEquals(1, storedDays.size)
    }
}
