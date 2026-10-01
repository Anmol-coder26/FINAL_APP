package com.guardian.app.domain.risk

/**
 * ScamSignal represents a piece of observed evidence.
 * It does NOT represent a final scam verdict on its own.
 *
 * @param type The categorized type of observed phenomenon.
 * @param context The conversational context (e.g., request vs. mention vs. warning).
 * @param source The origin of the observation (e.g., rule, model, identity check).
 * @param strength Severity/weight heuristic of the signal in [0.0, 1.0].
 * @param confidence Epistemic confidence in the observation in [0.0, 1.0].
 * @param rawEvidence Human-readable verbatim quote or phrase snippet.
 * @param metadata Extensible key-value metadata for diagnostic logging.
 * @param timestampMs Monotonic or wall-clock timestamp when signal was captured.
 */
data class ScamSignal(
    val type: SignalType,
    val context: SignalContext = SignalContext.UNKNOWN,
    val source: SignalSource = SignalSource.LOCAL_RULE,
    val strength: Float = 1.0f,
    val confidence: Float = 1.0f,
    val rawEvidence: String = "",
    val metadata: Map<String, String> = emptyMap(),
    val timestampMs: Long = System.currentTimeMillis()
) {
    init {
        require(strength in 0.0f..1.0f) { "Signal strength must be within [0.0, 1.0], was $strength" }
        require(confidence in 0.0f..1.0f) { "Signal confidence must be within [0.0, 1.0], was $confidence" }
    }
}
