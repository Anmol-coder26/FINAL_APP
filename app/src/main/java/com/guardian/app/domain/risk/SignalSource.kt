package com.guardian.app.domain.risk

/**
 * Origin of the observed evidence.
 * Supports multi-modal inputs without coupling the risk domain to any specific external provider or SDK.
 */
enum class SignalSource {
    LOCAL_RULE,
    SEMANTIC_MODEL,
    IDENTITY,
    HUMAN_VERIFICATION,
    AUDIO_HEURISTIC,
    REPUTATION
}
