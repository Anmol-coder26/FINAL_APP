package com.guardian.app.domain.risk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Behavior-focused unit tests for [RiskEngine] and [RiskPolicy].
 *
 * Verifies all 14 mandatory behavioral test cases:
 * 1. no signals → low risk
 * 2. benign OTP mention → low risk
 * 3. OTP warning/negation → low risk
 * 4. OTP request → elevated risk
 * 5. OTP request + urgency → high risk
 * 6. money request alone → elevated but not automatically critical
 * 7. money request + urgency → high risk
 * 8. authority claim + fear + money request → very high risk
 * 9. remote-access request + bank/authority context → high/very high risk
 * 10. trusted-contact rejection → very strong identity-risk evidence
 * 11. trusted-contact confirmation reduces identity concern appropriately
 * 12. multiple benign/informational signals do NOT accidentally accumulate into high risk
 * 13. contradictory benign context can reduce an earlier ambiguous signal
 * 14. deterministic input produces deterministic output
 */
class RiskEngineTest {

    private lateinit var engine: RiskEngine

    @Before
    fun setUp() {
        engine = RiskEngine()
    }

    // -------------------------------------------------------------------------
    // Test 1: No signals → Low risk
    // -------------------------------------------------------------------------
    @Test
    fun test1_noSignals_producesLowRisk() {
        val assessment = engine.evaluate(emptyList())

        assertEquals(0, assessment.score)
        assertEquals(RiskLevel.LOW, assessment.level)
        assertTrue(assessment.signals.isEmpty())
        assertTrue(assessment.explanation.contains("normal", ignoreCase = true))
        assertTrue(assessment.recommendedActions.isNotEmpty())
    }

    // -------------------------------------------------------------------------
    // Test 2: Benign OTP mention → Low risk
    // -------------------------------------------------------------------------
    @Test
    fun test2_benignOtpMention_producesLowRisk() {
        val signal = ScamSignal(
            type = SignalType.OTP_REQUEST,
            context = SignalContext.MENTION,
            source = SignalSource.LOCAL_RULE,
            rawEvidence = "I received an OTP for my online shopping order."
        )

        val assessment = engine.evaluate(listOf(signal))

        assertEquals(RiskLevel.LOW, assessment.level)
        assertTrue("Score ${assessment.score} should be <= ${RiskLevel.LOW_MAX}", assessment.score <= RiskLevel.LOW_MAX)
        assertTrue(assessment.explanation.contains("LOW", ignoreCase = true))
    }

    // -------------------------------------------------------------------------
    // Test 3: OTP warning/negation → Low risk
    // -------------------------------------------------------------------------
    @Test
    fun test3_otpWarningOrNegation_producesLowRisk() {
        val warningSignal = ScamSignal(
            type = SignalType.OTP_REQUEST,
            context = SignalContext.WARNING,
            source = SignalSource.SEMANTIC_MODEL,
            rawEvidence = "Never share your OTP or password with any bank representative."
        )

        val negationSignal = ScamSignal(
            type = SignalType.PIN_REQUEST,
            context = SignalContext.NEGATION,
            source = SignalSource.LOCAL_RULE,
            rawEvidence = "I will never give my PIN to you."
        )

        val assessment = engine.evaluate(listOf(warningSignal, negationSignal))

        assertEquals(0, assessment.score)
        assertEquals(RiskLevel.LOW, assessment.level)
        assertTrue(assessment.explanation.contains("defensive", ignoreCase = true) ||
                assessment.explanation.contains("LOW", ignoreCase = true))
    }

    // -------------------------------------------------------------------------
    // Test 4: OTP request alone → Elevated risk
    // -------------------------------------------------------------------------
    @Test
    fun test4_otpRequestAlone_producesElevatedRisk() {
        val signal = ScamSignal(
            type = SignalType.OTP_REQUEST,
            context = SignalContext.REQUEST,
            source = SignalSource.LOCAL_RULE,
            rawEvidence = "Please tell me the OTP sent to your phone."
        )

        val assessment = engine.evaluate(listOf(signal))

        assertEquals(RiskLevel.MEDIUM, assessment.level)
        assertEquals(55, assessment.score)
        assertTrue(assessment.explanation.contains("ELEVATED", ignoreCase = true))
        assertTrue(assessment.recommendedActions.any { it.contains("OTP", ignoreCase = true) })
    }

