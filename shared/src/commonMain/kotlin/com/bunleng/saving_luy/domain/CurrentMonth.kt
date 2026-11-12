package com.bunleng.saving_luy.domain

/** The current calendar month on this device. */
expect fun currentBudgetMonth(): BudgetMonth

/** Today's date in the device's time zone. */
expect fun currentDate(): SimpleDate
