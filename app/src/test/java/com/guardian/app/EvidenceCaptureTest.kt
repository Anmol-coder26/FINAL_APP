package com.guardian.app

import com.guardian.app.evidence.EvidencePdfGenerator
import org.junit.Assert.assertNotNull
import org.junit.Test

class EvidenceCaptureTest {

    @Test
    fun testEvidenceModuleClassesExist() {
        assertNotNull(EvidencePdfGenerator::class.java)
        assertNotNull(com.guardian.app.evidence.EvidenceSharer::class.java)
    }
}