    // -------------------------------------------------------------------------
    // Test 5: OTP request + urgency → High risk
    // -------------------------------------------------------------------------
    @Test
    fun test5_otpRequestPlusUrgency_producesHighRisk() {
        val otpDemand = ScamSignal(
            type = SignalType.OTP_REQUEST,
            context = SignalContext.REQUEST,
            source = SignalSource.LOCAL_RULE,
            rawEvidence = "Give me the OTP immediately!"
        )

        val urgency = ScamSignal(
            type = SignalType.URGENCY,
            context = SignalContext.COMMAND,
            source = SignalSource.SEMANTIC_MODEL,
            rawEvidence = "Your account will be blocked within 5 minutes if you do not comply."
        )

        val assessment = engine.evaluate(listOf(otpDemand, urgency))

        assertEquals(RiskLevel.HIGH, assessment.level)
        assertEquals(75, assessment.score) // 55 base + 10 urgency + 10 synergy
        assertTrue(assessment.explanation.contains("HIGH", ignoreCase = true))
        assertTrue(assessment.recommendedActions.any { it.contains("OTP", ignoreCase = true) })
    }

    // -------------------------------------------------------------------------
    // Test 6: Money request alone → Elevated but not automatically critical
    // -------------------------------------------------------------------------
    @Test
    fun test6_moneyRequestAlone_producesElevatedNotCriticalRisk() {
        val signal = ScamSignal(
            type = SignalType.MONEY_TRANSFER_REQUEST,
            context = SignalContext.REQUEST,
            source = SignalSource.LOCAL_RULE,
            rawEvidence = "Can you send Rs 2000 to my UPI ID?"
        )

        val assessment = engine.evaluate(listOf(signal))

        assertEquals(RiskLevel.MEDIUM, assessment.level)
        assertEquals(45, assessment.score)
        assertFalse(assessment.level == RiskLevel.CRITICAL)
        assertTrue(assessment.explanation.contains("ELEVATED", ignoreCase = true))
        assertTrue(assessment.recommendedActions.any { it.contains("verify", ignoreCase = true) })
    }

    // -------------------------------------------------------------------------
    // Test 7: Money request + urgency → High risk
    // -------------------------------------------------------------------------
    @Test
    fun test7_moneyRequestPlusUrgency_producesHighRisk() {
        val moneyDemand = ScamSignal(
            type = SignalType.MONEY_TRANSFER_REQUEST,
            context = SignalContext.REQUEST,
            source = SignalSource.LOCAL_RULE,
            rawEvidence = "Transfer Rs 5000 right now."
        )

        val timePressure = ScamSignal(
            type = SignalType.TIME_PRESSURE,
            context = SignalContext.COMMAND,
            source = SignalSource.SEMANTIC_MODEL,
            rawEvidence = "You must pay immediately before the deadline expires."
        )

        val assessment = engine.evaluate(listOf(moneyDemand, timePressure))

        assertEquals(RiskLevel.HIGH, assessment.level)
        assertEquals(70, assessment.score) // 45 base + 10 pressure + 15 synergy
        assertTrue(assessment.explanation.contains("HIGH", ignoreCase = true))
    }

    // -------------------------------------------------------------------------
    // Test 8: Authority claim + fear + money request → Very high (Critical) risk
    // -------------------------------------------------------------------------
    @Test
    fun test8_authorityClaimPlusFearPlusMoneyRequest_producesCriticalRisk() {
        val authority = ScamSignal(
            type = SignalType.GOVERNMENT_LAW_ENFORCEMENT_CLAIM,
            context = SignalContext.COMMAND,
            source = SignalSource.SEMANTIC_MODEL,
            rawEvidence = "This is Officer Sharma calling from CBI Headquarters."
        )

        val fearThreat = ScamSignal(
            type = SignalType.FEAR,
            context = SignalContext.COMMAND,
            source = SignalSource.SEMANTIC_MODEL,
            rawEvidence = "You are placed under digital arrest and will be sent to jail."
        )

        val moneyDemand = ScamSignal(
            type = SignalType.MONEY_TRANSFER_REQUEST,
            context = SignalContext.REQUEST,
            source = SignalSource.LOCAL_RULE,
            rawEvidence = "Transfer refundable bail bond of Rs 50,000 to this verification account."
        )

        val assessment = engine.evaluate(listOf(authority, fearThreat, moneyDemand))

        assertEquals(RiskLevel.CRITICAL, assessment.level)
        assertTrue("Score ${assessment.score} should be >= 85", assessment.score >= 85)
        assertTrue(assessment.explanation.contains("Digital Arrest", ignoreCase = true))
        assertTrue(assessment.recommendedActions.any { it.contains("arrest", ignoreCase = true) })
    }

