package com.invoka.app

data class LineCalc(val qty: Double, val price: Double, val discount: Double)

data class Totals(
    val subtotal: Double,
    val discount: Double,
    val taxable: Double,
    val tax: Double,
    val delivery: Double,
    val other: Double,
    val net: Double,
    val paid: Double,
    val remaining: Double
)

enum class PayStatus { UNPAID, PARTIAL, PAID }

object InvoiceCalculator {
    fun lineTotal(l: LineCalc): Double = round2(maxOf(0.0, l.qty * l.price - l.discount))

    fun totals(
        lines: List<LineCalc>, discount: Double, taxRate: Double,
        delivery: Double, other: Double, paid: Double
    ): Totals {
        val sub = round2(lines.sumOf { lineTotal(it) })
        val disc = round2(minOf(maxOf(discount, 0.0), sub))
        val taxable = round2(sub - disc)
        val tax = round2(taxable * maxOf(taxRate, 0.0) / 100.0)
        val d = round2(maxOf(delivery, 0.0))
        val o = round2(maxOf(other, 0.0))
        val net = round2(taxable + tax + d + o)
        val p = round2(maxOf(paid, 0.0))
        val rem = round2(maxOf(0.0, net - p))
        return Totals(sub, disc, taxable, tax, d, o, net, p, rem)
    }

    fun status(t: Totals): PayStatus = when {
        t.paid <= 0.0 -> PayStatus.UNPAID
        t.remaining <= 0.0 -> PayStatus.PAID
        else -> PayStatus.PARTIAL
    }
}

fun InvoiceWithLines.totals(): Totals = InvoiceCalculator.totals(
    lines.map { LineCalc(it.qty, it.price, it.discount) },
    invoice.discount, invoice.taxRate, invoice.delivery, invoice.otherAmount, invoice.paid
)
