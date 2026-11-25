package com.invoka.app

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun DetailScreen(vm: AppViewModel, id: Long) {
    val ctx = LocalContext.current
    val lang = LocalLang.current
    val biz by vm.business.collectAsStateWithLifecycle()
    val invoices by vm.invoices.collectAsStateWithLifecycle()
    val iwl = invoices.firstOrNull { it.invoice.id == id }

    var paperCode by remember { mutableStateOf(biz.defaultPaper) }
    var gen by remember { mutableStateOf<Generated?>(null) }
    var busy by remember { mutableStateOf(true) }
    var showCustom by remember { mutableStateOf(false) }
    var confirmCancel by remember { mutableStateOf(false) }

    val paper = resolvePaper(paperCode, vm.customW, vm.customH)

    LaunchedEffect(iwl, biz, paperCode, vm.customW, vm.customH, lang, vm.logoVersion) {
        if (iwl != null) {
            busy = true
            gen = try { PdfService.generate(ctx, iwl, biz, paper, lang) } catch (e: Exception) { null }
            busy = false
        }
    }

    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val g = gen
        if (uri != null && g != null) {
            val ok = copyFileToUri(ctx, g.file, uri)
            Toast.makeText(ctx, trL(lang, if (ok) "تم حفظ الملف" else "تعذر حفظ الملف"), Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        topBar = {
            AppBar(
                title = tr("فاتورة") + (if (iwl != null) " #" + iwl.invoice.number else ""),
                onBack = { vm.pop() },
                actions = {
                    if (iwl != null) {
                        val entries = mutableListOf<Pair<String, () -> Unit>>()
                        if (!iwl.invoice.cancelled) {
                            entries += trL(lang, "تعديل") to { vm.push(Screen.Editor(id)) }
                            entries += trL(lang, "إلغاء الفاتورة") to { confirmCancel = true }
                        } else {
                            entries += trL(lang, "استرجاع الفاتورة") to { vm.setCancelled(id, false) }
                        }
                        RowMenuWhite(entries)
                    }
                }
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val g = gen
                    OutlinedButton(
                        onClick = { if (g != null) printPdf(ctx, g, "Invoka-" + (iwl?.invoice?.number ?: 0)) },
                        enabled = g != null, modifier = Modifier.weight(1f)
                    ) { Text(tr("طباعة")) }
                    OutlinedButton(
                        onClick = { saver.launch("invoice_" + (iwl?.invoice?.number ?: 0) + ".pdf") },
                        enabled = g != null, modifier = Modifier.weight(1f)
                    ) { Text(tr("حفظ")) }
                    Button(
                        onClick = { if (g != null) sharePdf(ctx, g.file, trL(lang, "مشاركة")) },
                        enabled = g != null, modifier = Modifier.weight(1f)
                    ) { Text(tr("مشاركة")) }
                }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).consumeWindowInsets(pad).fillMaxSize()) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("A4" to "A4", "A5" to "A5", "T80" to "80 mm", "T58" to "58 mm", "CUSTOM" to tr("مخصص")).forEach { (code, label) ->
                    FilterChip(
                        selected = paperCode == code,
                        onClick = { if (code == "CUSTOM") showCustom = true else paperCode = code },
                        label = { Text(label) }
                    )
                }
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                val g = gen
                if (g == null) {
                    if (busy) CircularProgressIndicator(Modifier.padding(32.dp))
                    else Text(tr("تعذر إنشاء الفاتورة"), color = RedBad, modifier = Modifier.padding(32.dp))
                } else {
                    Column(
                        Modifier.verticalScroll(rememberScrollState()).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        g.previews.forEach { bmp -> PagePreview(bmp, narrow = paper.wPt < 300) }
                    }
                }
            }
        }
    }

    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text(tr("إلغاء الفاتورة")) },
            text = { Text(tr("الفاتورة هتفضل في السجل بعلامة ملغاة ورقمها محفوظ. متأكد؟")) },
            confirmButton = {
                TextButton(onClick = { vm.setCancelled(id, true); confirmCancel = false }) { Text(tr("إلغاء الفاتورة")) }
            },
            dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text(tr("رجوع")) } }
        )
    }

    if (showCustom) {
        var w by remember { mutableStateOf(fmtInput(vm.customW.toDouble())) }
        var h by remember { mutableStateOf(fmtInput(vm.customH.toDouble())) }
        AlertDialog(
            onDismissRequest = { showCustom = false },
            title = { Text(tr("مقاس مخصص")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(tr("العرض (مم)"), w, { w = it }, number = true)
                    Field(tr("الطول (مم) - فاضي = طول تلقائي"), h, { h = it }, number = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val ww = parseNum(w).toFloat()
                    if (ww >= 40f) {
                        vm.setCustomPaper(ww, parseNum(h).toFloat())
                        paperCode = "CUSTOM"
                        showCustom = false
                    }
                }) { Text(tr("تمام")) }
            },
            dismissButton = { TextButton(onClick = { showCustom = false }) { Text(tr("إلغاء")) } }
        )
    }
}

@Composable
private fun RowMenuWhite(entries: List<Pair<String, () -> Unit>>) {
    RowMenu(entries)
}

@Composable
private fun PagePreview(bmp: Bitmap, narrow: Boolean) {
    Box(
        Modifier.then(if (narrow) Modifier.widthIn(max = 340.dp) else Modifier.fillMaxWidth())
            .background(Color.White)
    ) {
        Image(
            bmp.asImageBitmap(), contentDescription = null,
            modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth
        )
    }
}
