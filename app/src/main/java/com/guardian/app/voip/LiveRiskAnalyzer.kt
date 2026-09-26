package com.guardian.app.voip

import android.util.Log
import com.guardian.app.RiskReport
import com.guardian.app.SemanticAnalyzer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LiveRiskAnalyzer(
    private val analyzer: SemanticAnalyzer,
    private val onReport: (RiskReport) -> Unit
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val buffer = StringBuilder()
    private var lastCallTs = 0L
    private val intervalMs = 2000L

    fun start(transcripts: SharedFlow<TranscriptLine>) {
        scope.launch {
            transcripts.collect { line ->
                buffer.append(
                    if (line.speaker == Speaker.LOCAL) "\nYou: ${line.text}"
                    else "\nCaller: ${line.text}"
                )
                if (buffer.length > 2000) {
                    buffer.delete(0, buffer.length - 2000)
                }

                if (line.isFinal) {
                    val now = System.currentTimeMillis()
                    if (now - lastCallTs >= intervalMs) {
                        lastCallTs = now
                        try {
                            val r = analyzer.analyzeChunk(buffer.toString())
                            withContext(Dispatchers.Main) {
                                onReport(r)
                            }
                        } catch (e: Exception) {
                            Log.e("LiveRiskAnalyzer", "Error analyzing chunk: ${e.message}")
                        }
                    }
                }
            }
        }
    }

    fun stop() {
        scope.cancel()
        buffer.clear()
    }
}
