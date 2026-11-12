package com.bunleng.saving_luy.domain

enum class NameError { Blank, TooLong, Duplicate }

/** Rules for naming and deleting categories and groups. Pure functions, unit tested. */
object CategoryRules {

    const val MAX_NAME_LENGTH = 40

    /**
     * Returns the first problem with [name], or null if it can be saved.
     * Names are compared trimmed and case-insensitively against [otherNames].
     */
    fun validateName(name: String, otherNames: List<String>): NameError? {
        val trimmed = name.trim()
        return when {
            trimmed.isEmpty() -> NameError.Blank
            trimmed.length > MAX_NAME_LENGTH -> NameError.TooLong
            otherNames.any { it.trim().equals(trimmed, ignoreCase = true) } -> NameError.Duplicate
            else -> null
        }
    }

    /**
     * When a category is deleted into [into], its Assigned amounts are added month by month,
     * so Ready to Assign and the total Available stay the same.
     * Maps are keyed by [BudgetMonth.key].
     */
    fun mergeAssigned(from: Map<Long, Long>, into: Map<Long, Long>): Map<Long, Long> =
        (from.keys + into.keys).associateWith { (from[it] ?: 0L) + (into[it] ?: 0L) }
}

/** The one-line nudge shown under the Ready to Assign banner. */
sealed interface BudgetHint {
    /** Some categories are overspent this month; [firstName] is the first one in screen order. */
    data class Overspent(val count: Int, val firstName: String) : BudgetHint
    data object Unassigned : BudgetHint
    data object OverAssigned : BudgetHint

    companion object {
        /** Overspending wins because it needs fixing; null means nothing to say. */
        fun of(readyToAssign: Long, overspentNames: List<String>): BudgetHint? = when {
            overspentNames.isNotEmpty() -> Overspent(overspentNames.size, overspentNames.first())
            readyToAssign > 0 -> Unassigned
            readyToAssign < 0 -> OverAssigned
            else -> null
        }
    }
}
