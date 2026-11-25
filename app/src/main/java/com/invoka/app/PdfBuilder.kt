package com.invoka.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Picture
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

class Generated(
    val file: File,
    val pages: Int,
    val wPt: Int,
    val hPt: Int,
    val previews: List<Bitmap>
)

object PdfService {
    suspend fun generate(
        ctx: Context, iwl: InvoiceWithLines, biz: BusinessProfile, paper: Paper, lang: String
    ): Generated = withContext(Dispatchers.Default) {
        val view = buildView(iwl, biz, lang)
        val logoFile = File(ctx.filesDir, "logo.png")
        val logo = if (logoFile.exists()) BitmapFactory.decodeFile(logoFile.path) else null
        val dir = File(ctx.cacheDir, "invoices").apply { mkdirs() }
        val out = File(dir, "invoice_${iwl.invoice.number}.pdf")
        val built = PdfBuilder.build(view, logo, paper, lang == "ar", out)
        val previews = renderPreviews(out, if (paper.wPt < 300) 600 else 1000)
        Generated(out, built.first, paper.wPt, built.second, previews)
    }

    private fun renderPreviews(file: File, targetW: Int): List<Bitmap> {
        val list = mutableListOf<Bitmap>()
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val r = PdfRenderer(pfd)
        try {
            for (i in 0 until r.pageCount) {
                val pg = r.openPage(i)
                val scale = min(targetW.toFloat() / pg.width, 8000f / pg.height)
                val bw = max(1, (pg.width * scale).toInt())
                val bh = max(1, (pg.height * scale).toInt())
                val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                pg.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                pg.close()
                list.add(bmp)
            }
        } finally {
            r.close()
            try { pfd.close() } catch (e: Exception) {}
        }
        return list
    }
}

object PdfBuilder {
    /** Returns (pageCount, firstPageHeightPt). */
    fun build(view: InvoiceView, logo: Bitmap?, paper: Paper, rtl: Boolean, out: File): Pair<Int, Int> {
        val doc = PdfDocument()
        val w = paper.wPt
        var pages = 0
        var firstH = 0
        try {
            val fixedH = paper.hPt
            if (fixedH == null) {
                // dry run to measure the total height of the receipt
                val pic = Picture()
                val c = pic.beginRecording(w, 10000)
                val endY = PdfLayout(view, logo, rtl, w.toFloat(), null, c, null, 1000f).run()
                pic.endRecording()
                val h = ceil(endY).toInt().coerceAtLeast(160)
                val page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, 1).create())
                PdfLayout(view, logo, rtl, w.toFloat(), null, page.canvas, null, h.toFloat()).run()
                doc.finishPage(page)
                pages = 1
                firstH = h
            } else {
                var num = 1
                var page = doc.startPage(PdfDocument.PageInfo.Builder(w, fixedH, num).create())
                val lay = PdfLayout(view, logo, rtl, w.toFloat(), fixedH.toFloat(), page.canvas, {
                    doc.finishPage(page)
                    num++
                    page = doc.startPage(PdfDocument.PageInfo.Builder(w, fixedH, num).create())
                    page.canvas
                }, fixedH.toFloat())
                lay.run()
                doc.finishPage(page)
                pages = num
                firstH = fixedH
            }
            FileOutputStream(out).use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
        return Pair(pages, firstH)
    }
}

