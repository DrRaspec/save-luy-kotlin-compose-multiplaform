package com.bunleng.saving_luy.widget

import android.content.Context

/**
 * @param refreshWidgets called after each save; the app passes a call to GlanceAppWidget.updateAll.
 */
actual class WidgetSnapshotStore(
    context: Context,
    private val refreshWidgets: () -> Unit = {},
) : WidgetSnapshotWriter {

    private val prefs = context.applicationContext
        .getSharedPreferences(WidgetSnapshotKeys.ANDROID_PREFS_NAME, Context.MODE_PRIVATE)

    actual override fun save(snapshot: WidgetSnapshot) {
        prefs.edit().apply {
            clear()
            putLong(WidgetSnapshotKeys.READY_TO_ASSIGN, snapshot.readyToAssign)
            putString(WidgetSnapshotKeys.READY_TO_ASSIGN_TEXT, snapshot.readyToAssignText)
            putString(WidgetSnapshotKeys.CURRENCY_CODE, snapshot.currencyCode)
            putInt(WidgetSnapshotKeys.CATEGORY_COUNT, snapshot.topCategories.size)
            snapshot.topCategories.forEachIndexed { index, category ->
                putString(WidgetSnapshotKeys.categoryName(index), category.name)
                putLong(WidgetSnapshotKeys.categoryAvailable(index), category.available)
                putString(WidgetSnapshotKeys.categoryAvailableText(index), category.availableText)
            }
        }.apply()
        refreshWidgets()
    }

    actual fun load(): WidgetSnapshot? {
        if (!prefs.contains(WidgetSnapshotKeys.READY_TO_ASSIGN)) return null
        val count = prefs.getInt(WidgetSnapshotKeys.CATEGORY_COUNT, 0)
        return WidgetSnapshot(
            readyToAssign = prefs.getLong(WidgetSnapshotKeys.READY_TO_ASSIGN, 0L),
            readyToAssignText = prefs.getString(WidgetSnapshotKeys.READY_TO_ASSIGN_TEXT, null).orEmpty(),
            topCategories = (0 until count).map { index ->
                WidgetCategory(
                    name = prefs.getString(WidgetSnapshotKeys.categoryName(index), "").orEmpty(),
                    available = prefs.getLong(WidgetSnapshotKeys.categoryAvailable(index), 0L),
                    availableText = prefs.getString(WidgetSnapshotKeys.categoryAvailableText(index), null).orEmpty(),
                )
            },
            currencyCode = prefs.getString(WidgetSnapshotKeys.CURRENCY_CODE, null) ?: "USD",
        )
    }
}
