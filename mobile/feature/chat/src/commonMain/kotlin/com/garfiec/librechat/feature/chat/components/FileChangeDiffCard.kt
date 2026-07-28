package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

@Composable
internal fun FileChangeDiffCard(
    output: String?,
    isComplete: Boolean,
    modifier: Modifier = Modifier,
) {
    var expanded by remember(output) { mutableStateOf(false) }
    val added = MaterialTheme.colorScheme.primary
    val deleted = MaterialTheme.colorScheme.error
    val section = MaterialTheme.colorScheme.tertiary
    val normal = MaterialTheme.colorScheme.onSurfaceVariant
    val highlighted = remember(output, added, deleted, section, normal) {
        buildAnnotatedString {
            output.orEmpty().lineSequence().filterNot { it == "```diff" || it == "```" }.forEach { line ->
                val color = when {
                    line.startsWith("+") && !line.startsWith("+++") -> added
                    line.startsWith("-") && !line.startsWith("---") -> deleted
                    line.startsWith("@@") || line.startsWith("编辑 ") || line.startsWith("新增 ") || line.startsWith("删除 ") -> section
                    else -> normal
                }
                withStyle(SpanStyle(color = color)) {
                    append(line)
                    append('\n')
                }
            }
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("代码修改", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (isComplete) {
                    Icon(Icons.Default.Check, contentDescription = "完成", modifier = Modifier.size(18.dp), tint = added)
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                }
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "折叠" else "展开",
                    modifier = Modifier.size(20.dp),
                )
            }
            if (expanded && highlighted.isNotEmpty()) {
                Text(
                    text = highlighted,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp).horizontalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    softWrap = false,
                )
            }
        }
    }
}
