package com.guardian.app.domain.risk

import kotlin.math.roundToInt

/**
 * Authoritative, deterministic risk evaluation engine for SuSagi V1.
 *
 * Design Principles:
 * - [Single Authority]: RiskEngine is the sole owner of the final risk score in the new domain layer.
 * - [Deterministic]: Identical signals consistently yield identical scores and explanations.
 * - [Decoupled]: Strictly zero dependencies on Android framework, UI, networking, or Gemini.
 * - [Sub-additive & Context-Aware]: Signals are NOT blindly summed; combinations, compound vectors,
 *   and benign/defensive contexts (WARNING, NEGATION) suppress false positives.
 * - [Dynamic]: Risk can decrease when earlier ambiguous evidence is resolved or superseded.
 */
class RiskEngine(
    private val policy: RiskPolicy = RiskPolicy.DEFAULT
) {

    /**
     * Evaluates a collection of observed signals into an authoritative [RiskAssessment].
     */
    fun evaluate(signals: List<ScamSignal>): RiskAssessment {
        if (signals.isEmpty()) {
            return RiskAssessment(
                score = 0,
                level = RiskLevel.LOW,
                signals = emptyList(),
                explanation = "No suspicious signals detected. Conversation appears normal.",
                recommendedActions = listOf("Continue call normally. Stay alert to requests for credentials or money.")
            )
        }

        // 1. Check for cryptographic/human trusted contact verification evidence
        val hasTrustedContactConfirmed = signals.any {
            it.type == SignalType.TRUSTED_CONTACT_VERIFICATION_CONFIRMED &&
                    !it.context.isBenignOrDefensive
        }
        val hasTrustedContactRejected = signals.any {
            it.type == SignalType.TRUSTED_CONTACT_VERIFICATION_REJECTED &&
                    !it.context.isBenignOrDefensive
        }
        val hasVerificationConflict = hasTrustedContactConfirmed && hasTrustedContactRejected

        // Deterministic Conflict Rule (Fail-Safe Security Default):
        // If both CONFIRMED and REJECTED verification signals coexist in the same evaluation,
        // confirmation CANNOT silently erase rejection evidence. Confirmation suppression is disabled
        // so that the rejection remains active and visible as an unresolved identity threat.
        val isTrustedContactConfirmed = hasTrustedContactConfirmed && !hasTrustedContactRejected

        // 2. Identify defensive/warning contexts (e.g., "Never share your OTP", "Police warning")
        val warningSignalTypes = signals
            .filter { it.context.isBenignOrDefensive }
            .map { it.type }
            .toSet()

        val hasGeneralScamWarning = signals.any {
            it.context.isBenignOrDefensive && (
                    it.type == SignalType.SUSPICIOUS_PHRASE_PATTERN ||
                            it.type == SignalType.INFORMATION_ASYMMETRY
                    )
        }

        // 3. Filter and resolve effective signals
        val activeExtractionSignals = mutableListOf<ScamSignal>()
        val passiveSignals = mutableListOf<ScamSignal>()
        val pressureSignals = mutableListOf<ScamSignal>()
        val identitySignals = mutableListOf<ScamSignal>()
        val supportingSignals = mutableListOf<ScamSignal>()

        for (signal in signals) {
            // Mitigating verification events produce zero risk score
            if (signal.type == SignalType.TRUSTED_CONTACT_VERIFICATION_CONFIRMED) {
                continue
            }

            // Defensive warnings or negations produce 0 threat score individually
            if (signal.context.isBenignOrDefensive) {
                continue
            }

            // Suppress passive/ambiguous signals contradicted by benign context
            if (signal.context.isPassiveOrInformational &&
                (warningSignalTypes.contains(signal.type) || hasGeneralScamWarning)
            ) {
                continue
            }

            // If trusted contact is confirmed, suppress impersonation/identity signals
            if (isTrustedContactConfirmed && signal.type.isIdentityOrImpersonation) {
                continue
            }

            // Route signal to appropriate evaluation pipeline
            when {
                signal.type.isCredentialExtraction ||
                        signal.type.isFinancialExtraction ||
                        signal.type.isDeviceTakeover -> {
                    if (signal.context.isActiveDemand) {
                        activeExtractionSignals.add(signal)
                    } else {
                        passiveSignals.add(signal)
                    }
                }

                signal.type.isPsychologicalPressure -> {
                    if (signal.context.isActiveDemand || signal.context == SignalContext.UNKNOWN) {
                        pressureSignals.add(signal)
                    } else {
                        passiveSignals.add(signal)
                    }
                }

                signal.type.isIdentityOrImpersonation -> {
                    if (signal.context.isPassiveOrInformational) {
                        passiveSignals.add(signal)
                    } else {
                        identitySignals.add(signal)
                    }
                }

                else -> {
                    supportingSignals.add(signal)
                }
            }
        }

        // 4. Base Score Computation with Saturation
        var baseScore = 0

        // Handle Active Extractions (Credential, Financial, Device Takeover)
        var maxExtractionScore = 0
        for (signal in activeExtractionSignals) {
            val weight = getActiveExtractionWeight(signal.type)
            val effective = (weight * signal.strength * signal.confidence).roundToInt()
            if (effective > maxExtractionScore) {
                maxExtractionScore = effective
            }
        }
        baseScore += maxExtractionScore

        // Additional distinct extraction attempts add diminishing increments
        val additionalExtractions = activeExtractionSignals.size - 1
        if (additionalExtractions > 0) {
            baseScore += (additionalExtractions * 5).coerceAtMost(10)
        }

        // Handle Identity / Impersonation Signals
        var maxIdentityScore = 0
        for (signal in identitySignals) {
            val weight = getIdentityWeight(signal.type, signal.context)
            val effective = (weight * signal.strength * signal.confidence).roundToInt()
            if (effective > maxIdentityScore) {
                maxIdentityScore = effective
            }
        }
        baseScore += maxIdentityScore

        // Handle Psychological Pressure
        var maxPressureScore = 0
        for (signal in pressureSignals) {
            val effective = (policy.pressureBase * signal.strength * signal.confidence).roundToInt()
            if (effective > maxPressureScore) {
                maxPressureScore = effective
            }
        }
        baseScore += maxPressureScore

        // Handle Supporting Signals
        for (signal in supportingSignals) {
            val weight = when (signal.type) {
                SignalType.INFORMATION_ASYMMETRY -> policy.asymmetryBase
                SignalType.SUSPICIOUS_PHRASE_PATTERN -> policy.suspiciousPatternBase
                SignalType.REPUTATION_SIGNAL -> policy.reputationBase
                SignalType.SYNTHETIC_VOICE_UNCERTAINTY -> policy.syntheticVoiceBase
                else -> 10
            }
            baseScore += (weight * signal.strength * signal.confidence * 0.5f).roundToInt()
        }

        // Handle Passive / Informational Signals (subject to strict ceiling)
        if (passiveSignals.isNotEmpty()) {
            val passiveTotal = passiveSignals.sumOf {
                (getPassiveBaseWeight(it.type) * policy.passiveMentionMultiplier * it.strength * it.confidence).roundToInt()
            }
            baseScore += passiveTotal.coerceAtMost(policy.maxPassiveAccumulationCeiling)
        }

        // 5. Compound Vector Synergy Rules
        var synergyBonus = 0

        val hasActiveCredentialDemand = activeExtractionSignals.any { it.type.isCredentialExtraction }
        val hasActiveMoneyDemand = activeExtractionSignals.any { it.type == SignalType.MONEY_TRANSFER_REQUEST }
        val hasActiveRemoteDemand = activeExtractionSignals.any { it.type.isDeviceTakeover }

        val hasPressure = pressureSignals.isNotEmpty()
        val hasFearOrThreat = pressureSignals.any { it.type == SignalType.FEAR || it.type == SignalType.THREAT }
        val hasUrgency = pressureSignals.any { it.type == SignalType.URGENCY || it.type == SignalType.TIME_PRESSURE }

        val hasAuthorityPretext = identitySignals.any {
            it.type == SignalType.AUTHORITY_CLAIM ||
                    it.type == SignalType.GOVERNMENT_LAW_ENFORCEMENT_CLAIM ||
                    it.type == SignalType.BANK_COMPANY_CLAIM
        }
        val hasBankPretext = identitySignals.any { it.type == SignalType.BANK_COMPANY_CLAIM }
        val hasIdentityMismatch = identitySignals.any {
            it.type == SignalType.IDENTITY_MISMATCH || it.type == SignalType.CONTRADICTION
        }

        // Vector A: Credential Demand + Coercive Urgency
        if (hasActiveCredentialDemand && (hasUrgency || hasPressure)) {
            synergyBonus += policy.credentialUrgencySynergy
        }

        // Vector B: Money Transfer + Pressure / Fear
        if (hasActiveMoneyDemand && (hasUrgency || hasFearOrThreat || hasPressure)) {
            synergyBonus += policy.financialUrgencySynergy
        }

        // Vector C: Extortion / Digital Arrest (Authority + Fear/Threat + Money Demand)
        if (hasAuthorityPretext && hasFearOrThreat && hasActiveMoneyDemand) {
            synergyBonus += policy.extortionVectorSynergy
        }

        // Vector D: Remote Access + Bank/Authority Pretext
        if (hasActiveRemoteDemand && (hasBankPretext || hasAuthorityPretext)) {
            synergyBonus += policy.remoteAccessBankSynergy
        }

        // Vector E: Identity Mismatch + Active Demand
        if (hasIdentityMismatch && (hasActiveCredentialDemand || hasActiveMoneyDemand)) {
            synergyBonus += policy.identityContradictionSynergy
        }

        // Anchor Check:
        // Distinguish active demand / identity risk anchors from pure passive mentions.
        // Active anchors:
        // - Active extraction demands (OTP, money, remote access, apps)
        // - Active identity risk evidence (rejected contact verification, identity mismatch, contradiction)
        val hasActiveExtractionAnchor = activeExtractionSignals.isNotEmpty()
        val hasActiveIdentityRiskAnchor = identitySignals.any {
            it.type == SignalType.TRUSTED_CONTACT_VERIFICATION_REJECTED ||
                    it.type == SignalType.IDENTITY_MISMATCH ||
                    it.type == SignalType.CONTRADICTION
        }
        val hasActiveRiskAnchor = hasActiveExtractionAnchor || hasActiveIdentityRiskAnchor

        var rawScore = baseScore + synergyBonus
        if (!hasActiveRiskAnchor) {
            // When no active risk anchor is present:
            // 1. Pure passive mentions/quotes without any active signals are strictly capped at maxPassiveAccumulationCeiling (15)
            // 2. Active authority/bank claims alone without demands or mismatch cannot exceed LOW_MAX (39)
            val hasActiveNonAnchorIdentitySignal = identitySignals.isNotEmpty() || pressureSignals.isNotEmpty()
            rawScore = if (hasActiveNonAnchorIdentitySignal) {
                rawScore.coerceAtMost(RiskLevel.LOW_MAX)
            } else {
                rawScore.coerceAtMost(policy.maxPassiveAccumulationCeiling)
            }
        }

        val finalScore = rawScore.coerceIn(0, policy.maxScoreCeiling)
        val riskLevel = RiskLevel.fromScore(finalScore)

        // 6. Generate Human-Readable Rationale and Recommendations
        val explanation = buildExplanation(
            finalScore = finalScore,
            level = riskLevel,
            hasActiveCredentialDemand = hasActiveCredentialDemand,
            hasActiveMoneyDemand = hasActiveMoneyDemand,
            hasActiveRemoteDemand = hasActiveRemoteDemand,
            hasAuthorityPretext = hasAuthorityPretext,
            hasFearOrThreat = hasFearOrThreat,
            hasUrgency = hasUrgency,
            isTrustedContactConfirmed = isTrustedContactConfirmed,
            hasVerificationConflict = hasVerificationConflict,
            identitySignals = identitySignals,
            hasBenignWarning = warningSignalTypes.isNotEmpty()
        )

        val recommendedActions = buildRecommendedActions(
            level = riskLevel,
            hasActiveCredentialDemand = hasActiveCredentialDemand,
            hasActiveMoneyDemand = hasActiveMoneyDemand,
            hasActiveRemoteDemand = hasActiveRemoteDemand,
            hasAuthorityPretext = hasAuthorityPretext,
            isTrustedContactConfirmed = isTrustedContactConfirmed,
            hasVerificationConflict = hasVerificationConflict,
            identitySignals = identitySignals
        )

        return RiskAssessment(
            score = finalScore,
            level = riskLevel,
            signals = signals,
            explanation = explanation,
            recommendedActions = recommendedActions,
            metadata = mapOf(
                "baseScore" to baseScore.toString(),
                "synergyBonus" to synergyBonus.toString(),
                "isTrustedContactConfirmed" to isTrustedContactConfirmed.toString(),
                "hasVerificationConflict" to hasVerificationConflict.toString()
            )
        )
    }

    private fun getActiveExtractionWeight(type: SignalType): Int = when (type) {
        SignalType.OTP_REQUEST,
        SignalType.PIN_REQUEST,
        SignalType.CVV_REQUEST,
        SignalType.PASSWORD_REQUEST -> policy.credentialRequestBase

        SignalType.SENSITIVE_INFO_REQUEST -> policy.sensitiveInfoRequestBase
        SignalType.MONEY_TRANSFER_REQUEST -> policy.moneyTransferRequestBase

        SignalType.REMOTE_ACCESS_REQUEST,
        SignalType.SCREEN_SHARING_REQUEST,
        SignalType.SUSPICIOUS_APP_INSTALLATION -> policy.remoteAccessRequestBase

        SignalType.SUSPICIOUS_LINK_ACTION -> policy.suspiciousLinkBase
        else -> 20
    }

    private fun getPassiveBaseWeight(type: SignalType): Int = when (type) {
        SignalType.OTP_REQUEST,
        SignalType.PIN_REQUEST,
        SignalType.CVV_REQUEST,
        SignalType.PASSWORD_REQUEST -> policy.credentialRequestBase

        SignalType.SENSITIVE_INFO_REQUEST -> policy.sensitiveInfoRequestBase
        SignalType.MONEY_TRANSFER_REQUEST -> policy.moneyTransferRequestBase

        SignalType.REMOTE_ACCESS_REQUEST,
        SignalType.SCREEN_SHARING_REQUEST,
        SignalType.SUSPICIOUS_APP_INSTALLATION -> policy.remoteAccessRequestBase

        SignalType.SUSPICIOUS_LINK_ACTION -> policy.suspiciousLinkBase

        SignalType.AUTHORITY_CLAIM,
        SignalType.GOVERNMENT_LAW_ENFORCEMENT_CLAIM -> policy.authorityClaimBase

        SignalType.BANK_COMPANY_CLAIM -> policy.bankCompanyClaimBase

        else -> 10
    }

    private fun getIdentityWeight(type: SignalType, context: SignalContext): Int {
        val multiplier = if (context.isPassiveOrInformational) policy.passiveMentionMultiplier else 1.0f
        val base = when (type) {
            SignalType.TRUSTED_CONTACT_VERIFICATION_REJECTED -> policy.trustedContactRejectedBase
            SignalType.IDENTITY_MISMATCH -> policy.identityMismatchBase
            SignalType.CONTRADICTION -> policy.contradictionBase
            SignalType.AUTHORITY_CLAIM,
            SignalType.GOVERNMENT_LAW_ENFORCEMENT_CLAIM -> policy.authorityClaimBase
            SignalType.BANK_COMPANY_CLAIM -> policy.bankCompanyClaimBase
            else -> 10
        }
        return (base * multiplier).roundToInt()
    }

    private fun buildExplanation(
        finalScore: Int,
        level: RiskLevel,
        hasActiveCredentialDemand: Boolean,
        hasActiveMoneyDemand: Boolean,
        hasActiveRemoteDemand: Boolean,
        hasAuthorityPretext: Boolean,
        hasFearOrThreat: Boolean,
        hasUrgency: Boolean,
        isTrustedContactConfirmed: Boolean,
        hasVerificationConflict: Boolean,
        identitySignals: List<ScamSignal>,
        hasBenignWarning: Boolean
    ): String {
        val hasRejectedContact = identitySignals.any {
            it.type == SignalType.TRUSTED_CONTACT_VERIFICATION_REJECTED
        }

        return when {
            hasVerificationConflict ->
                "HIGH RISK: Conflicting trusted-contact verification signals detected. Rejection evidence takes precedence as unresolved identity hazard."

            hasAuthorityPretext && hasFearOrThreat && hasActiveMoneyDemand ->
                "CRITICAL THREAT: Digital Arrest / Law Enforcement extortion scheme detected. Caller is using authority coercion and threats to extract funds."

            hasActiveRemoteDemand && hasAuthorityPretext ->
                "CRITICAL RISK: Suspicious remote access request detected under a bank or authority pretext. Caller is requesting device control or screen sharing."

            hasActiveCredentialDemand && hasUrgency ->
                "HIGH RISK: Urgent credential harvesting detected. Caller is demanding sensitive authentication codes (OTP/PIN/Password) under time pressure."

            hasActiveCredentialDemand ->
                "ELEVATED RISK: Credential extraction request detected. Demands for OTP, PIN, or passwords represent a significant security hazard."

            hasActiveMoneyDemand && hasUrgency ->
                "HIGH RISK: Coercive financial transfer demand detected under manufactured urgency."

            hasActiveMoneyDemand ->
                "ELEVATED RISK: Money transfer request observed. Verify recipient identity independently before transferring funds."

            hasRejectedContact ->
                "HIGH RISK: Trusted contact verification failed. Caller does not match verified contact credentials."

            hasActiveRemoteDemand ->
                "HIGH RISK: Suspicious remote access or application installation request detected."

            isTrustedContactConfirmed && finalScore <= RiskLevel.LOW_MAX ->
                "LOW RISK: Caller verified as trusted contact. Identity impersonation concern neutralized."

            hasBenignWarning && finalScore <= RiskLevel.LOW_MAX ->
                "LOW RISK: Defensive or educational security advisory detected in conversation."

            level == RiskLevel.LOW ->
                "LOW RISK: No active scam extraction or coercion patterns detected."

            else ->
                "Risk level is $level (Score: $finalScore) based on observed conversational signals."
        }
    }

    private fun buildRecommendedActions(
        level: RiskLevel,
        hasActiveCredentialDemand: Boolean,
        hasActiveMoneyDemand: Boolean,
        hasActiveRemoteDemand: Boolean,
        hasAuthorityPretext: Boolean,
        isTrustedContactConfirmed: Boolean,
        hasVerificationConflict: Boolean,
        identitySignals: List<ScamSignal>
    ): List<String> {
        val actions = mutableListOf<String>()

        if (hasVerificationConflict) {
            actions.add("Conflicting contact verification detected. Treat call with extreme caution, hang up, and call back on a verified phonebook number.")
        }
        if (hasActiveCredentialDemand) {
            actions.add("Never share your OTP, PIN, CVV, or passwords with anyone, even if they claim to be from your bank.")
        }
        if (hasActiveRemoteDemand) {
            actions.add("Do not install AnyDesk, TeamViewer, or unknown APK applications. Never share your screen.")
        }
        if (hasAuthorityPretext && hasActiveMoneyDemand) {
            actions.add("Indian law enforcement and court officials NEVER arrest citizens over video/voice calls or demand money transfers.")
        }
        if (identitySignals.any { it.type == SignalType.TRUSTED_CONTACT_VERIFICATION_REJECTED } && !hasVerificationConflict) {
            actions.add("Caller failed trusted contact verification. Hang up immediately and call back using your saved phonebook contact.")
        }
        if (hasActiveMoneyDemand && !hasAuthorityPretext) {
            actions.add("Pause and independently verify the recipient's phone number or account before sending any money.")
        }

        if (actions.isEmpty()) {
            if (level == RiskLevel.LOW) {
                actions.add("Continue call normally. Stay alert to unexpected requests for money or security codes.")
            } else {
                actions.add("Exercise caution and do not disclose sensitive personal or financial information.")
            }
        }

        return actions
    }
}
