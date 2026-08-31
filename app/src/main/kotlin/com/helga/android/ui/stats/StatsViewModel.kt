package com.helga.android.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.helga.android.data.local.dao.RecipeDao
import com.helga.android.data.local.dao.RecipeHistoryDao
import com.helga.android.data.local.entity.RecipeEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/** Zeitraumfilter analog zu [com.helga.android.ui.receipts.TimePeriod] im Ausgabenüberblick. */
enum class StatsPeriod { WEEK, MONTH, ALL }

data class MonthStats(
    val totalCooked: Int = 0,
    val meatCount: Int = 0,
    val fishCount: Int = 0,
    val vegCount: Int = 0,
    val otherCount: Int = 0,
    val topRecipes: List<Pair<String, Int>> = emptyList(),
    val firstTimers: List<String> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatsViewModel @Inject constructor(
    private val historyDao: RecipeHistoryDao,
    private val recipeDao: RecipeDao,
) : ViewModel() {

    private val _period = MutableStateFlow(StatsPeriod.MONTH)
    val period: StateFlow<StatsPeriod> = _period

    fun setPeriod(period: StatsPeriod) {
        _period.value = period
    }

    private fun sinceDateFor(period: StatsPeriod): String {
        val today = LocalDate.now()
        val start = when (period) {
            StatsPeriod.WEEK -> today.minus(6, ChronoUnit.DAYS)
            StatsPeriod.MONTH -> today.withDayOfMonth(1)
            StatsPeriod.ALL -> LocalDate.ofEpochDay(0)
        }
        return start.format(DateTimeFormatter.ISO_LOCAL_DATE)
    }

    val stats: StateFlow<MonthStats> = _period.flatMapLatest { period ->
        val sinceDate = sinceDateFor(period)
        historyDao.observeSince(sinceDate).map { history ->
            val recipeIds = history.map { it.recipeId }
            val recipeCounts = recipeIds.groupingBy { it }.eachCount()
                .entries.sortedByDescending { it.value }

            val recipes = mutableMapOf<String, RecipeEntity>()
            recipeIds.distinct().forEach { id ->
                recipeDao.findById(id)?.let { recipes[id] = it }
            }

            val topRecipes = recipeCounts.take(5).mapNotNull { (id, count) ->
                recipes[id]?.name?.let { it to count }
            }

            var meat = 0; var fish = 0; var veg = 0; var other = 0
            recipeIds.forEach { id ->
                when (recipes[id]?.proteinType?.lowercase()) {
                    "fleisch" -> meat++
                    "fisch" -> fish++
                    "vegetarisch", "vegan" -> veg++
                    else -> other++
                }
            }

            // First-timers: Rezepte, die im Zeitraum gekocht wurden, aber davor noch nie
            val previousRecipeIds = historyDao.getRecipeIdsBefore(sinceDate).toSet()
            val firstTimers = recipeIds.distinct()
                .filter { it !in previousRecipeIds }
                .take(5)
                .mapNotNull { recipes[it]?.name }

            MonthStats(
                totalCooked = recipeIds.size,
                meatCount = meat,
                fishCount = fish,
                vegCount = veg,
                otherCount = other,
                topRecipes = topRecipes,
                firstTimers = firstTimers,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MonthStats())
}
