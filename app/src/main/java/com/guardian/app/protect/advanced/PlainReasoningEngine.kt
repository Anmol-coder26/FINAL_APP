package com.guardian.app.protect.advanced

import com.guardian.app.RiskReport

object PlainReasoningEngine {

    fun explain(report: RiskReport): String {
        // Prefer Gemini-returned plain reasoning if available
        if (report.plainReasoning.isNotBlank()) return report.plainReasoning

        val reasons = mutableListOf<String>()

        if (report.engines.pretextLegitimacy.detected) {
            val type = report.engines.pretextLegitimacy.type.ifBlank { "an official" }
            reasons += "the caller claimed to be from $type"
        }
        if (report.engines.intentRisk.detected) {
            val action = report.engines.intentRisk.action.ifBlank { "sensitive information" }
            reasons += "they asked for $action"
        }
        if (report.engines.psychologicalPressure.detected) {
            val tactic = report.engines.psychologicalPressure.tactic.ifBlank { "pressure" }
            reasons += "they used $tactic tactics"
        }
        if (report.engines.informationAsymmetry.detected) {
            reasons += "they refused to verify their identity"
        }
        if (report.identityMismatch.detected) {
            reasons += "the call pattern doesn't match this contact's usual behavior"
        }
        if (report.syntheticConfidence >= 0.7f) {
            reasons += "the voice shows signs of being AI-generated"
        }

        val level = when {
            report.riskScore >= 85 -> "CRITICAL"
            report.riskScore >= 60 -> "HIGH"
            report.riskScore >= 40 -> "MEDIUM"
            else -> "LOW"
        }

        return if (reasons.isEmpty()) {
            "Risk is $level. No scam indicators detected yet."
        } else {
            "Risk is $level because ${reasons.joinToString(" and ")}."
        }
    }

    fun explainInHindi(report: RiskReport): String {
        if (report.plainReasoningHi.isNotBlank()) return report.plainReasoningHi
        // Fallback: return English if Hindi unavailable
        return explain(report)
    }
}
