package com.guardian.app.domain.risk

/**
 * Deterministic policy rules and scoring weights for the SuSagi V1 Risk Engine.
 *
 * NOTE: These initial weights are V1 deterministic policy weights designed for
 * transparent, rule-driven risk reasoning. They are NOT claimed to be statistically
 * calibrated probabilities and will be tuned through evaluation against benchmark datasets.
 */
data class RiskPolicy(
    // Base signal weights for active extraction (REQUEST / COMMAND)
    val credentialRequestBase: Int = 55,
    val sensitiveInfoRequestBase: Int = 35,
    val moneyTransferRequestBase: Int = 45,
    val remoteAccessRequestBase: Int = 55,
    val suspiciousLinkBase: Int = 35,

    // Identity and Authority base weights
    val authorityClaimBase: Int = 20,
    val bankCompanyClaimBase: Int = 15,
    val identityMismatchBase: Int = 35,
    val contradictionBase: Int = 25,
    val trustedContactRejectedBase: Int = 65,

    // Psychological pressure base weights
    val pressureBase: Int = 10,

    // Supporting signals
    val asymmetryBase: Int = 10,
    val suspiciousPatternBase: Int = 10,
    val reputationBase: Int = 15,
    val syntheticVoiceBase: Int = 10,

    // Passive context damping
    val passiveMentionMultiplier: Float = 0.1f,
    val maxPassiveAccumulationCeiling: Int = 15,

    // Synergy bonuses for compound fraud vectors
    val credentialUrgencySynergy: Int = 10,
    val financialUrgencySynergy: Int = 15,
    val extortionVectorSynergy: Int = 25,       // Authority + Fear/Threat + Money Demand (Digital Arrest)
    val remoteAccessBankSynergy: Int = 20,      // Remote Access Demand + Bank/Authority Pretext
    val identityContradictionSynergy: Int = 15,

    // Upper guardrail
    val maxScoreCeiling: Int = 98
) {
    companion object {
        val DEFAULT = RiskPolicy()
    }
}