    // -------------------------------------------------------------------------
    // Test 9: Remote-access request + bank/authority context → Very high (Critical) risk
    // -------------------------------------------------------------------------
    @Test
    fun test9_remoteAccessRequestPlusBankPretext_producesCriticalRisk() {
        val bankClaim = ScamSignal(
            type = SignalType.BANK_COMPANY_CLAIM,
            context = SignalContext.COMMAND,
            source = SignalSource.SEMANTIC_MODEL,
            rawEvidence = "I am calling from SBI fraud prevention department."
        )

        val remoteAccessDemand = ScamSignal(
            type = SignalType.REMOTE_ACCESS_REQUEST,
            context = SignalContext.REQUEST,
            source = SignalSource.LOCAL_RULE,
            rawEvidence = "Install AnyDesk app from Play Store so we can fix your account block."
        )

        val assessment = engine.evaluate(listOf(bankClaim, remoteAccessDemand))

        assertEquals(RiskLevel.CRITICAL, assessment.level)
        assertEquals(90, assessment.score) // 55 remote + 15 bank + 20 synergy
        assertTrue(assessment.explanation.contains("remote access", ignoreCase = true) ||
                assessment.explanation.contains("remote-access", ignoreCase = true))
        assertTrue(assessment.recommendedActions.any { it.contains("AnyDesk", ignoreCase = true) })
    }

    // -------------------------------------------------------------------------
    // Test 10: Trusted-contact rejection → Very strong identity-risk evidence
    // -------------------------------------------------------------------------
    @Test
    fun test10_trustedContactRejection_producesStrongIdentityRisk() {
        val rejectionSignal = ScamSignal(
            type = SignalType.TRUSTED_CONTACT_VERIFICATION_REJECTED,
            context = SignalContext.COMMAND,
            source = SignalSource.HUMAN_VERIFICATION,
            rawEvidence = "Cryptographic signature check failed. Caller is not recognized contact."
        )

        val assessment = engine.evaluate(listOf(rejectionSignal))

        assertEquals(RiskLevel.HIGH, assessment.level)
        assertEquals(65, assessment.score)
        assertTrue(assessment.explanation.contains("verification failed", ignoreCase = true))
        assertTrue(assessment.recommendedActions.any { it.contains("Hang up", ignoreCase = true) })
    }

    // -------------------------------------------------------------------------
    // Test 11: Trusted-contact confirmation reduces identity concern appropriately
    // -------------------------------------------------------------------------
    @Test
    fun test11_trustedContactConfirmation_reducesIdentityConcernWithoutErasingDirectExtortion() {
        // Sub-case 11A: Identity claim alone + trusted contact confirmed → score drops to 0 / LOW
        val authorityClaim = ScamSignal(
            type = SignalType.AUTHORITY_CLAIM,
            context = SignalContext.COMMAND,
            source = SignalSource.SEMANTIC_MODEL,
            rawEvidence = "I am your manager."
        )
        val contactConfirmed = ScamSignal(
            type = SignalType.TRUSTED_CONTACT_VERIFICATION_CONFIRMED,
            context = SignalContext.COMMAND,
            source = SignalSource.HUMAN_VERIFICATION,
            rawEvidence = "Family contact verified via public key challenge."
        )

        val assessmentA = engine.evaluate(listOf(authorityClaim, contactConfirmed))
        assertEquals(0, assessmentA.score)
        assertEquals(RiskLevel.LOW, assessmentA.level)
        assertTrue(assessmentA.explanation.contains("trusted contact", ignoreCase = true))

        // Sub-case 11B: Money demand + identity claim + confirmation
        // Identity risk is neutralized, but money demand evidence remains!
        val moneyDemand = ScamSignal(
            type = SignalType.MONEY_TRANSFER_REQUEST,
            context = SignalContext.REQUEST,
            source = SignalSource.LOCAL_RULE,
            rawEvidence = "Can you send 1000 rupees?"
        )

        val assessmentB = engine.evaluate(listOf(authorityClaim, moneyDemand, contactConfirmed))
        assertEquals(45, assessmentB.score) // Money base remains active, identity claim eliminated
        assertEquals(RiskLevel.MEDIUM, assessmentB.level)
        assertFalse(assessmentB.score == 0) // Did NOT blindly erase money demand!
    }

