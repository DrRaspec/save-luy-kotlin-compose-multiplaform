package com.bunleng.saving_luy.ui.components

/**
 * Keypad digit entry for amounts. Digits are cents, e.g. "1234" = $12.34.
 */
object AmountInput {

    /** Max ~$9,999,999.99. */
    private const val MAX_DIGITS = 9

    fun appendDigit(digits: String, digit: Char): String = when {
        digits.length >= MAX_DIGITS -> digits
        digits.isEmpty() && digit == '0' -> digits
        else -> digits + digit
    }

    fun backspace(digits: String): String = digits.dropLast(1)

    /** Starting digits for an existing amount; empty for zero. */
    fun fromCents(cents: Long): String = if (cents > 0) cents.toString() else ""
}
