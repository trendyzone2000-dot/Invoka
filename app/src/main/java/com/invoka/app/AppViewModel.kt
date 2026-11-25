package com.invoka.app

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

sealed interface Screen {
    data object Home : Screen
    data class Editor(val invoiceId: Long?) : Screen
    data class Detail(val invoiceId: Long) : Screen
    data object Business : Screen
}

class LineDraft(val name: String, val qty: Double, val price: Double, val discount: Double)

class InvoiceDraft(
    val id: Long?,
    val dateMillis: Long,
    val customerId: Long?,
    val customerName: String,
    val customerPhone: String,
    val customerAddress: String,
    val lines: List<LineDraft>,
    val delivery: Double,
    val otherName: String,
    val otherAmount: Double,
    val discount: Double,
    val taxRate: Double,
    val paid: Double,
    val notes: String
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx: Context = app.applicationContext
    private val db = AppDatabase.get(ctx)
    private val dao = db.dao()
    private val prefs = ctx.getSharedPreferences("invoka", Context.MODE_PRIVATE)

    var lang by mutableStateOf(prefs.getString("lang", "ar") ?: "ar")
        private set
    var tab by mutableIntStateOf(0)
    var logoVersion by mutableIntStateOf(0)
        private set
    var customW by mutableStateOf(prefs.getFloat("cw", 100f))
        private set
    var customH by mutableStateOf(prefs.getFloat("ch", 0f))
        private set
    val stack = mutableStateListOf<Screen>(Screen.Home)

    val business: StateFlow<BusinessProfile> = dao.businessFlow()
        .map { it ?: BusinessProfile() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, BusinessProfile())
    val customers: StateFlow<List<Customer>> = dao.customersFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val items: StateFlow<List<CatalogItem>> = dao.itemsFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val invoices: StateFlow<List<InvoiceWithLines>> = dao.invoicesFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val onboarded: Boolean get() = prefs.getBoolean("onboarded", false)
    fun markOnboarded() { prefs.edit().putBoolean("onboarded", true).apply() }

    // navigation
    fun push(s: Screen) { stack.add(s) }
    fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    fun replaceTop(s: Screen) {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
        stack.add(s)
    }

    fun setLang(l: String) {
        lang = l
        prefs.edit().putString("lang", l).apply()
    }

    fun setCustomPaper(w: Float, h: Float) {
        customW = w
        customH = h
        prefs.edit().putFloat("cw", w).putFloat("ch", h).apply()
    }

    // business
    fun saveBusiness(b: BusinessProfile) { viewModelScope.launch { dao.saveBusiness(b.copy(id = 1)) } }

    private fun logoFile() = File(ctx.filesDir, "logo.png")

    fun loadLogo(): Bitmap? = logoFile().takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }

    fun setLogo(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (bounds.outWidth / sample > 1200 || bounds.outHeight / sample > 1200) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                    ?: return@launch
                val maxSide = 600f
                val sc = minOf(1f, maxSide / maxOf(bmp.width, bmp.height))
                val out = if (sc < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * sc).toInt(), (bmp.height * sc).toInt(), true) else bmp
                FileOutputStream(logoFile()).use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
                withContext(Dispatchers.Main) { logoVersion++ }
            } catch (e: Exception) {
            }
        }
    }

    fun removeLogo() {
        logoFile().delete()
        logoVersion++
    }

    // customers / items (archive instead of delete)
    fun saveCustomer(c: Customer) { viewModelScope.launch { dao.saveCustomer(c) } }
    fun saveItem(i: CatalogItem) { viewModelScope.launch { dao.saveItem(i) } }

    // invoices
    suspend fun loadInvoice(id: Long): InvoiceWithLines? = dao.invoice(id)

    suspend fun saveInvoice(d: InvoiceDraft, saveCustomerToo: Boolean): Long = db.withTransaction {
        var cid = d.customerId
        if (cid == null && saveCustomerToo && d.customerName.isNotBlank()) {
            cid = dao.saveCustomer(
                Customer(name = d.customerName.trim(), phone = d.customerPhone.trim(), address = d.customerAddress.trim())
            )
        }
        val id: Long
        if (d.id == null) {
            val number = dao.nextNumber()
            id = dao.insertInvoice(
                Invoice(
                    number = number, dateMillis = d.dateMillis, customerId = cid,
                    customerName = d.customerName.trim(), customerPhone = d.customerPhone.trim(),
                    customerAddress = d.customerAddress.trim(), delivery = d.delivery,
                    otherName = d.otherName.trim(), otherAmount = d.otherAmount, discount = d.discount,
                    taxRate = d.taxRate, paid = d.paid, notes = d.notes.trim()
                )
            )
        } else {
            id = d.id
            val old = dao.invoice(id)!!.invoice
            dao.updateInvoice(
                old.copy(
                    dateMillis = d.dateMillis, customerId = cid, customerName = d.customerName.trim(),
                    customerPhone = d.customerPhone.trim(), customerAddress = d.customerAddress.trim(),
                    delivery = d.delivery, otherName = d.otherName.trim(), otherAmount = d.otherAmount,
                    discount = d.discount, taxRate = d.taxRate, paid = d.paid, notes = d.notes.trim()
                )
            )
            dao.deleteLines(id)
        }
        dao.insertLines(
            d.lines.mapIndexed { idx, l ->
                InvoiceLine(invoiceId = id, position = idx, name = l.name.trim(), qty = l.qty, price = l.price, discount = l.discount)
            }
        )
        id
    }

    fun setCancelled(id: Long, c: Boolean) { viewModelScope.launch { dao.setCancelled(id, c) } }

    // backup
    suspend fun exportBackup(): String = Backup.export(ctx, dao)

    suspend fun restoreBackup(json: String) {
        Backup.restore(ctx, db, json)
        logoVersion++
    }
}
