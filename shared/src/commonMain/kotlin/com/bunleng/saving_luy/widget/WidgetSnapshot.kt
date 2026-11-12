package com.bunleng.saving_luy.widget

import com.bunleng.saving_luy.domain.BudgetCategory
import com.bunleng.saving_luy.domain.CategoryBudgetEntry
import com.bunleng.saving_luy.ui.MoneyFormatter

/**
 * The small summary home screen widgets show. Widgets read only this, never the database.
 * Amounts are Long in minor units; the *Text fields are already formatted by the shared
 * [MoneyFormatter] so native widget code never formats money itself.
 */
data class WidgetSnapshot(
    val readyToAssign: Long,
    val readyToAssignText: String,
    val topCategories: List<WidgetCategory>,
    val currencyCode: String,
) {
    companion object {
        const val TOP_CATEGORY_COUNT = 3

        /**
         * Build the snapshot: the [TOP_CATEGORY_COUNT] categories with the highest Available.
         * Ties keep the order of [categories] (the Budget screen order).
         */
        fun from(
            readyToAssign: Long,
            categories: List<Pair<BudgetCategory, CategoryBudgetEntry>>,
            currencyCode: String,
        ): WidgetSnapshot = WidgetSnapshot(
            readyToAssign = readyToAssign,
            readyToAssignText = MoneyFormatter.format(readyToAssign, currencyCode),
            topCategories = categories
                .sortedByDescending { it.second.available }
                .take(TOP_CATEGORY_COUNT)
                .map { (category, entry) ->
                    WidgetCategory(category.name, entry.available, MoneyFormatter.format(entry.available, currencyCode))
                },
            currencyCode = currencyCode,
        )
    }
}

data class WidgetCategory(
    val name: String,
    val available: Long,
    val availableText: String,
)

/**
 * Storage keys shared with the native widget code (Glance on Android, WidgetKit on iOS).
 * Changing a key here means changing it in the widget code too.
 */
object WidgetSnapshotKeys {
    /** Android SharedPreferences file name. */
    const val ANDROID_PREFS_NAME = "widget_snapshot"

    /** iOS App Group used for UserDefaults(suiteName:). Must match the app's entitlements. */
    const val IOS_APP_GROUP = "group.com.bunleng.saving_luy"

    const val READY_TO_ASSIGN = "ready_to_assign"
    const val READY_TO_ASSIGN_TEXT = "ready_to_assign_text"
    const val CURRENCY_CODE = "currency_code"
    const val CATEGORY_COUNT = "category_count"

    fun categoryName(index: Int) = "category_${index}_name"
    fun categoryAvailable(index: Int) = "category_${index}_available"
    fun categoryAvailableText(index: Int) = "category_${index}_available_text"
}

/** Receives a fresh snapshot after every data change. */
fun interface WidgetSnapshotWriter {
    fun save(snapshot: WidgetSnapshot)
}

/**
 * Platform storage for [WidgetSnapshot]. After saving it calls the platform's widget refresh:
 * Android: SharedPreferences, then GlanceAppWidget.updateAll (passed in by the app).
 * iOS: App Group UserDefaults, then WidgetCenter.reloadAllTimelines (passed in from Swift).
 */
expect class WidgetSnapshotStore : WidgetSnapshotWriter {
    override fun save(snapshot: WidgetSnapshot)

    /** The last saved snapshot, or null if none has been written yet. */
    fun load(): WidgetSnapshot?
}
