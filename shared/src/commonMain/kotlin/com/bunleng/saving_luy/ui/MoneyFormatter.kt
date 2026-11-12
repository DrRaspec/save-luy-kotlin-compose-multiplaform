package com.bunleng.saving_luy.ui

/**
 * Single shared money formatter. Formats Long minor units (cents) for display.
 * USD only for now; currency code is kept so riel can be added later.
 */
object MoneyFormatter {

    /**
     * Format cents to a readable string, e.g. 123456 → "$1,234.56".
     * Negative amounts show a minus sign: -500 → "-$5.00".
     */
    fun format(cents: Long, currencyCode: String = "USD"): String {
        val sign = if (cents < 0) "-" else ""
        val absCents = if (cents < 0) -cents else cents
        val dollars = absCents / 100
        val remainder = absCents % 100
        val symbol = currencySymbol(currencyCode)
        val dollarsStr = formatWithCommas(dollars)
        val centsStr = remainder.toString().padStart(2, '0')
        return "$sign$symbol$dollarsStr.$centsStr"
    }

    /**
     * Parse a display string (digits only, no symbol) back to cents.
     * Input is the raw digit string from the keypad, e.g. "1234" → 1234 cents = $12.34.
     */
    fun parseCentsFromDigits(digits: String): Long =
        digits.toLongOrNull() ?: 0L

    /**
     * Parse a typed amount like "12", "12.5" or "1,234.56" to cents.
     * Returns null for anything else (negative, more than 2 decimals, letters).
     */
    fun parseCents(text: String): Long? {
        val cleaned = text.trim().replace(",", "")
        if (cleaned.isEmpty()) return 0L
        val match = AMOUNT_PATTERN.matchEntire(cleaned) ?: return null
        val (whole, fraction) = match.destructured
        if (whole.length > 9) return null
        val dollars = whole.ifEmpty { "0" }.toLong()
        return dollars * 100 + fraction.padEnd(2, '0').toLong()
    }

    /** Plain editable text for a non-negative amount, e.g. 1250 → "12.50", 0 → "". */
    fun toEditableText(cents: Long): String {
        if (cents <= 0) return ""
        return "${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"
    }

    private val AMOUNT_PATTERN = Regex("""(\d*)(?:\.(\d{0,2}))?""")

    private fun currencySymbol(code: String): String = when (code) {
        "USD" -> "$"
        "KHR" -> "៛"
        else -> "$"
    }

    private fun formatWithCommas(value: Long): String {
        val str = value.toString()
        val result = StringBuilder()
        for ((i, c) in str.reversed().withIndex()) {
            if (i > 0 && i % 3 == 0) result.append(',')
            result.append(c)
        }
        return result.reverse().toString()
    }
}
