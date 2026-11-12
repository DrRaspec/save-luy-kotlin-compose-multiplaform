package com.bunleng.saving_luy.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Background + text pair. Each pair keeps at least 4.5:1 contrast. */
@Immutable
data class StatusColors(val container: Color, val content: Color)

/** Colors for budget states (funded, needs attention, overspent, zero). */
@Immutable
data class BudgetStatusColors(
    val positive: StatusColors,
    val warning: StatusColors,
    val negative: StatusColors,
    val neutral: StatusColors,
)

private val LightBudgetStatusColors = BudgetStatusColors(
    positive = StatusColors(Color(0xFFC8E6C9), Color(0xFF1B5E20)),
    warning = StatusColors(Color(0xFFFFE082), Color(0xFF5D4000)),
    negative = StatusColors(Color(0xFFFFCDD2), Color(0xFFB71C1C)),
    neutral = StatusColors(Color(0xFFE0E0E0), Color(0xFF424242)),
)

private val DarkBudgetStatusColors = BudgetStatusColors(
    positive = StatusColors(Color(0xFF1B5E20), Color(0xFFC8E6C9)),
    warning = StatusColors(Color(0xFF5D4000), Color(0xFFFFE082)),
    negative = StatusColors(Color(0xFF8E1B1B), Color(0xFFFFCDD2)),
    neutral = StatusColors(Color(0xFF424242), Color(0xFFE0E0E0)),
)

val LocalBudgetStatusColors = staticCompositionLocalOf { LightBudgetStatusColors }

/** App theme: Material 3, follows the system light/dark setting. */
@Composable
fun MoneyAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val statusColors = if (darkTheme) DarkBudgetStatusColors else LightBudgetStatusColors
    CompositionLocalProvider(LocalBudgetStatusColors provides statusColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme(),
            content = content,
        )
    }
}
