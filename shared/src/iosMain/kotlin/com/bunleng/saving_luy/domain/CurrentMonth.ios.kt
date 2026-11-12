package com.bunleng.saving_luy.domain

import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitMonth
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSDate

actual fun currentBudgetMonth(): BudgetMonth {
    val components = NSCalendar.currentCalendar.components(
        NSCalendarUnitYear or NSCalendarUnitMonth,
        fromDate = NSDate(),
    )
    return BudgetMonth(components.year.toInt(), components.month.toInt())
}

actual fun currentDate(): SimpleDate {
    val components = NSCalendar.currentCalendar.components(
        NSCalendarUnitYear or NSCalendarUnitMonth or NSCalendarUnitDay,
        fromDate = NSDate(),
    )
    return SimpleDate(components.year.toInt(), components.month.toInt(), components.day.toInt())
}
