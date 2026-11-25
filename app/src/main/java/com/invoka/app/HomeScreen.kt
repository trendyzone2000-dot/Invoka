package com.invoka.app

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(vm: AppViewModel) {
    val titles = listOf(tr("الفواتير"), tr("العملاء"), tr("الأصناف"), tr("الإعدادات"))
    val icons = listOf("🧾", "👥", "📦", "⚙️")
    Scaffold(
        topBar = { AppBar(titles[vm.tab]) },
        bottomBar = {
            NavigationBar {
                icons.forEachIndexed { i, e ->
                    NavigationBarItem(
                        selected = vm.tab == i, onClick = { vm.tab = i },
                        icon = { Text(e) }, label = { Text(titles[i]) }
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).consumeWindowInsets(pad).fillMaxSize()) {
            when (vm.tab) {
                0 -> InvoicesTab(vm)
                1 -> CustomersTab(vm)
                2 -> ItemsTab(vm)
                else -> SettingsTab(vm)
            }
        }
    }
}

@Composable
private fun SearchBar(q: String, onChange: (String) -> Unit) {
    Field(
        tr("بحث"), q, onChange,
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp)
    )
}

@Composable
private fun EmptyText(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, color = GrayOff, style = MaterialTheme.typography.bodyLarge)
    }
}

// ---------------------------------------------------------------- invoices

@Composable
private fun InvoicesTab(vm: AppViewModel) {
    val invoices by vm.invoices.collectAsStateWithLifecycle()
    var q by remember { mutableStateOf("") }
    var filter by remember { mutableIntStateOf(0) }
    val lang = LocalLang.current
    val chips = listOf(tr("الكل"), tr("غير مدفوعة"), tr("مدفوعة جزئيًا"), tr("مدفوعة"), tr("ملغاة"))
    val shown = invoices.filter { iwl ->
        val inv = iwl.invoice
        val st = InvoiceCalculator.status(iwl.totals())
        val okFilter = when (filter) {
            0 -> true
            1 -> !inv.cancelled && st == PayStatus.UNPAID
            2 -> !inv.cancelled && st == PayStatus.PARTIAL
            3 -> !inv.cancelled && st == PayStatus.PAID
            else -> inv.cancelled
        }
        val qq = q.trim()
        okFilter && (qq.isEmpty() || inv.number.toString().contains(qq) ||
            inv.customerName.contains(qq, ignoreCase = true) || inv.customerPhone.contains(qq))
    }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            SearchBar(q) { q = it }
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                chips.forEachIndexed { i, c ->
                    FilterChip(selected = filter == i, onClick = { filter = i }, label = { Text(c) })
                }
            }
            if (shown.isEmpty()) {
                EmptyText(if (invoices.isEmpty()) tr("لا توجد فواتير بعد. اضغط + لإنشاء أول فاتورة") else tr("لا توجد نتائج"))
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(shown, key = { it.invoice.id }) { iwl -> InvoiceRow(iwl, lang) { vm.push(Screen.Detail(iwl.invoice.id)) } }
                }
            }
        }
        FloatingActionButton(
            onClick = { vm.push(Screen.Editor(null)) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) { Icon(Icons.Default.Add, contentDescription = null) }
    }
}

@Composable
private fun InvoiceRow(iwl: InvoiceWithLines, lang: String, onClick: () -> Unit) {
    val inv = iwl.invoice
    val t = iwl.totals()
    val st = InvoiceCalculator.status(t)
    val dim = inv.cancelled
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "#" + inv.number, style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (dim) TextDecoration.LineThrough else null,
                    color = if (dim) GrayOff else MaterialTheme.colorScheme.onSurface
                )
                if (inv.customerName.isNotBlank()) Text(inv.customerName, style = MaterialTheme.typography.bodyMedium)
                Text(fmtDate(inv.dateMillis), style = MaterialTheme.typography.bodySmall, color = GrayOff)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    fmt(t.net) + " " + (if (lang == "ar") "ج.م" else "EGP"),
                    style = MaterialTheme.typography.titleSmall,
                    textDecoration = if (dim) TextDecoration.LineThrough else null
                )
                Spacer(Modifier.height(4.dp))
                if (dim) Badge(trL(lang, "ملغاة"), GrayOff) else Badge(payStatusLabel(lang, st), statusColor(st))
            }
        }
    }
}

