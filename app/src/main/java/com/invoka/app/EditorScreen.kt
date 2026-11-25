@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.invoka.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

private var keyCounter = 0L
private fun nextKey(): Long = ++keyCounter

private data class LineUi(
    val key: Long,
    val name: String = "",
    val qty: String = "1",
    val price: String = "",
    val discount: String = ""
)

@Composable
fun EditorScreen(vm: AppViewModel, invoiceId: Long?) {
    val lang = LocalLang.current
    val scope = rememberCoroutineScope()
    val customers by vm.customers.collectAsStateWithLifecycle()
    val catalog by vm.items.collectAsStateWithLifecycle()

    var customerId by remember { mutableStateOf<Long?>(null) }
    var cName by remember { mutableStateOf("") }
    var cPhone by remember { mutableStateOf("") }
    var cAddress by remember { mutableStateOf("") }
    var saveCust by remember { mutableStateOf(true) }
    val lines = remember { mutableStateListOf(LineUi(nextKey())) }
    var delivery by remember { mutableStateOf("") }
    var otherName by remember { mutableStateOf("") }
    var otherAmt by remember { mutableStateOf("") }
    var discount by remember { mutableStateOf("") }
    var tax by remember { mutableStateOf(fmtInput(vm.business.value.defaultTaxRate)) }
    var paid by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var date by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var error by remember { mutableStateOf<String?>(null) }
    var pickCustomer by remember { mutableStateOf(false) }
    var pickItemFor by remember { mutableStateOf<Int?>(null) }
    var showDate by remember { mutableStateOf(false) }

    LaunchedEffect(invoiceId) {
        if (invoiceId != null) {
            vm.loadInvoice(invoiceId)?.let { iwl ->
                val i = iwl.invoice
                customerId = i.customerId
                cName = i.customerName; cPhone = i.customerPhone; cAddress = i.customerAddress
                delivery = fmtInput(i.delivery); otherName = i.otherName; otherAmt = fmtInput(i.otherAmount)
                discount = fmtInput(i.discount); tax = fmtInput(i.taxRate); paid = fmtInput(i.paid)
                notes = i.notes; date = i.dateMillis
                lines.clear()
                iwl.lines.sortedBy { it.position }.forEach {
                    lines.add(LineUi(nextKey(), it.name, fmtInput(it.qty), fmtInput(it.price), fmtInput(it.discount)))
                }
                if (lines.isEmpty()) lines.add(LineUi(nextKey()))
            }
        }
    }

    val calc = InvoiceCalculator.totals(
        lines.map { LineCalc(qtyOf(it.qty), parseNum(it.price), parseNum(it.discount)) },
        parseNum(discount), parseNum(tax), parseNum(delivery), parseNum(otherAmt), parseNum(paid)
    )
    val cur = if (lang == "ar") "ج.م" else "EGP"

    Scaffold(
        topBar = {
            AppBar(
                if (invoiceId == null) tr("فاتورة جديدة") else tr("تعديل الفاتورة"),
                onBack = { vm.pop() }
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).consumeWindowInsets(pad).imePadding()
                .verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionCard(tr("العميل")) {
                OutlinedButton(onClick = { pickCustomer = true }) { Text(tr("اختيار من العملاء")) }
                Field(tr("اسم العميل"), cName, { cName = it })
                Field(tr("التليفون"), cPhone, { cPhone = it })
                Field(tr("العنوان"), cAddress, { cAddress = it })
                if (customerId == null && cName.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = saveCust, onCheckedChange = { saveCust = it })
                        Text(tr("حفظ في العملاء"))
                    }
                }
            }

            SectionCard(tr("البنود")) {
                lines.forEachIndexed { idx, l ->
                    key(l.key) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF6F7FB)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Field(tr("الصنف"), l.name, { lines[idx] = l.copy(name = it) }, Modifier.weight(1f))
                                    IconButton(onClick = { pickItemFor = idx }) { Text("📦") }
                                    if (lines.size > 1) {
                                        IconButton(onClick = { lines.removeAt(idx) }) {
                                            Icon(Icons.Default.Delete, contentDescription = null)
                                        }
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Field(tr("الكمية"), l.qty, { lines[idx] = l.copy(qty = it) }, Modifier.weight(1f), number = true)
                                    Field(tr("السعر"), l.price, { lines[idx] = l.copy(price = it) }, Modifier.weight(1f), number = true)
                                    Field(tr("خصم"), l.discount, { lines[idx] = l.copy(discount = it) }, Modifier.weight(1f), number = true)
                                }
                                val lt = InvoiceCalculator.lineTotal(LineCalc(qtyOf(l.qty), parseNum(l.price), parseNum(l.discount)))
                                if (lt > 0) Text(tr("إجمالي البند") + ": " + fmt(lt), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                OutlinedButton(onClick = { lines.add(LineUi(nextKey())) }) { Text(tr("إضافة بند")) }
            }

            SectionCard(tr("مصاريف وخصم")) {
                Field(tr("مصاريف توصيل"), delivery, { delivery = it }, number = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(tr("اسم مصاريف أخرى"), otherName, { otherName = it }, Modifier.weight(1f))
                    Field(tr("المبلغ"), otherAmt, { otherAmt = it }, Modifier.weight(1f), number = true)
                }
                Field(tr("خصم إجمالي"), discount, { discount = it }, number = true)
                Field(tr("نسبة الضريبة %"), tax, { tax = it }, number = true)
                Field(tr("المدفوع"), paid, { paid = it }, number = true)
                OutlinedButton(onClick = { showDate = true }) { Text(tr("التاريخ") + ": " + fmtDate(date)) }
                Field(tr("ملاحظات"), notes, { notes = it }, lines = 2)
            }

            SectionCard(tr("الملخص")) {
                @Composable
                fun row(label: String, v: Double, bold: Boolean = false) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(label, style = if (bold) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium)
                        Text(fmt(v) + " " + cur, style = if (bold) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium)
                    }
                }
                if (calc.discount > 0 || calc.tax > 0 || calc.delivery > 0 || calc.other > 0) row(tr("الإجمالي الفرعي"), calc.subtotal)
                if (calc.discount > 0) row(tr("خصم إجمالي"), calc.discount)
                if (calc.tax > 0) row(tr("ضريبة"), calc.tax)
                if (calc.delivery > 0) row(tr("مصاريف توصيل"), calc.delivery)
                if (calc.other > 0) row(otherName.trim().ifEmpty { tr("مصاريف أخرى") }, calc.other)
                row(tr("الصافي"), calc.net, true)
                if (calc.paid > 0) row(tr("المدفوع"), calc.paid)
                if (calc.paid > 0 && calc.remaining > 0) row(tr("المتبقي"), calc.remaining, true)
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Button(
                onClick = {
                    val valid = lines.filter { it.name.isNotBlank() || parseNum(it.price) > 0 }
                    if (valid.isEmpty()) {
                        error = trL(lang, "أضف بند واحد على الأقل")
                    } else {
                        error = null
                        val draft = InvoiceDraft(
                            id = invoiceId, dateMillis = date, customerId = customerId,
                            customerName = cName, customerPhone = cPhone, customerAddress = cAddress,
                            lines = valid.map { LineDraft(it.name, qtyOf(it.qty), parseNum(it.price), parseNum(it.discount)) },
                            delivery = parseNum(delivery), otherName = otherName, otherAmount = parseNum(otherAmt),
                            discount = parseNum(discount), taxRate = parseNum(tax), paid = parseNum(paid), notes = notes
                        )
                        scope.launch {
                            val id = vm.saveInvoice(draft, saveCust)
                            if (invoiceId == null) vm.replaceTop(Screen.Detail(id)) else vm.pop()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().navigationBarsPadding()
            ) { Text(tr("حفظ الفاتورة")) }
        }
    }

    if (pickCustomer) {
        PickerDialog(
            title = tr("اختيار عميل"),
            all = customers.filter { !it.archived },
            text = { it.name }, sub = { it.phone },
            onPick = { c ->
                customerId = c.id; cName = c.name; cPhone = c.phone; cAddress = c.address
            },
            onDismiss = { pickCustomer = false }
        )
    }
    pickItemFor?.let { idx ->
        PickerDialog(
            title = tr("اختيار صنف"),
            all = catalog.filter { !it.archived },
            text = { it.name }, sub = { if (it.price > 0) fmt(it.price) else "" },
            onPick = { c ->
                if (idx in lines.indices) lines[idx] = lines[idx].copy(name = c.name, price = fmtInput(c.price))
            },
            onDismiss = { pickItemFor = null }
        )
    }
    if (showDate) {
        val st = rememberDatePickerState(initialSelectedDateMillis = localToUtcPick(date))
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    st.selectedDateMillis?.let { date = utcPickToLocal(it) }
                    showDate = false
                }) { Text(tr("تمام")) }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text(tr("إلغاء")) } }
        ) { DatePicker(state = st) }
    }
}
