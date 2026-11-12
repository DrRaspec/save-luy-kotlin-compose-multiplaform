package com.bunleng.saving_luy.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.bunleng.saving_luy.MainActivity

/**
 * Home screen widget. Small: Ready to Assign. Medium: plus the top 3 categories by Available.
 * Reads only the [WidgetSnapshot] written by the shared code. Tapping opens Add Transaction.
 */
class MoneyWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetSnapshotStore(context).load()
        provideContent {
            GlanceTheme {
                WidgetContent(snapshot)
            }
        }
    }

    companion object {
        private val SMALL = DpSize(110.dp, 40.dp)
        private val MEDIUM = DpSize(250.dp, 110.dp)
    }
}

class MoneyWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = MoneyWidget()
}

@Composable
private fun WidgetContent(snapshot: WidgetSnapshot?) {
    val context = LocalContext.current
    val openAddTransaction = actionStartActivity(
        Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_ADD_TRANSACTION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
    )
    val showCategories = LocalSize.current.width >= 250.dp && !snapshot?.topCategories.isNullOrEmpty()

    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(16.dp)
            .padding(12.dp)
            .clickable(openAddTransaction),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ReadyToAssign(snapshot, modifier = GlanceModifier.defaultWeight())
        if (showCategories && snapshot != null) {
            Spacer(GlanceModifier.width(12.dp))
            TopCategories(snapshot.topCategories, modifier = GlanceModifier.defaultWeight())
        }
    }
}

@Composable
private fun ReadyToAssign(snapshot: WidgetSnapshot?, modifier: GlanceModifier) {
    val amount = snapshot?.readyToAssign ?: 0L
    val label = when {
        snapshot == null -> "Open the app to start"
        amount < 0 -> "⚠ Over-assigned"
        amount > 0 -> "Ready to Assign"
        else -> "All money assigned"
    }
    Column(modifier = modifier, horizontalAlignment = Alignment.Start) {
        Text(
            text = snapshot?.readyToAssignText ?: "—",
            style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, color = statusColor(amount)),
            maxLines = 1,
        )
        Text(
            text = label,
            style = TextStyle(fontSize = 13.sp, color = GlanceTheme.colors.onSurfaceVariant),
            maxLines = 1,
        )
    }
}

@Composable
private fun TopCategories(categories: List<WidgetCategory>, modifier: GlanceModifier) {
    Column(modifier = modifier) {
        categories.forEachIndexed { index, category ->
            if (index > 0) Spacer(GlanceModifier.height(4.dp))
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = category.name,
                    style = TextStyle(fontSize = 13.sp, color = GlanceTheme.colors.onSurface),
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight(),
                )
                Text(
                    text = (if (category.available < 0) "⚠ " else "") + category.availableText,
                    style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = statusColor(category.available)),
                    maxLines = 1,
                )
            }
        }
    }
}

/** Same meaning as the app's status colors: green funded/zero RTA, amber unassigned, red negative. */
@Composable
private fun statusColor(amount: Long) = when {
    amount < 0 -> ColorProvider(day = Color(0xFFB71C1C), night = Color(0xFFFFCDD2))
    else -> ColorProvider(day = Color(0xFF1B5E20), night = Color(0xFFC8E6C9))
}
