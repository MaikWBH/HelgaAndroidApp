package com.helga.android.ui.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.helga.android.MainActivity
import com.helga.android.data.local.AppDatabase
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

class TodayRecipeWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val db = AppDatabase.build(context)
        val todayStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val dayName = LocalDate.now().dayOfWeek.getDisplayName(JavaTextStyle.FULL, Locale.GERMAN)

        val recipeId = db.weekplanDao().observeTodayRecipeId(todayStr).first()
        val recipeName = recipeId?.let { db.weekplanDao().recipeName(it) }

        provideContent {
            GlanceTheme {
                WidgetContent(
                    dayName = dayName,
                    recipeName = recipeName,
                    context = context,
                )
            }
        }
    }
}

@Composable
private fun WidgetContent(
    dayName: String,
    recipeName: String?,
    context: Context,
) {
    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
    }
    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(12.dp)
            .background(GlanceTheme.colors.surface)
            .clickable(actionStartActivity(intent)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "🍳",
                style = TextStyle(fontSize = 24.sp),
            )
            Text(
                text = " Helga – $dayName",
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = GlanceTheme.colors.onSurface,
                ),
            )
        }
        Spacer(modifier = GlanceModifier.height(4.dp))
        Text(
            text = recipeName ?: "Kein Rezept geplant",
            style = TextStyle(
                fontSize = if (recipeName != null) 16.sp else 14.sp,
                fontWeight = if (recipeName != null) FontWeight.Medium else FontWeight.Normal,
                color = if (recipeName != null) GlanceTheme.colors.primary
                        else GlanceTheme.colors.onSurfaceVariant,
            ),
        )
    }
}

class TodayRecipeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayRecipeWidget()
}
