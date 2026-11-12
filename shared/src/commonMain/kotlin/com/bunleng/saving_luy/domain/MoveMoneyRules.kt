package com.bunleng.saving_luy.domain

enum class MoveMoneyError { MissingFrom, MissingTo, SameCategory, MissingAmount }

/** Rules for moving money between categories. Pure functions, unit tested. */
object MoveMoneyRules {

    /** Returns the first problem, or null if the move can be made. */
    fun validate(fromCategoryId: String?, toCategoryId: String?, amount: Long): MoveMoneyError? = when {
        fromCategoryId == null -> MoveMoneyError.MissingFrom
        toCategoryId == null -> MoveMoneyError.MissingTo
        fromCategoryId == toCategoryId -> MoveMoneyError.SameCategory
        amount <= 0 -> MoveMoneyError.MissingAmount
        else -> null
    }

    /** Suggested amount when moving into a category: enough to cover overspending, else nothing. */
    fun suggestedAmount(toAvailable: Long): Long = if (toAvailable < 0) -toAvailable else 0L
}
