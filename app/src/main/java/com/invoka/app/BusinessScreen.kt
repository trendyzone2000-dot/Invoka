package com.invoka.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun BusinessScreen(vm: AppViewModel) {
    val biz by vm.business.collectAsStateWithLifecycle()
    var name by remember(biz.id, biz.name) { mutableStateOf(biz.name) }
    var phone by remember(biz.id, biz.phone) { mutableStateOf(biz.phone) }
    var address by remember(biz.id, biz.address) { mutableStateOf(biz.address) }
    var taxNo by remember(biz.id, biz.taxNumber) { mutableStateOf(biz.taxNumber) }
    var taxRate by remember(biz.id, biz.defaultTaxRate) { mutableStateOf(fmtInput(biz.defaultTaxRate)) }
    var paper by remember(biz.id, biz.defaultPaper) { mutableStateOf(biz.defaultPaper) }
    val logo = remember(vm.logoVersion) { vm.loadLogo() }

    DisposableEffect(Unit) { onDispose { vm.markOnboarded() } }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.setLogo(uri)
    }

    Scaffold(topBar = { AppBar(tr("بيانات النشاط"), onBack = { vm.pop() }) }) { pad ->
        Column(
            Modifier.padding(pad).consumeWindowInsets(pad).imePadding()
                .verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionCard(tr("اللوجو")) {
                if (logo != null) {
                    Image(
                        logo.asImageBitmap(), contentDescription = null,
                        modifier = Modifier.height(90.dp), contentScale = ContentScale.Fit
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { picker.launch("image/*") }) { Text(tr("اختيار صورة")) }
                    if (logo != null) OutlinedButton(onClick = { vm.removeLogo() }) { Text(tr("إزالة")) }
                }
            }
            SectionCard(tr("بيانات النشاط")) {
                Field(tr("اسم النشاط"), name, { name = it })
                Field(tr("التليفون"), phone, { phone = it })
                Field(tr("العنوان"), address, { address = it })
                Field(tr("الرقم الضريبي"), taxNo, { taxNo = it })
                Field(tr("نسبة الضريبة الافتراضية %"), taxRate, { taxRate = it }, number = true)
            }
            SectionCard(tr("مقاس الورق الافتراضي")) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("A4" to "A4", "A5" to "A5", "T80" to "80 mm", "T58" to "58 mm").forEach { (code, label) ->
                        FilterChip(selected = paper == code, onClick = { paper = code }, label = { Text(label) })
                    }
                }
            }
            Button(
                onClick = {
                    vm.saveBusiness(
                        BusinessProfile(
                            name = name.trim(), phone = phone.trim(), address = address.trim(),
                            taxNumber = taxNo.trim(), defaultTaxRate = parseNum(taxRate), defaultPaper = paper
                        )
                    )
                    vm.pop()
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(tr("حفظ")) }
        }
    }
}
