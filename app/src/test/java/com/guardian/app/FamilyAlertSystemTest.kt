package com.guardian.app

import com.guardian.app.fcm.FamilyAlertEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class FamilyAlertSystemTest {

    @Test
    fun testFamilyAlertEntryDataModel() {
        val entry = FamilyAlertEntry(
            alertId = "1001",
            protectedUserName = "Mom",
            riskScore = 88,
            scamType = "OTP Request",
            callerNumber = "+919876543210",
            transcriptSummary = "Caller asked for OTP under bank threat",
            timestamp = System.currentTimeMillis()
        )

        assertEquals("Mom", entry.protectedUserName)
        assertEquals(88, entry.riskScore)
        assertEquals("OTP Request", entry.scamType)
        assertEquals("+919876543210", entry.callerNumber)
        assertNotNull(entry.transcriptSummary)
    }
}
