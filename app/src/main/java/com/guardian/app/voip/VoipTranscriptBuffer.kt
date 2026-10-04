package com.guardian.app.voip

/** Each speaker has one evolving utterance; a final replaces that utterance. */
class VoipTranscriptBuffer(private val maxLines: Int = 100) {
    private val lines = mutableListOf<TranscriptLine>()

    @Synchronized fun accept(line: TranscriptLine): List<TranscriptLine> {
        if (line.text.isBlank()) return lines.toList()
        val pending = lines.indexOfLast { it.speaker == line.speaker && !it.isFinal }
        if (pending >= 0) {
            lines[pending] = line.copy(timestamp = lines[pending].timestamp)
        } else {
            lines.add(line)
        }
        while (lines.size > maxLines) lines.removeAt(0)
        return lines.toList()
    }

    @Synchronized fun context(maxChars: Int = 2000): String = lines.joinToString("\n") {
        "${if (it.speaker == Speaker.LOCAL) "You" else "Caller"}: ${it.text}"
    }.takeLast(maxChars)

    @Synchronized fun clear() = lines.clear()
}
