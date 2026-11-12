package com.bunleng.saving_luy.widget

import platform.Foundation.NSUserDefaults

/**
 * @param reloadWidgets called after each save. WidgetCenter is Swift-only, so the app passes
 * `{ WidgetCenter.shared.reloadAllTimelines() }` from Swift.
 */
actual class WidgetSnapshotStore(
    private val reloadWidgets: () -> Unit = {},
) : WidgetSnapshotWriter {

    // Falls back to standard defaults if the App Group entitlement is missing.
    private val defaults = NSUserDefaults(suiteName = WidgetSnapshotKeys.IOS_APP_GROUP)

    actual override fun save(snapshot: WidgetSnapshot) {
        val previousCount = defaults.integerForKey(WidgetSnapshotKeys.CATEGORY_COUNT).toInt()
        defaults.setInteger(snapshot.readyToAssign, WidgetSnapshotKeys.READY_TO_ASSIGN)
        defaults.setObject(snapshot.readyToAssignText, WidgetSnapshotKeys.READY_TO_ASSIGN_TEXT)
        defaults.setObject(snapshot.currencyCode, WidgetSnapshotKeys.CURRENCY_CODE)
        defaults.setInteger(snapshot.topCategories.size.toLong(), WidgetSnapshotKeys.CATEGORY_COUNT)
        snapshot.topCategories.forEachIndexed { index, category ->
            defaults.setObject(category.name, WidgetSnapshotKeys.categoryName(index))
            defaults.setInteger(category.available, WidgetSnapshotKeys.categoryAvailable(index))
            defaults.setObject(category.availableText, WidgetSnapshotKeys.categoryAvailableText(index))
        }
        for (index in snapshot.topCategories.size until previousCount) {
            defaults.removeObjectForKey(WidgetSnapshotKeys.categoryName(index))
            defaults.removeObjectForKey(WidgetSnapshotKeys.categoryAvailable(index))
            defaults.removeObjectForKey(WidgetSnapshotKeys.categoryAvailableText(index))
        }
        reloadWidgets()
    }

    actual fun load(): WidgetSnapshot? {
        if (defaults.objectForKey(WidgetSnapshotKeys.READY_TO_ASSIGN) == null) return null
        val count = defaults.integerForKey(WidgetSnapshotKeys.CATEGORY_COUNT).toInt()
        return WidgetSnapshot(
            readyToAssign = defaults.integerForKey(WidgetSnapshotKeys.READY_TO_ASSIGN),
            readyToAssignText = defaults.stringForKey(WidgetSnapshotKeys.READY_TO_ASSIGN_TEXT).orEmpty(),
            topCategories = (0 until count).map { index ->
                WidgetCategory(
                    name = defaults.stringForKey(WidgetSnapshotKeys.categoryName(index)).orEmpty(),
                    available = defaults.integerForKey(WidgetSnapshotKeys.categoryAvailable(index)),
                    availableText = defaults.stringForKey(WidgetSnapshotKeys.categoryAvailableText(index)).orEmpty(),
                )
            },
            currencyCode = defaults.stringForKey(WidgetSnapshotKeys.CURRENCY_CODE) ?: "USD",
        )
    }
}
