package com.guardian.app.domain.risk

/**
 * Categorized scam signals representing observed evidence in a communication.
 * A signal represents a piece of evidence, NOT a final scam verdict.
 */
enum class SignalType {
    // Identity / Impersonation
    IDENTITY_CLAIM,
    AUTHORITY_CLAIM,
    BANK_COMPANY_CLAIM,
    GOVERNMENT_LAW_ENFORCEMENT_CLAIM,
    IDENTITY_MISMATCH,
    CONTRADICTION,
    TRUSTED_CONTACT_VERIFICATION_REJECTED,
    TRUSTED_CONTACT_VERIFICATION_CONFIRMED,

    // Requested Actions
    MONEY_TRANSFER_REQUEST,
    OTP_REQUEST,
    PIN_REQUEST,
    CVV_REQUEST,
    PASSWORD_REQUEST,
    SENSITIVE_INFO_REQUEST,
    REMOTE_ACCESS_REQUEST,
    SCREEN_SHARING_REQUEST,
    SUSPICIOUS_APP_INSTALLATION,
    SUSPICIOUS_LINK_ACTION,

    // Psychological Pressure
    URGENCY,
    FEAR,
    THREAT,
    AUTHORITY_PRESSURE,
    GREED_REWARD,
    SECRECY,
    ISOLATION,
    TIME_PRESSURE,

    // Supporting Signals
    INFORMATION_ASYMMETRY,
    SUSPICIOUS_PHRASE_PATTERN,
    REPUTATION_SIGNAL,
    SYNTHETIC_VOICE_UNCERTAINTY;

    val isIdentityOrImpersonation: Boolean
        get() = this in setOf(
            IDENTITY_CLAIM,
            AUTHORITY_CLAIM,
            BANK_COMPANY_CLAIM,
            GOVERNMENT_LAW_ENFORCEMENT_CLAIM,
            IDENTITY_MISMATCH,
            CONTRADICTION,
            TRUSTED_CONTACT_VERIFICATION_REJECTED
        )

    val isCredentialExtraction: Boolean
        get() = this in setOf(
            OTP_REQUEST,
            PIN_REQUEST,
            CVV_REQUEST,
            PASSWORD_REQUEST,
            SENSITIVE_INFO_REQUEST
        )

    val isFinancialExtraction: Boolean
        get() = this in setOf(
            MONEY_TRANSFER_REQUEST,
            SUSPICIOUS_LINK_ACTION
        )

    val isDeviceTakeover: Boolean
        get() = this in setOf(
            REMOTE_ACCESS_REQUEST,
            SCREEN_SHARING_REQUEST,
            SUSPICIOUS_APP_INSTALLATION
        )

    val isPsychologicalPressure: Boolean
        get() = this in setOf(
            URGENCY,
            FEAR,
            THREAT,
            AUTHORITY_PRESSURE,
            GREED_REWARD,
            SECRECY,
            ISOLATION,
            TIME_PRESSURE
        )
}
