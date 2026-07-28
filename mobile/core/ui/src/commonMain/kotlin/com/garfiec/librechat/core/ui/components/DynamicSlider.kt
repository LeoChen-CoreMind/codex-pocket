package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Labeled slider that snaps to step increments, with stepCount derived from (max - min) / step. */
@Composable
fun DynamicSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    min: Float = 0f,
    max: Float = 1f,
    step: Float = 0.1f,
    description: String? = null,
    displayDecimals: Int = 2,
) {
    val stepCount = ((max - min) / step).toInt().coerceAtLeast(1) - 1
    val displayValue = roundToDecimals(value, displayDecimals)

    val sliderCd = "$label slider, value $displayValue"
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = sliderCd },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = displayValue,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = value,
            onValueChange = { newValue ->
                onValueChange((newValue / step).roundToInt() * step)
            },
            valueRange = min..max,
            steps = stepCount,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview
@Composable
private fun DynamicSliderPreview() {
    var value by remember { mutableFloatStateOf(0.7f) }
    DynamicSlider(
        label = "Temperature",
        value = value,
        onValueChange = { value = it },
        min = 0f,
        max = 2f,
        step = 0.1f,
        description = "Controls randomness of the output.",
    )
}

private fun roundToDecimals(value: Float, decimals: Int): String {
    val factor = 10f.pow(decimals)
    val rounded = (value * factor).roundToLong() / factor
    val str = rounded.toString()
    val dot = str.indexOf('.')
    if (dot < 0) return str + "." + "0".repeat(decimals)
    val existing = str.length - dot - 1
    return if (existing >= decimals) {
        str.substring(0, dot + decimals + 1)
    } else {
        str + "0".repeat(decimals - existing)
    }
}