    // -------------------------------------------------------------------------
    // Test 12: Multiple benign/informational signals do NOT accumulate into high risk
    // -------------------------------------------------------------------------
    @Test
    fun test12_multipleBenignMentions_doNotAccumulateIntoHighRisk() {
        val benignSignals = listOf(
            ScamSignal(SignalType.OTP_REQUEST, SignalContext.MENTION, rawEvidence = "Did you get the OTP for grocery?"),
            ScamSignal(SignalType.PIN_REQUEST, SignalContext.MENTION, rawEvidence = "I forgot my ATM PIN yesterday."),
            ScamSignal(SignalType.BANK_COMPANY_CLAIM, SignalContext.MENTION, rawEvidence = "I need to visit the bank branch."),
            ScamSignal(SignalType.AUTHORITY_CLAIM, SignalContext.MENTION, rawEvidence = "I saw police near the crossroad."),
            ScamSignal(SignalType.MONEY_TRANSFER_REQUEST, SignalContext.MENTION, rawEvidence = "We can split the bill transfer later."),
            ScamSignal(SignalType.REMOTE_ACCESS_REQUEST, SignalContext.MENTION, rawEvidence = "My tech team uses screen share for meetings."),
            ScamSignal(SignalType.SENSITIVE_INFO_REQUEST, SignalContext.MENTION, rawEvidence = "Aadhaar card update center is open.")
        )

        val assessment = engine.evaluate(benignSignals)

        assertEquals(RiskLevel.LOW, assessment.level)
        assertTrue("Passive accumulation ${assessment.score} must not exceed 15", assessment.score <= 15)
        assertTrue(assessment.explanation.contains("LOW", ignoreCase = true))
    }

    // -------------------------------------------------------------------------
    // Test 13: Contradictory benign context can reduce an earlier ambiguous signal
    // -------------------------------------------------------------------------
    @Test
    fun test13_contradictoryBenignContext_reducesAmbiguousSignal() {
        val ambiguousSignal = ScamSignal(
            type = SignalType.OTP_REQUEST,
            context = SignalContext.MENTION,
            source = SignalSource.LOCAL_RULE,
            rawEvidence = "The message mentions an OTP code."
        )

        // Alone: produces small passive score
        val assessmentBefore = engine.evaluate(listOf(ambiguousSignal))
        assertTrue(assessmentBefore.score > 0)

        // Now accompanied by explicit defensive warning
        val defensiveWarning = ScamSignal(
            type = SignalType.OTP_REQUEST,
            context = SignalContext.WARNING,
            source = SignalSource.SEMANTIC_MODEL,
            rawEvidence = "Bank warning: Never share your OTP with any caller under any circumstances."
        )

        val assessmentAfter = engine.evaluate(listOf(ambiguousSignal, defensiveWarning))

        // Ambiguous signal was neutralized by specific defensive context
        assertEquals(0, assessmentAfter.score)
        assertEquals(RiskLevel.LOW, assessmentAfter.level)
    }

    // -------------------------------------------------------------------------
    // Test 14: Deterministic input produces deterministic output
    // -------------------------------------------------------------------------
    @Test
    fun test14_deterministicInput_producesDeterministicOutput() {
        val signals = listOf(
            ScamSignal(
                type = SignalType.GOVERNMENT_LAW_ENFORCEMENT_CLAIM,
                context = SignalContext.COMMAND,
                source = SignalSource.SEMANTIC_MODEL,
                rawEvidence = "This is Delhi Police Crime Branch."
            ),
            ScamSignal(
                type = SignalType.URGENCY,
                context = SignalContext.COMMAND,
                source = SignalSource.SEMANTIC_MODEL,
                rawEvidence = "Stay on this call right now or warrant will be issued."
            ),
            ScamSignal(
                type = SignalType.MONEY_TRANSFER_REQUEST,
                context = SignalContext.REQUEST,
                source = SignalSource.LOCAL_RULE,
                rawEvidence = "Pay Rs 25,000 penalty immediately."
            )
        )

        val assessment1 = engine.evaluate(signals)
        val assessment2 = engine.evaluate(signals)
        val assessment3 = engine.evaluate(signals)

        assertEquals(assessment1.score, assessment2.score)
        assertEquals(assessment2.score, assessment3.score)
        assertEquals(assessment1.level, assessment2.level)
        assertEquals(assessment1.explanation, assessment2.explanation)
        assertEquals(assessment1.recommendedActions, assessment2.recommendedActions)
        assertEquals(assessment1.metadata, assessment2.metadata)
    }

