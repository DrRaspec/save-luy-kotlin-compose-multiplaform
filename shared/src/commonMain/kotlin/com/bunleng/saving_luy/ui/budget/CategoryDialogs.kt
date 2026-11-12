package com.bunleng.saving_luy.ui.budget

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.bunleng.saving_luy.domain.CategoryRules
import com.bunleng.saving_luy.domain.NameError

/** Add or rename a category or group. */
@Composable
fun NameDialog(
    state: NameDialogState,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Local state keeps typing in sync even when the view model's flow is a frame behind.
    var name by remember { mutableStateOf(state.name) }
    val focusRequester = remember { FocusRequester() }
    val noun = if (state.isCategory) "category" else "group"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (state.isNew) "Add $noun" else "Rename $noun") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    onNameChange(it)
                },
                label = { Text("Name") },
                isError = state.error != null,
                supportingText = state.error?.let { error -> { Text(nameErrorMessage(error, noun)) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(if (state.isNew) "Add" else "Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

/**
 * Confirm deleting a category. If it has transactions or Assigned money, the user picks
 * another category to receive them, so no money disappears from the budget.
 */
@Composable
fun DeleteCategoryDialog(
    state: DeleteCategoryState,
    otherCategories: List<CategoryRowUiState>,
    onSelectMoveTo: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val name = state.category.name
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete $name?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when {
                    !state.hasHistory -> Text("$name has no transactions or assigned money.")
                    otherCategories.isEmpty() ->
                        Text("$name has transactions or assigned money. Add another category first so they have somewhere to go.")
                    else -> {
                        Text("$name has transactions or assigned money. Move them to:")
                        MoveToPicker(state.moveToCategoryId, otherCategories, onSelectMoveTo)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = state.canDelete) {
                Text("Delete", color = if (state.canDelete) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Confirm deleting a group. Groups with categories cannot be deleted. */
@Composable
fun DeleteGroupDialog(
    state: DeleteGroupState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val name = state.group.name
    if (state.isEmpty) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Delete $name?") },
            text = { Text("This group has no categories.") },
            confirmButton = {
                TextButton(onClick = onConfirm) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Can't delete $name yet") },
            text = { Text("Delete its categories first. Long-press a category to delete it.") },
            confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        )
    }
}

@Composable
private fun MoveToPicker(
    selectedId: String?,
    categories: List<CategoryRowUiState>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = categories.firstOrNull { it.category.id == selectedId }
    Box {
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clickable { expanded = true }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = selected?.category?.name ?: "Choose category  ›",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                selected?.let { AvailablePill(available = it.entry.available) }
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (row in categories) {
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(row.category.name, modifier = Modifier.weight(1f, fill = false))
                            Spacer(Modifier.width(16.dp))
                            AvailablePill(available = row.entry.available)
                        }
                    },
                    onClick = {
                        onSelect(row.category.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun nameErrorMessage(error: NameError, noun: String): String = when (error) {
    NameError.Blank -> "Enter a name"
    NameError.TooLong -> "Use ${CategoryRules.MAX_NAME_LENGTH} characters or fewer"
    NameError.Duplicate -> "A $noun with this name already exists"
}
