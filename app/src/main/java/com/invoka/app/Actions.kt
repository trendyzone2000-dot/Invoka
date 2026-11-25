package com.invoka.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

fun sharePdf(ctx: Context, file: File, title: String) {
    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)
    val i = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(Intent.createChooser(i, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

fun shareText(ctx: Context, text: String, title: String) {
    val i = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    ctx.startActivity(Intent.createChooser(i, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

fun copyText(ctx: Context, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Invoka", text))
}

fun copyFileToUri(ctx: Context, file: File, uri: Uri): Boolean = try {
    ctx.contentResolver.openOutputStream(uri)?.use { o -> FileInputStream(file).use { it.copyTo(o) } }
    true
} catch (e: Exception) {
    false
}

fun readUriText(ctx: Context, uri: Uri): String? = try {
    ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
} catch (e: Exception) {
    null
}

fun writeUriText(ctx: Context, uri: Uri, text: String): Boolean = try {
    ctx.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(text) }
    true
} catch (e: Exception) {
    false
}

private class PdfPrintAdapter(
    private val file: File,
    private val name: String,
    private val pages: Int
) : PrintDocumentAdapter() {
    override fun onLayout(
        oldAttributes: PrintAttributes?, newAttributes: PrintAttributes?,
        cancellationSignal: CancellationSignal?, callback: LayoutResultCallback?, extras: Bundle?
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback?.onLayoutCancelled()
            return
        }
        val info = PrintDocumentInfo.Builder(name)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(pages)
            .build()
        callback?.onLayoutFinished(info, newAttributes != oldAttributes)
    }

    override fun onWrite(
        pages: Array<out PageRange>?, destination: ParcelFileDescriptor?,
        cancellationSignal: CancellationSignal?, callback: WriteResultCallback?
    ) {
        try {
            FileInputStream(file).use { i ->
                FileOutputStream(destination!!.fileDescriptor).use { o -> i.copyTo(o) }
            }
            callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: Exception) {
            callback?.onWriteFailed(e.message)
        }
    }
}

fun printPdf(ctx: Context, g: Generated, name: String) {
    val pm = ctx.getSystemService(Context.PRINT_SERVICE) as PrintManager
    val attrs = PrintAttributes.Builder()
        .setMediaSize(
            PrintAttributes.MediaSize(
                "invoka_paper", "Invoka",
                (g.wPt / 72f * 1000f).toInt(), (g.hPt / 72f * 1000f).toInt()
            )
        )
        .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
        .build()
    pm.print(name, PdfPrintAdapter(g.file, name, g.pages), attrs)
}