    // -------------------------------------------------------------------------
    // Test 15: Active identity mismatch can exceed passive ceiling
    // -------------------------------------------------------------------------
    @Test
    fun test15_activeIdentityMismatch_canExceedPassiveCeiling() {
        val activeMismatch = ScamSignal(
            type = SignalType.IDENTITY_MISMATCH,
            context = SignalContext.COMMAND,
            source = SignalSource.IDENTITY,
            rawEvidence = "Voice biometrics and caller pattern do not match saved contact profile."
        )

        val assessment = engine.evaluate(listOf(activeMismatch))

        assertEquals(35, assessment.score)
        assertTrue("Active identity mismatch (${assessment.score}) must exceed passive ceiling (15)", assessment.score > 15)
        assertFalse("Identity mismatch alone must not be automatically CRITICAL", assessment.level == RiskLevel.CRITICAL)
    }

    // -------------------------------------------------------------------------
    // Test 16: Passive identity/authority mention stays LOW
    // -------------------------------------------------------------------------
    @Test
    fun test16_passiveIdentityOrAuthorityMention_staysLow() {
        val passiveAuthority = ScamSignal(
            type = SignalType.AUTHORITY_CLAIM,
            context = SignalContext.MENTION,
            source = SignalSource.LOCAL_RULE,
            rawEvidence = "I saw police near the crossroad."
        )
        val passiveBank = ScamSignal(
            type = SignalType.BANK_COMPANY_CLAIM,
            context = SignalContext.MENTION,
            source = SignalSource.LOCAL_RULE,
            rawEvidence = "I need to visit the bank branch tomorrow."
        )

        val assessment = engine.evaluate(listOf(passiveAuthority, passiveBank))

        assertEquals(RiskLevel.LOW, assessment.level)
        assertTrue("Score ${assessment.score} must not exceed passive ceiling (15)", assessment.score <= 15)
        assertTrue(assessment.explanation.contains("LOW", ignoreCase = true))
    }

    // -------------------------------------------------------------------------
    // Test 17: Conflicting trusted-contact verification evidence does NOT silently resolve to LOW
    // -------------------------------------------------------------------------
    @Test
    fun test17_conflictingTrustedContactVerification_doesNotSilentlyResolveToLow() {
        val confirmedSignal = ScamSignal(
            type = SignalType.TRUSTED_CONTACT_VERIFICATION_CONFIRMED,
            context = SignalContext.COMMAND,
            source = SignalSource.HUMAN_VERIFICATION,
            rawEvidence = "Contact was marked confirmed in a previous call record."
        )
        val rejectedSignal = ScamSignal(
            type = SignalType.TRUSTED_CONTACT_VERIFICATION_REJECTED,
            context = SignalContext.COMMAND,
            source = SignalSource.HUMAN_VERIFICATION,
            rawEvidence = "Cryptographic signature failed. Contact verification rejected."
        )

        val assessment = engine.evaluate(listOf(confirmedSignal, rejectedSignal))

        assertEquals(RiskLevel.HIGH, assessment.level)
        assertEquals(65, assessment.score)
        assertFalse("Conflicting verification must not silently resolve to LOW", assessment.level == RiskLevel.LOW)
        assertTrue(assessment.explanation.contains("conflict", ignoreCase = true))
        assertTrue(assessment.recommendedActions.any { it.contains("Conflicting", ignoreCase = true) })
    }

    // -------------------------------------------------------------------------
    // Test 18: Remote-access explanation does not claim a trojan/malware was detected
    // -------------------------------------------------------------------------
    @Test
    fun test18_remoteAccessExplanation_doesNotClaimTrojanDetected() {
        val bankPretext = ScamSignal(
            type = SignalType.BANK_COMPANY_CLAIM,
            context = SignalContext.COMMAND,
            source = SignalSource.SEMANTIC_MODEL,
            rawEvidence = "I am calling from HDFC Bank Security."
        )
        val remoteAccessDemand = ScamSignal(
            type = SignalType.REMOTE_ACCESS_REQUEST,
            context = SignalContext.REQUEST,
            source = SignalSource.LOCAL_RULE,
            rawEvidence = "Install AnyDesk app from store so we can inspect your account."
        )

        val assessment = engine.evaluate(listOf(bankPretext, remoteAccessDemand))

        assertEquals(RiskLevel.CRITICAL, assessment.level)
        assertFalse("Explanation must not claim a trojan was detected", assessment.explanation.contains("trojan", ignoreCase = true))
        assertFalse("Explanation must not claim malware was proven", assessment.explanation.contains("malware", ignoreCase = true))
        assertTrue(assessment.explanation.contains("remote-access", ignoreCase = true) ||
                assessment.explanation.contains("remote access", ignoreCase = true))
    }
}
