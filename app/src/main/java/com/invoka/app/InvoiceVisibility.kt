package com.invoka.app

data class ViewLine(
    val name: String, val qty: String, val price: String, val discount: String, val total: String
)

data class ViewRow(val label: String, val value: String, val strong: Boolean = false)

/**
 * Everything the PDF needs, already filtered by the "empty or zero never shows" rule.
 * Anything blank here is simply not drawn.
 */
data class InvoiceView(
    val businessName: String,
    val businessLines: List<String>,
    val title: String,
    val numberText: String,
    val dateText: String,
    val customerTitle: String,
    val customerName: String,
    val customerLines: List<String>,
    val headers: List<String>,
    val lines: List<ViewLine>,
    val showDiscountColumn: Boolean,
    val totals: List<ViewRow>,
    val notesLabel: String,
    val notes: String,
    val cancelled: Boolean,
    val cancelledText: String
)

fun payStatusLabel(lang: String, s: PayStatus): String = when (s) {
    PayStatus.PAID -> trL(lang, "مدفوعة")
    PayStatus.PARTIAL -> trL(lang, "مدفوعة جزئيًا")
    PayStatus.UNPAID -> trL(lang, "غير مدفوعة")
}

fun buildView(iwl: InvoiceWithLines, biz: BusinessProfile, lang: String): InvoiceView {
    val inv = iwl.invoice
    val cur = if (lang == "ar") "ج.م" else "EGP"
    fun money(v: Double) = fmt(v) + " " + cur
    val t = iwl.totals()
    val lines = iwl.lines.sortedBy { it.position }

    val rows = mutableListOf<ViewRow>()
    if (t.discount > 0 || t.tax > 0 || t.delivery > 0 || t.other > 0) {
        rows += ViewRow(trL(lang, "الإجمالي الفرعي"), money(t.subtotal))
    }
    if (t.discount > 0) rows += ViewRow(trL(lang, "خصم إجمالي"), money(t.discount))
    if (t.tax > 0) rows += ViewRow(trL(lang, "ضريبة") + " (" + fmt(inv.taxRate) + "%)", money(t.tax))
    if (t.delivery > 0) rows += ViewRow(trL(lang, "مصاريف توصيل"), money(t.delivery))
    if (t.other > 0) {
        rows += ViewRow(inv.otherName.trim().ifEmpty { trL(lang, "مصاريف أخرى") }, money(t.other))
    }
    rows += ViewRow(trL(lang, "الصافي"), money(t.net), strong = true)
    if (t.paid > 0) {
        rows += ViewRow(trL(lang, "المدفوع"), money(t.paid))
        if (t.remaining > 0) rows += ViewRow(trL(lang, "المتبقي"), money(t.remaining), strong = true)
        rows += ViewRow(trL(lang, "حالة الدفع"), payStatusLabel(lang, InvoiceCalculator.status(t)))
    }

    val bizLines = mutableListOf<String>()
    if (biz.phone.isNotBlank()) bizLines += trL(lang, "تليفون") + ": " + biz.phone.trim()
    if (biz.address.isNotBlank()) bizLines += biz.address.trim()
    if (biz.taxNumber.isNotBlank()) bizLines += trL(lang, "الرقم الضريبي") + ": " + biz.taxNumber.trim()

    val custLines = mutableListOf<String>()
    if (inv.customerPhone.isNotBlank()) custLines += trL(lang, "تليفون") + ": " + inv.customerPhone.trim()
    if (inv.customerAddress.isNotBlank()) custLines += inv.customerAddress.trim()

    return InvoiceView(
        businessName = biz.name.trim(),
        businessLines = bizLines,
        title = trL(lang, "فاتورة"),
        numberText = trL(lang, "رقم") + ": " + inv.number,
        dateText = trL(lang, "التاريخ") + ": " + fmtDate(inv.dateMillis),
        customerTitle = trL(lang, "العميل"),
        customerName = inv.customerName.trim(),
        customerLines = custLines,
        headers = listOf(
            trL(lang, "الصنف"), trL(lang, "الكمية"), trL(lang, "السعر"),
            trL(lang, "الخصم"), trL(lang, "الإجمالي")
        ),
        lines = lines.map {
            ViewLine(
                name = it.name.trim(),
                qty = fmtBlank(it.qty),
                price = fmtBlank(it.price),
                discount = fmtBlank(it.discount),
                total = fmtBlank(InvoiceCalculator.lineTotal(LineCalc(it.qty, it.price, it.discount)))
            )
        },
        showDiscountColumn = lines.any { it.discount > 0 },
        totals = rows,
        notesLabel = trL(lang, "ملاحظات"),
        notes = inv.notes.trim(),
        cancelled = inv.cancelled,
        cancelledText = trL(lang, "ملغاة")
    )
}
