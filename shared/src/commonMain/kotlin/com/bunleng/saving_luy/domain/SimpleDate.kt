package com.bunleng.saving_luy.domain

/**
 * A calendar date without time or time zone. Stored in the DB as epoch day.
 */
data class SimpleDate(
    val year: Int,
    val month: Int, // 1-12
    val day: Int,
) : Comparable<SimpleDate> {

    val budgetMonth: BudgetMonth get() = BudgetMonth(year, month)

    override fun compareTo(other: SimpleDate): Int = toEpochDay().compareTo(other.toEpochDay())

    /** Days since 1970-01-01 (proleptic Gregorian calendar). */
    fun toEpochDay(): Long {
        // Algorithm from Howard Hinnant's "days_from_civil".
        val y = (if (month <= 2) year - 1 else year).toLong()
        val era = (if (y >= 0) y else y - 399) / 400
        val yearOfEra = y - era * 400
        val shiftedMonth = if (month > 2) month - 3 else month + 9
        val dayOfYear = (153 * shiftedMonth + 2) / 5 + day - 1
        val dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
        return era * 146_097 + dayOfEra - 719_468
    }

    /** Display name, e.g. "Nov 12, 2026". */
    fun displayName(): String = "${budgetMonth.monthName()} $day, $year"

    companion object {
        const val MILLIS_PER_DAY = 86_400_000L

        fun fromEpochDay(epochDay: Long): SimpleDate {
            // Algorithm from Howard Hinnant's "civil_from_days".
            val z = epochDay + 719_468
            val era = (if (z >= 0) z else z - 146_096) / 146_097
            val dayOfEra = z - era * 146_097
            val yearOfEra = (dayOfEra - dayOfEra / 1460 + dayOfEra / 36_524 - dayOfEra / 146_096) / 365
            val dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100)
            val mp = (5 * dayOfYear + 2) / 153
            val day = (dayOfYear - (153 * mp + 2) / 5 + 1).toInt()
            val month = (if (mp < 10) mp + 3 else mp - 9).toInt()
            val year = (yearOfEra + era * 400 + if (month <= 2) 1 else 0).toInt()
            return SimpleDate(year, month, day)
        }

        /** Convert UTC midnight millis (as used by the Material date picker). */
        fun fromUtcMillis(millis: Long): SimpleDate = fromEpochDay(millis.floorDiv(MILLIS_PER_DAY))
    }
}
