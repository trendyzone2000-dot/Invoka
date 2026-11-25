package com.invoka.app

data class Paper(val code: String, val wPt: Int, val hPt: Int?)

fun mmToPt(mm: Float): Int = (mm * 72f / 25.4f).toInt()

val PAPER_CODES = listOf("A4", "A5", "T80", "T58", "CUSTOM")

/** hPt == null means "auto height" (thermal rolls). */
fun resolvePaper(code: String, customWmm: Float, customHmm: Float): Paper = when (code) {
    "A5" -> Paper("A5", 420, 595)
    "T80" -> Paper("T80", mmToPt(80f), null)
    "T58" -> Paper("T58", mmToPt(58f), null)
    "CUSTOM" -> Paper(
        "CUSTOM",
        mmToPt(customWmm.coerceIn(40f, 400f)),
        if (customHmm <= 0f) null else mmToPt(customHmm.coerceIn(40f, 600f))
    )
    else -> Paper("A4", 595, 842)
}