// ---------------------------------------------------------------- customers

@Composable
private fun CustomersTab(vm: AppViewModel) {
    val customers by vm.customers.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val lang = LocalLang.current
    var q by remember { mutableStateOf("") }
    var archived by remember { mutableStateOf(false) }
    var form by remember { mutableStateOf<Customer?>(null) }
    val shown = customers.filter {
        it.archived == archived && (q.isBlank() || it.name.contains(q, true) || it.phone.contains(q))
    }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            SearchBar(q) { q = it }
            Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                FilterChip(selected = archived, onClick = { archived = !archived }, label = { Text(tr("عرض المؤرشف")) })
            }
            if (shown.isEmpty()) {
                EmptyText(tr("لا يوجد عملاء"))
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(shown, key = { it.id }) { c ->
                        val text = listOf(c.name, c.phone, c.address).filter { it.isNotBlank() }.joinToString("\n")
                        Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                                    Text(c.name, style = MaterialTheme.typography.titleMedium)
                                    if (c.phone.isNotBlank()) Text(c.phone, style = MaterialTheme.typography.bodyMedium)
                                    if (c.address.isNotBlank()) Text(c.address, style = MaterialTheme.typography.bodySmall, color = GrayOff)
                                }
                                RowMenu(
                                    listOf(
                                        trL(lang, "تعديل") to { form = c },
                                        trL(lang, "نسخ") to {
                                            copyText(ctx, text)
                                            Toast.makeText(ctx, trL(lang, "تم النسخ"), Toast.LENGTH_SHORT).show()
                                        },
                                        trL(lang, "مشاركة") to { shareText(ctx, text, trL(lang, "مشاركة")) },
                                        (if (c.archived) trL(lang, "استرجاع") else trL(lang, "أرشفة")) to {
                                            vm.saveCustomer(c.copy(archived = !c.archived))
                                        }
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { form = Customer(name = "") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) { Icon(Icons.Default.Add, contentDescription = null) }
    }
    form?.let { c ->
        var name by remember(c) { mutableStateOf(c.name) }
        var phone by remember(c) { mutableStateOf(c.phone) }
        var address by remember(c) { mutableStateOf(c.address) }
        AlertDialog(
            onDismissRequest = { form = null },
            title = { Text(if (c.id == 0L) tr("عميل جديد") else tr("تعديل عميل")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(tr("اسم العميل"), name, { name = it })
                    Field(tr("التليفون"), phone, { phone = it })
                    Field(tr("العنوان"), address, { address = it })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) {
                        vm.saveCustomer(c.copy(name = name.trim(), phone = phone.trim(), address = address.trim()))
                        form = null
                    }
                }) { Text(tr("حفظ")) }
            },
            dismissButton = { TextButton(onClick = { form = null }) { Text(tr("إلغاء")) } }
        )
    }
}

// ---------------------------------------------------------------- items

@Composable
private fun ItemsTab(vm: AppViewModel) {
    val catalog by vm.items.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val lang = LocalLang.current
    var q by remember { mutableStateOf("") }
    var archived by remember { mutableStateOf(false) }
    var form by remember { mutableStateOf<CatalogItem?>(null) }
    val shown = catalog.filter { it.archived == archived && (q.isBlank() || it.name.contains(q, true)) }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            SearchBar(q) { q = it }
            Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                FilterChip(selected = archived, onClick = { archived = !archived }, label = { Text(tr("عرض المؤرشف")) })
            }
            if (shown.isEmpty()) {
                EmptyText(tr("لا توجد أصناف"))
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(shown, key = { it.id }) { it2 ->
                        val text = it2.name + (if (it2.price > 0) " - " + fmt(it2.price) else "")
                        Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                                    Text(it2.name, style = MaterialTheme.typography.titleMedium)
                                    if (it2.price > 0) Text(fmt(it2.price), style = MaterialTheme.typography.bodyMedium, color = GrayOff)
                                }
                                RowMenu(
                                    listOf(
                                        trL(lang, "تعديل") to { form = it2 },
                                        trL(lang, "نسخ") to {
                                            copyText(ctx, text)
                                            Toast.makeText(ctx, trL(lang, "تم النسخ"), Toast.LENGTH_SHORT).show()
                                        },
                                        trL(lang, "مشاركة") to { shareText(ctx, text, trL(lang, "مشاركة")) },
                                        (if (it2.archived) trL(lang, "استرجاع") else trL(lang, "أرشفة")) to {
                                            vm.saveItem(it2.copy(archived = !it2.archived))
                                        }
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { form = CatalogItem(name = "") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) { Icon(Icons.Default.Add, contentDescription = null) }
    }
    form?.let { c ->
        var name by remember(c) { mutableStateOf(c.name) }
        var price by remember(c) { mutableStateOf(fmtInput(c.price)) }
        AlertDialog(
            onDismissRequest = { form = null },
            title = { Text(if (c.id == 0L) tr("صنف جديد") else tr("تعديل صنف")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(tr("اسم الصنف"), name, { name = it })
                    Field(tr("السعر"), price, { price = it }, number = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) {
                        vm.saveItem(c.copy(name = name.trim(), price = parseNum(price)))
                        form = null
                    }
                }) { Text(tr("حفظ")) }
            },
            dismissButton = { TextButton(onClick = { form = null }) { Text(tr("إلغاء")) } }
        )
    }
}

