package com.guardian.app.domain.risk

/**
 * Contextual role of the signal within conversational utterance.
 * Essential for false-positive reduction: distinguishes requests from benign warnings or casual mentions.
 */
enum class SignalContext {
    /** An explicit solicitation (e.g., "Give me your OTP", "Please send money") */
    REQUEST,

    /** An authoritative instruction or demand (e.g., "Transfer the funds immediately", "Install AnyDesk now") */
    COMMAND,

    /** Casual or neutral reference without active intent (e.g., "I received an OTP for my order") */
    MENTION,

    /** Defensive or advisory statement (e.g., "Never share your OTP with anyone", "Police warn about fake calls") */
    WARNING,

    /** Repeating third-party statement (e.g., "The caller asked me for OTP, is that safe?") */
    QUOTE,

    /** Explicit rejection or negation (e.g., "I will not share my PIN", "No bank asks for your password") */
    NEGATION,

    /** Ambiguous or unclassified utterance */
    UNKNOWN;

    val isBenignOrDefensive: Boolean
        get() = this == WARNING || this == NEGATION

    val isPassiveOrInformational: Boolean
        get() = this == MENTION || this == QUOTE || this == UNKNOWN

    val isActiveDemand: Boolean
        get() = this == REQUEST || this == COMMAND
}
