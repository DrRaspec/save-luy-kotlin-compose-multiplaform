package com.bunleng.saving_luy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Calculator-style keypad: digits, clear (C) and backspace (←).
 */
@Composable
fun NumericKeypad(
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    keySize: Dp = 72.dp,
) {
    val rows = listOf(
        listOf('1', '2', '3'),
        listOf('4', '5', '6'),
        listOf('7', '8', '9'),
        listOf('C', '0', '←'),
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        for (row in rows) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (key in row) {
                    KeypadButton(
                        label = key.toString(),
                        size = keySize,
                        onClick = {
                            when (key) {
                                '←' -> onBackspace()
                                'C' -> onClear()
                                else -> onDigit(key)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun KeypadButton(
    label: String,
    size: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Medium,
        )
    }
}
