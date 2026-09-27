package com.guardian.app.evidence

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object EvidencePdfGenerator {

    fun generate(
        context: Context,
        screenshot: Bitmap,
        riskScore: Int,
        callerId: String,
        sourcePackage: String,
        transcript: String
    ): File {
        val dir = File(context.cacheDir, "evidence").apply { mkdirs() }
        val file = File(dir, "Guardian_${System.currentTimeMillis()}.pdf")

        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4
        val page = doc.startPage(pageInfo)
        val canvas = page.canvas

        val titlePaint = Paint().apply {
            textSize = 18f; color = Color.BLACK; isFakeBoldText = true
        }
        val bodyPaint = Paint().apply { textSize = 11f; color = Color.DKGRAY }
        val labelPaint = Paint().apply {
            textSize = 11f; color = Color.BLACK; isFakeBoldText = true
        }
        val dangerPaint = Paint().apply {
            textSize = 14f; color = Color.parseColor("#D32F2F"); isFakeBoldText = true
        }

        var y = 50f

        canvas.drawText("Guardian — Cybercrime Evidence Report", 40f, y, titlePaint)
        y += 30f

        canvas.drawText("Generated:", 40f, y, labelPaint)
        canvas.drawText(
            SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault()).format(Date()),
            140f, y, bodyPaint
        )
        y += 20f

        canvas.drawText("Risk Score:", 40f, y, labelPaint)
        canvas.drawText("$riskScore / 100", 140f, y, dangerPaint)
        y += 20f

        canvas.drawText("Caller ID:", 40f, y, labelPaint)
        canvas.drawText(callerId, 140f, y, bodyPaint)
        y += 20f

        canvas.drawText("Source App:", 40f, y, labelPaint)
        canvas.drawText(sourcePackage, 140f, y, bodyPaint)
        y += 30f

        // Transcript section
        if (transcript.isNotBlank()) {
            canvas.drawText("Transcript Extract:", 40f, y, labelPaint)
            y += 15f
            val transcriptLines = wrapText(transcript, bodyPaint, 515f)
            transcriptLines.take(10).forEach { line ->
                if (y > 700f) return@forEach
                canvas.drawText(line, 40f, y, bodyPaint)
                y += 14f
            }
            y += 15f
        }

        // Screenshot
        canvas.drawText("Screenshot Evidence:", 40f, y, labelPaint)
        y += 15f

        val maxW = 515f
        val scale = maxW / screenshot.width
        val scaledH = (screenshot.height * scale).toInt()
        val scaled = Bitmap.createScaledBitmap(
            screenshot, maxW.toInt(), scaledH, true
        )

        val remainingSpace = 800f - y
        if (scaledH > remainingSpace) {
            val adjustedH = remainingSpace.toInt()
            val adjusted = Bitmap.createScaledBitmap(
                screenshot,
                (screenshot.width * (adjustedH.toFloat() / screenshot.height)).toInt(),
                adjustedH,
                true
            )
            canvas.drawBitmap(adjusted, 40f, y, null)
            y += adjustedH
        } else {
            canvas.drawBitmap(scaled, 40f, y, null)
            y += scaledH
        }

        y += 20f
        canvas.drawText(
            "Note: This is an auto-generated evidence report for",
            40f, y, bodyPaint
        )
        y += 14f
        canvas.drawText(
            "cybercrime.gov.in filing. Not a legal document.",
            40f, y, bodyPaint
        )

        doc.finishPage(page)
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()

        return file
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()

        for (word in words) {
            val test = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (paint.measureText(test) < maxWidth) {
                currentLine = StringBuilder(test)
            } else {
                if (currentLine.isNotEmpty()) lines.add(currentLine.toString())
                currentLine = StringBuilder(word)
            }
        }
        if (currentLine.isNotEmpty()) lines.add(currentLine.toString())
        return lines
    }
}
