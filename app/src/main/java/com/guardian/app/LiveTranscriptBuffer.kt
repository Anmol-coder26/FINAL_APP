package com.guardian.app

/** Interim hypotheses replace each other; only final segments enter history/context. */
class LiveTranscriptBuffer(private val maxChars: Int = 1000, private val maxSegments: Int = 40) {
    private val segments = ArrayDeque<String>()
    var interim: String = ""
        private set
    val history: List<String> get() = segments.toList().asReversed()
    val context: String get() = segments.joinToString(" ").takeLast(maxChars)

    fun accept(text: String, isFinal: Boolean) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        if (isFinal) {
            segments.addLast(clean.takeLast(maxChars))
            while (segments.size > maxSegments) segments.removeFirst()
            interim = ""
        } else interim = clean
    }

    fun clear() {
        segments.clear()
        interim = ""
    }
}
