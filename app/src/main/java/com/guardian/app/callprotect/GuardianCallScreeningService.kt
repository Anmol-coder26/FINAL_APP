package com.guardian.app.callprotect

import android.telecom.Call
import android.telecom.CallScreeningService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GuardianCallScreeningService : CallScreeningService() {
    override fun onScreenCall(callDetails: Call.Details) {
        val number = callDetails.handle?.schemeSpecificPart ?: return
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            val repo = NumberReputationRepository(applicationContext)
            val flagged = repo.lookup(number)
            val shouldBlock = flagged?.riskLevel in listOf("SPAM", "HIGH_RISK", "SCAM")
            val autoBlockEnabled = AutoBlockPrefs.isEnabled(applicationContext)

            val response = CallResponse.Builder()
                .setDisallowCall(shouldBlock && autoBlockEnabled)
                .setRejectCall(shouldBlock && autoBlockEnabled)
                .setSilenceCall(shouldBlock)
                .setSkipCallLog(false)
                .setSkipNotification(false)
                .build()

            respondToCall(callDetails, response)

            if (shouldBlock) {
                CallLogStore.record(applicationContext, number, "blocked_by_guardian")
            }
        }
    }
}
