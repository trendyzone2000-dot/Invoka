package com.invoka.app

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

private val symbols = DecimalFormatSymbols(Locale.US)

fun fmt(v: Double): String = DecimalFormat("#,##0.##", symbols).format(v)
fun fmtBlank(v: Double): String = if (abs(v) < 0.005) "" else fmt(v)
fun fmtInput(v: Double): String = if (abs(v) < 0.005) "" else DecimalFormat("0.##", symbols).format(v)
fun fmtDate(ms: Long): String = SimpleDateFormat("dd/MM/yyyy", Locale.US).format(Date(ms))
fun round2(v: Double): Double = Math.round(v * 100.0) / 100.0

fun normalizeDigits(s: String): String = buildString {
    for (c in s) {
        append(
            when (c) {
                in '\u0660'..'\u0669' -> '0' + (c - '\u0660')
                in '\u06F0'..'\u06F9' -> '0' + (c - '\u06F0')
                '\u066B', '\u060C', ',' -> '.'
                else -> c
            }
        )
    }
}

fun parseNum(s: String): Double = normalizeDigits(s).trim().toDoubleOrNull() ?: 0.0

/** Quantity: blank or non-positive means 1. */
fun qtyOf(s: String): Double = parseNum(s).let { if (it <= 0.0) 1.0 else it }

/** DatePicker works in UTC midnight; invoices store local time. */
fun utcPickToLocal(ms: Long): Long {
    val u = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = ms }
    return Calendar.getInstance().apply {
        clear()
        set(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH), 12, 0, 0)
    }.timeInMillis
}

fun localToUtcPick(ms: Long): Long {
    val l = Calendar.getInstance().apply { timeInMillis = ms }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(l.get(Calendar.YEAR), l.get(Calendar.MONTH), l.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
    }.timeInMillis
}