// ---------------------------------------------------------------- settings

@Composable
private fun SettingsTab(vm: AppViewModel) {
    val ctx = LocalContext.current
    val lang = LocalLang.current
    val scope = rememberCoroutineScope()
    val biz by vm.business.collectAsStateWithLifecycle()
    var pendingRestore by remember { mutableStateOf<String?>(null) }

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val ok = writeUriText(ctx, uri, vm.exportBackup())
            Toast.makeText(ctx, trL(lang, if (ok) "تم حفظ النسخة الاحتياطية" else "تعذر حفظ النسخة"), Toast.LENGTH_LONG).show()
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = readUriText(ctx, uri)
            if (text == null) Toast.makeText(ctx, trL(lang, "تعذر قراءة الملف"), Toast.LENGTH_LONG).show()
            else pendingRestore = text
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionCard(tr("بيانات النشاط")) {
            val summary = listOf(biz.name, biz.phone, biz.address).filter { it.isNotBlank() }.joinToString(" • ")
            if (summary.isNotBlank()) Text(summary, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = { vm.push(Screen.Business) }) { Text(tr("تعديل بيانات النشاط")) }
        }
        SectionCard(tr("اللغة")) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = vm.lang == "ar", onClick = { vm.setLang("ar") }, label = { Text("العربية") })
                FilterChip(selected = vm.lang == "en", onClick = { vm.setLang("en") }, label = { Text("English") })
            }
        }
        SectionCard(tr("مقاس الورق الافتراضي")) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("A4" to "A4", "A5" to "A5", "T80" to "80 mm", "T58" to "58 mm").forEach { (code, label) ->
                    FilterChip(
                        selected = biz.defaultPaper == code,
                        onClick = { vm.saveBusiness(biz.copy(defaultPaper = code)) },
                        label = { Text(label) }
                    )
                }
            }
        }
        SectionCard(tr("نسخ احتياطي")) {
            val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
            OutlinedButton(onClick = { backupLauncher.launch("invoka_backup_$stamp.json") }) { Text(tr("إنشاء نسخة احتياطية")) }
            OutlinedButton(onClick = { restoreLauncher.launch(arrayOf("*/*")) }) { Text(tr("استرجاع نسخة احتياطية")) }
        }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painterResource(R.drawable.logo_full), contentDescription = null,
                modifier = Modifier.width(200.dp), contentScale = ContentScale.FillWidth
            )
            Text("v1.0", style = MaterialTheme.typography.bodySmall, color = GrayOff)
        }
    }

    pendingRestore?.let { json ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text(tr("استرجاع نسخة احتياطية")) },
            text = { Text(tr("هتستبدل كل البيانات الحالية بالنسخة دي. متأكد؟")) },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestore = null
                    scope.launch {
                        val ok = try { vm.restoreBackup(json); true } catch (e: Exception) { false }
                        Toast.makeText(ctx, trL(lang, if (ok) "تم الاسترجاع" else "الملف غير صالح"), Toast.LENGTH_LONG).show()
                    }
                }) { Text(tr("استرجاع")) }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text(tr("إلغاء")) } }
        )
    }
}
