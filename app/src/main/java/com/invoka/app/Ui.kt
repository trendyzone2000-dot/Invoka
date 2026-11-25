package com.invoka.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

val Indigo = Color(0xFF2D3A9E)
val Orange = Color(0xFFF59E0B)
val GreenOk = Color(0xFF1B8A4B)
val AmberWarn = Color(0xFFD97706)
val RedBad = Color(0xFFC0392B)
val GrayOff = Color(0xFF7A7F99)

@Composable
fun InvokaTheme(content: @Composable () -> Unit) {
    val scheme = lightColorScheme(
        primary = Indigo, onPrimary = Color.White,
        secondary = Orange, onSecondary = Color.White,
        background = Color(0xFFF6F7FB), surface = Color.White,
        surfaceVariant = Color(0xFFE9ECF8), onSurface = Color(0xFF1B1F3B),
        primaryContainer = Color(0xFFDDE3FF), onPrimaryContainer = Indigo,
        error = RedBad
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

fun statusColor(s: PayStatus): Color = when (s) {
    PayStatus.PAID -> GreenOk
    PayStatus.PARTIAL -> AmberWarn
    PayStatus.UNPAID -> RedBad
}

@Composable
fun AppBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Surface(color = MaterialTheme.colorScheme.primary, contentColor = Color.White, shadowElevation = 4.dp) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().height(56.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
            } else {
                Spacer(Modifier.width(12.dp))
            }
            Text(
                title, style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            actions()
        }
    }
}

@Composable
fun Field(
    label: String, value: String, onChange: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(), number: Boolean = false, lines: Int = 1
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, modifier = modifier,
        singleLine = lines == 1, minLines = lines,
        keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Decimal else KeyboardType.Text)
    )
}

@Composable
fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Indigo)
            content()
        }
    }
}

@Composable
fun Badge(text: String, color: Color) {
    Text(
        text, color = color, style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.background(color.copy(alpha = 0.12f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

@Composable
fun RowMenu(entries: List<Pair<String, () -> Unit>>) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Default.MoreVert, contentDescription = null) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            entries.forEach { (label, action) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { open = false; action() })
            }
        }
    }
}

@Composable
fun <T> PickerDialog(
    title: String, all: List<T>, text: (T) -> String, sub: (T) -> String,
    onPick: (T) -> Unit, onDismiss: () -> Unit
) {
    var q by remember { mutableStateOf("") }
    val shown = all.filter { q.isBlank() || text(it).contains(q, ignoreCase = true) || sub(it).contains(q, ignoreCase = true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Field(tr("بحث"), q, { q = it })
                Spacer(Modifier.height(8.dp))
                if (shown.isEmpty()) {
                    Text(tr("لا توجد نتائج"), color = GrayOff)
                } else {
                    LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(shown) { x ->
                            Column(Modifier.fillMaxWidth().clickable { onPick(x); onDismiss() }.padding(vertical = 8.dp)) {
                                Text(text(x), style = MaterialTheme.typography.bodyLarge)
                                val s = sub(x)
                                if (s.isNotBlank()) Text(s, style = MaterialTheme.typography.bodySmall, color = GrayOff)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("إغلاق")) } }
    )
}
