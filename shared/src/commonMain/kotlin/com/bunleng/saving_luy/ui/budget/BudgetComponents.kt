package com.bunleng.saving_luy.ui.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bunleng.saving_luy.domain.BudgetHint
import com.bunleng.saving_luy.domain.BudgetMonth
import com.bunleng.saving_luy.ui.MoneyFormatter
import com.bunleng.saving_luy.ui.theme.LocalBudgetStatusColors

// ──────────────────────────────────────────────────────────────────────
// Month Selector
// ──────────────────────────────────────────────────────────────────────

@Composable
fun MonthSelector(
    month: BudgetMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Text(
                text = "◀",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = month.displayName(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        IconButton(onClick = onNext) {
            Text(
                text = "▶",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

// ──────────────────────────────────────────────────────────────────────
// Ready to Assign Banner
// ──────────────────────────────────────────────────────────────────────

@Composable
fun ReadyToAssignBanner(
    amount: Long,
    modifier: Modifier = Modifier,
) {
    val statusColors = LocalBudgetStatusColors.current
    val colors = when {
        amount < 0 -> statusColors.negative  // over-assigned
        amount > 0 -> statusColors.warning   // money still unassigned
        else       -> statusColors.positive  // perfectly zero
    }
    val label = when {
        amount < 0 -> "Over-assigned"
        amount > 0 -> "Ready to Assign"
        else       -> "All Money Assigned"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.container)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = MoneyFormatter.format(amount),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = colors.content,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.content,
        )
    }
}

// ──────────────────────────────────────────────────────────────────────
// Summary under the banner: what to do next, and cash in budget accounts
// ──────────────────────────────────────────────────────────────────────

@Composable
fun BudgetSummary(
    hint: BudgetHint?,
    onBudgetBalance: Long,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        hint?.let {
            Text(
                text = hintText(it),
                style = MaterialTheme.typography.bodyMedium,
                color = if (it is BudgetHint.Overspent || it is BudgetHint.OverAssigned) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
        Text(
            text = "In budget accounts: ${MoneyFormatter.format(onBudgetBalance)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun hintText(hint: BudgetHint): String = when (hint) {
    is BudgetHint.Overspent ->
        if (hint.count == 1) {
            "⚠ ${hint.firstName} is overspent. Long-press it to cover it with Move Money."
        } else {
            "⚠ ${hint.count} categories are overspent. Long-press one to cover it with Move Money."
        }
    BudgetHint.Unassigned -> "Tap a category to give this money a job."
    BudgetHint.OverAssigned -> "⚠ You assigned more than you have. Lower a category's Assigned."
}

// ──────────────────────────────────────────────────────────────────────
// Category Group Header (collapsible)
// ──────────────────────────────────────────────────────────────────────

/** Tap toggles the group; the ⋮ menu adds a category, renames, or deletes the group. */
@Composable
fun CategoryGroupHeader(
    name: String,
    isExpanded: Boolean,
    onClick: () -> Unit,
    onAddCategory: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (isExpanded) "▼" else "▶",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Text(
                    text = "⋮",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { contentDescription = "$name options" },
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                MenuItem("Add category") { menuOpen = false; onAddCategory() }
                MenuItem("Rename group") { menuOpen = false; onRename() }
                MenuItem("Delete group", isDestructive = true) { menuOpen = false; onDelete() }
            }
        }
    }
}

@Composable
private fun MenuItem(text: String, isDestructive: Boolean = false, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Text(text, color = if (isDestructive) MaterialTheme.colorScheme.error else Color.Unspecified)
        },
        onClick = onClick,
    )
}

/** A quiet "+ label" row, used for Add category and Add group. */
@Composable
fun AddRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = "+ $label",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    )
}

// ──────────────────────────────────────────────────────────────────────
// Category Row
// ──────────────────────────────────────────────────────────────────────

/** Tap edits Assigned; long-press opens a menu with Move money, Rename, and Delete. */
@Composable
fun CategoryRow(
    name: String,
    assigned: Long,
    available: Long,
    onClick: () -> Unit,
    onMoveMoney: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        CategoryRowContent(
            name = name,
            assigned = assigned,
            available = available,
            modifier = Modifier.combinedClickable(
                onClick = onClick,
                onLongClick = { menuOpen = true },
                onLongClickLabel = "Category options",
            ),
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            MenuItem("Move money") { menuOpen = false; onMoveMoney() }
            MenuItem("Rename") { menuOpen = false; onRename() }
            MenuItem("Delete", isDestructive = true) { menuOpen = false; onDelete() }
        }
    }
}

@Composable
private fun CategoryRowContent(
    name: String,
    assigned: Long,
    available: Long,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Category name
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )

        // Assigned amount
        Text(
            text = MoneyFormatter.format(assigned),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.width(90.dp),
        )

        Spacer(Modifier.width(12.dp))

        // Available pill
        AvailablePill(available = available)
    }
}

// ──────────────────────────────────────────────────────────────────────
// Available Pill
// ──────────────────────────────────────────────────────────────────────

@Composable
fun AvailablePill(
    available: Long,
    modifier: Modifier = Modifier,
) {
    val statusColors = LocalBudgetStatusColors.current
    val colors = when {
        available < 0 -> statusColors.negative
        available > 0 -> statusColors.positive
        else -> statusColors.neutral
    }
    // Warning icon for overspent so color is not the only signal
    val icon = if (available < 0) "⚠ " else ""

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(colors.container)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "$icon${MoneyFormatter.format(available)}",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = colors.content,
        )
    }
}
