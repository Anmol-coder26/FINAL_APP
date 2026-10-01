package com.guardian.app.domain.risk

/**
 * Immutable final risk outcome produced solely by [RiskEngine].
 *
 * @param score Scaled integer risk score in [0, 100].
 * @param level Categorical risk classification derived from [score].
 * @param signals Underlying structured evidence signals evaluated in this assessment.
 * @param explanation Human-readable concise explanation of the assessment rationale.
 * @param recommendedActions Actionable safety instructions tailored to the detected vectors.
 * @param metadata Additional diagnostic or provenance details.
 */
data class RiskAssessment(
    val score: Int,
    val level: RiskLevel,
    val signals: List<ScamSignal> = emptyList(),
    val explanation: String = "",
    val recommendedActions: List<String> = emptyList(),
    val metadata: Map<String, String> = emptyMap()
) {
    init {
        require(score in 0..100) { "Risk score must be in range [0, 100], was $score" }
    }
}
