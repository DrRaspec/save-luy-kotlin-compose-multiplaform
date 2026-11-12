package com.bunleng.saving_luy.domain

import java.util.Calendar

actual fun currentBudgetMonth(): BudgetMonth {
    val calendar = Calendar.getInstance()
    return BudgetMonth(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1)
}

actual fun currentDate(): SimpleDate {
    val calendar = Calendar.getInstance()
    return SimpleDate(
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH) + 1,
        calendar.get(Calendar.DAY_OF_MONTH),
    )
}
