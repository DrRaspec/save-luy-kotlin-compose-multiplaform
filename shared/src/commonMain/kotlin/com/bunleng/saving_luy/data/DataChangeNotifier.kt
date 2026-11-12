package com.bunleng.saving_luy.data

/**
 * Tells screens to reload after any write to the database.
 * Repositories call [notifyChanged]; view models listen while they are alive.
 */
class DataChangeNotifier {

    private val listeners = mutableSetOf<() -> Unit>()

    fun addListener(listener: () -> Unit) {
        listeners += listener
    }

    fun removeListener(listener: () -> Unit) {
        listeners -= listener
    }

    fun notifyChanged() {
        listeners.toList().forEach { it() }
    }
}
