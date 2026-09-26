package com.guardian.app.protect.advanced

import android.content.Context
import androidx.room.Room
import com.guardian.app.callprotect.GuardianDatabase

object DemoSeed {
    // Called from settings debug menu only
    suspend fun seedDemoData(context: Context) {
        val db = GuardianDatabase.getInstance(context)
        db.contactProfileDao().upsert(
            ContactProfile(
                number = "+919999999999",
                displayName = "Mom",
                usualCallHoursStart = 18,
                usualCallHoursEnd = 22,
                usualDurationSec = 300,
                neverAsksForOtp = true,
                neverAsksForMoney = true,
                callCount = 47
            )
        )
        TrustedContactManager.save(
            context,
            TrustedContactManager.TrustedContact(
                name = "Family Member",
                phone = "+919876543210",
                enabled = true
            )
        )
    }
}
