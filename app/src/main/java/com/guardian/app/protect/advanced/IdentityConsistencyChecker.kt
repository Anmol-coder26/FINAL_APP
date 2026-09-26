package com.guardian.app.protect.advanced

import com.guardian.app.IdentityMismatch

object IdentityConsistencyChecker {
    fun check(
        profile: ContactProfile,
        currentHour: Int,
        signalsInCall: List<String>
    ): IdentityMismatch {
        val mismatches = mutableListOf<String>()

        // Time-of-day
        if (currentHour < profile.usualCallHoursStart || currentHour > profile.usualCallHoursEnd) {
            mismatches += "call outside usual hours (${profile.usualCallHoursStart}h–${profile.usualCallHoursEnd}h)"
        }

        // Topic
        val hasOtp = signalsInCall.any { it.contains("otp", ignoreCase = true) }
        val hasMoney = signalsInCall.any {
            it.contains("transfer", true) || it.contains("upi", true) || it.contains("money", true)
        }

        if (profile.neverAsksForOtp && hasOtp) {
            mismatches += "asked for OTP — this contact never does"
        }
        if (profile.neverAsksForMoney && hasMoney) {
            mismatches += "asked for money — this contact never does"
        }

        return IdentityMismatch(
            detected = mismatches.isNotEmpty(),
            reasons = mismatches
        )
    }
}