private class PdfLayout(
    private val v: InvoiceView,
    private val logo: Bitmap?,
    private val rtl: Boolean,
    private val w: Float,
    private val pageH: Float?,
    private var canvas: Canvas,
    private val newPage: (() -> Canvas)?,
    private val stampH: Float
) {
    private val narrow = w < 300f
    private val m = if (narrow) 8f else if (w < 450f) 24f else 36f
    private val cw = w - 2 * m
    private val fs = if (narrow) 8.5f else if (w < 450f) 9.5f else 11f
    private val dir = if (rtl) TextDirectionHeuristics.RTL else TextDirectionHeuristics.LTR
    private val ink = Color.rgb(27, 31, 59)
    private val accent = Color.rgb(45, 58, 158)
    private val grayInk = Color.rgb(90, 95, 120)
    private var y = m

    private val aStart = Layout.Alignment.ALIGN_NORMAL
    private val aEnd = Layout.Alignment.ALIGN_OPPOSITE
    private val aMid = Layout.Alignment.ALIGN_CENTER

    private class Col(val off: Float, val wd: Float, val align: Layout.Alignment)

    private fun tp(size: Float, bold: Boolean = false, color: Int = ink) =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }

    private fun fill(color: Int) = Paint().apply { this.color = color; style = Paint.Style.FILL }

    private fun lay(t: String, width: Float, p: TextPaint, a: Layout.Alignment): StaticLayout =
        StaticLayout.Builder.obtain(t, 0, t.length, p, max(1, width.toInt()))
            .setAlignment(a)
            .setTextDirection(dir)
            .setIncludePad(false)
            .build()

    private fun put(sl: StaticLayout, left: Float, top: Float) {
        canvas.save()
        canvas.translate(left, top)
        sl.draw(canvas)
        canvas.restore()
    }

    /** Left x for a column that starts [offset] from the reading-start edge. */
    private fun colX(offset: Float, width: Float): Float =
        if (rtl) m + cw - offset - width else m + offset

    private fun ensure(h: Float): Boolean {
        if (pageH != null && y + h > pageH - m) {
            stamp()
            canvas = newPage!!.invoke()
            y = m
            return true
        }
        return false
    }

    private fun divider() {
        val p = Paint().apply { color = Color.rgb(210, 214, 230); strokeWidth = 0.8f }
        canvas.drawLine(m, y, w - m, y, p)
        y += 6f
    }

    private fun stamp() {
        if (!v.cancelled) return
        val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(70, 200, 30, 30)
            textSize = if (narrow) 34f else 90f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        canvas.save()
        canvas.rotate(-28f, w / 2, stampH / 2)
        canvas.drawText(v.cancelledText, w / 2, stampH / 2, p)
        canvas.restore()
    }

    fun run(): Float {
        header()
        customer()
        table()
        totals()
        notes()
        stamp()
        return y + m + 6f
    }

    private fun header() {
        val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG)
        val small = tp(fs - 0.5f, false, grayInk)
        if (narrow) {
            if (logo != null) {
                val lh = 44f
                val lw = min(cw, lh * logo.width / logo.height)
                val lx = m + (cw - lw) / 2
                canvas.drawBitmap(logo, null, RectF(lx, y, lx + lw, y + lh), bmpPaint)
                y += lh + 6f
            }
            if (v.businessName.isNotEmpty()) {
                val s = lay(v.businessName, cw, tp(fs + 3f, true), aMid)
                put(s, m, y); y += s.height + 2f
            }
            for (l in v.businessLines) {
                val s = lay(l, cw, small, aMid); put(s, m, y); y += s.height
            }
            y += 4f
            divider()
            val t = lay(v.title, cw, tp(fs + 3f, true, accent), aMid); put(t, m, y); y += t.height
            val n = lay(v.numberText, cw, tp(fs, true), aMid); put(n, m, y); y += n.height
            val d = lay(v.dateText, cw, small, aMid); put(d, m, y); y += d.height + 4f
            divider()
        } else {
            val startW = cw * 0.58f
            val endW = cw - startW - 12f
            var tx = 0f
            var yStart = y
            if (logo != null) {
                val lh = 60f
                val lw = min(120f, lh * logo.width / logo.height)
                val lx = colX(0f, lw)
                canvas.drawBitmap(logo, null, RectF(lx, y, lx + lw, y + lh), bmpPaint)
                tx = lw + 10f
                yStart = y + lh
            }
            var ty = y
            if (v.businessName.isNotEmpty()) {
                val s = lay(v.businessName, startW - tx, tp(fs + 5f, true), aStart)
                put(s, colX(tx, startW - tx), ty); ty += s.height + 2f
            }
            for (l in v.businessLines) {
                val s = lay(l, startW - tx, small, aStart)
                put(s, colX(tx, startW - tx), ty); ty += s.height
            }
            var ye = y
            val endX = colX(cw - endW, endW)
            val t = lay(v.title, endW, tp(fs + 8f, true, accent), aEnd); put(t, endX, ye); ye += t.height + 2f
            val n = lay(v.numberText, endW, tp(fs + 1f, true), aEnd); put(n, endX, ye); ye += n.height
            val d = lay(v.dateText, endW, small, aEnd); put(d, endX, ye); ye += d.height
            y = max(max(yStart, ty), ye) + 8f
            divider()
        }
    }

    private fun customer() {
        if (v.customerName.isEmpty() && v.customerLines.isEmpty()) return
        ensure(48f)
        val s0 = lay(v.customerTitle, cw, tp(fs - 1f, true, accent), aStart)
        put(s0, m, y); y += s0.height + 2f
        if (v.customerName.isNotEmpty()) {
            val s = lay(v.customerName, cw, tp(fs + 1f, true), aStart)
            put(s, m, y); y += s.height
        }
        for (l in v.customerLines) {
            val s = lay(l, cw, tp(fs, false, grayInk), aStart)
            put(s, m, y); y += s.height
        }
        y += 8f
    }

    private fun table() {
        if (v.lines.isEmpty()) return
        if (narrow) { narrowLines(); return }
        val hasD = v.showDiscountColumn
        val qtyW = cw * 0.10f
        val priceW = cw * 0.16f
        val discW = if (hasD) cw * 0.14f else 0f
        val totW = cw * 0.18f
        val nameW = cw - qtyW - priceW - discW - totW
        val cols = buildList {
            add(Col(0f, nameW, aStart))
            add(Col(nameW, qtyW, aMid))
            add(Col(nameW + qtyW, priceW, aMid))
            if (hasD) add(Col(nameW + qtyW + priceW, discW, aMid))
            add(Col(cw - totW, totW, aEnd))
        }
        val heads = buildList {
            add(v.headers[0]); add(v.headers[1]); add(v.headers[2])
            if (hasD) add(v.headers[3])
            add(v.headers[4])
        }
        fun drawHeader() {
            val hp = tp(fs, true, Color.WHITE)
            val ls = cols.mapIndexed { i, c -> lay(heads[i], c.wd - 8f, hp, c.align) }
            val hh = ls.maxOf { it.height }.toFloat() + 10f
            canvas.drawRect(m, y, m + cw, y + hh, fill(accent))
            ls.forEachIndexed { i, s -> put(s, colX(cols[i].off, cols[i].wd) + 4f, y + 5f) }
            y += hh
        }
        ensure(60f)
        drawHeader()
        val bp = tp(fs)
        for ((idx, ln) in v.lines.withIndex()) {
            val cells = buildList {
                add(ln.name); add(ln.qty); add(ln.price)
                if (hasD) add(ln.discount)
                add(ln.total)
            }
            val ls = cols.mapIndexed { i, c -> lay(cells[i], c.wd - 8f, bp, c.align) }
            val rh = ls.maxOf { it.height }.toFloat() + 8f
            if (ensure(rh + 4f)) drawHeader()
            if (idx % 2 == 1) canvas.drawRect(m, y, m + cw, y + rh, fill(Color.rgb(244, 246, 252)))
            ls.forEachIndexed { i, s -> put(s, colX(cols[i].off, cols[i].wd) + 4f, y + 4f) }
            y += rh
        }
        divider()
        y += 4f
    }

    private fun narrowLines() {
        val nameP = tp(fs, true)
        val dp = tp(fs - 0.5f, false, grayInk)
        for (ln in v.lines) {
            val s1 = lay(ln.name, cw, nameP, aStart)
            var detail = listOf(ln.qty, ln.price).filter { it.isNotEmpty() }.joinToString(" × ")
            if (ln.discount.isNotEmpty()) detail += "  " + v.headers[3] + " " + ln.discount
            val s2 = lay(detail, cw * 0.62f, dp, aStart)
            val s3 = lay(ln.total, cw * 0.38f, nameP, aEnd)
            val h = s1.height + max(s2.height, s3.height) + 6f
            ensure(h)
            put(s1, m, y)
            put(s2, colX(0f, cw * 0.62f), y + s1.height)
            put(s3, colX(cw * 0.62f, cw * 0.38f), y + s1.height)
            y += h
        }
        divider()
    }

    private fun totals() {
        val bw = if (narrow) cw else cw * 0.52f
        val off = cw - bw
        val labW = if (narrow) bw * 0.5f else bw * 0.55f
        val valW = bw - labW
        y += 4f
        for (r in v.totals) {
            val p = tp(if (r.strong) fs + 1.5f else fs, r.strong)
            val a = lay(r.label, labW - 4f, p, aStart)
            val b = lay(r.value, valW - 4f, p, aEnd)
            val h = max(a.height, b.height).toFloat() + 8f
            ensure(h)
            if (r.strong) canvas.drawRect(colX(off, bw), y, colX(off, bw) + bw, y + h, fill(Color.rgb(232, 236, 252)))
            put(a, colX(off, labW) + 2f, y + 4f)
            put(b, colX(off + labW, valW) + 2f, y + 4f)
            y += h
        }
    }

    private fun notes() {
        if (v.notes.isEmpty()) return
        y += 10f
        ensure(40f)
        val l = lay(v.notesLabel, cw, tp(fs - 1f, true, accent), aStart)
        put(l, m, y); y += l.height + 2f
        val s = lay(v.notes, cw, tp(fs - 0.5f, false, grayInk), aStart)
        put(s, m, y); y += s.height
    }
}
