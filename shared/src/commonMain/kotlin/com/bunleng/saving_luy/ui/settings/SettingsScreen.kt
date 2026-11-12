package com.bunleng.saving_luy.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bunleng.saving_luy.data.AccountRepository

/**
 * Settings tab. Information only for now: the app has no user-changeable settings in scope yet.
 */
@Composable
fun SettingsScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)

        SettingsCard(
            listOf(
                "Currency" to "US Dollar (${AccountRepository.DEFAULT_CURRENCY})",
                "Theme" to "Follows your phone's light / dark setting",
                "Your data" to "Stored only on this device. No account, no internet.",
            )
        )

        SettingsCard(
            listOf(
                "Home screen widget" to "Long-press your home screen, choose Widgets, then SavingLuy. " +
                    "It shows Ready to Assign and your top categories. Tap it to add a transaction.",
                "Move money" to "On the Budget tab, long-press a category or tap “Move money”.",
            )
        )
    }
}

@Composable
private fun SettingsCard(rows: List<Pair<String, String>>) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            rows.forEachIndexed { index, (title, detail) ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )
                }
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(title, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
