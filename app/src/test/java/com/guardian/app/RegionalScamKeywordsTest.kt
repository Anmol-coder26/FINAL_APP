package com.guardian.app

import com.guardian.app.protect.RegionalScamKeywords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RegionalScamKeywordsTest {

    @Test
    fun testKeywordCountPerLanguage() {
        val keywordsByLang = RegionalScamKeywords.allKeywords.groupBy { it.language }

        assertEquals(10, keywordsByLang.keys.size)
        assertTrue((keywordsByLang["hi"]?.size ?: 0) >= 15)
        assertTrue((keywordsByLang["ta"]?.size ?: 0) >= 10)
        assertTrue((keywordsByLang["te"]?.size ?: 0) >= 10)
        assertTrue((keywordsByLang["bn"]?.size ?: 0) >= 10)
        assertTrue((keywordsByLang["mr"]?.size ?: 0) >= 10)
        assertTrue((keywordsByLang["kn"]?.size ?: 0) >= 10)
        assertTrue((keywordsByLang["ml"]?.size ?: 0) >= 10)
        assertTrue((keywordsByLang["pa"]?.size ?: 0) >= 10)
        assertTrue((keywordsByLang["gu"]?.size ?: 0) >= 10)
        assertTrue((keywordsByLang["en"]?.size ?: 0) >= 15)
    }

    @Test
    fun testHindiScamPhraseMatching() {
        val transcript = "आपका खाता बंद हो जाएगा, OTP बताइए"
        val matches = RegionalScamKeywords.match(transcript, "hi")
        val score = RegionalScamKeywords.score(transcript, "hi")

        assertTrue(matches.any { it.phrase == "OTP बताइए" })
        assertTrue(score >= 60)
    }

    @Test
    fun testTamilScamPhraseMatching() {
        val transcript = "உங்கள் கணக்கு மூடப்படும், OTP சொல்லுங்கள்"
        val matches = RegionalScamKeywords.match(transcript, "ta")
        val score = RegionalScamKeywords.score(transcript, "ta")

        assertTrue(matches.any { it.phrase == "OTP சொல்லுங்கள்" })
        assertTrue(score >= 60)
    }

    @Test
    fun testTeluguScamPhraseMatching() {
        val transcript = "మీ ఖాతా మూసివేయబడుతుంది, OTP చెప్పండి"
        val matches = RegionalScamKeywords.match(transcript, "te")
        val score = RegionalScamKeywords.score(transcript, "te")

        assertTrue(matches.any { it.phrase == "OTP చెప్పండి" })
        assertTrue(score >= 60)
    }

    @Test
    fun testBengaliScamPhraseMatching() {
        val transcript = "আপনার অ্যাকাউন্ট বন্ধ হয়ে যাবে, OTP বলুন"
        val matches = RegionalScamKeywords.match(transcript, "bn")
        val score = RegionalScamKeywords.score(transcript, "bn")

        assertTrue(matches.any { it.phrase == "OTP বলুন" })
        assertTrue(score >= 60)
    }

    @Test
    fun testMarathiScamPhraseMatching() {
        val transcript = "तुमचे खाते बंद होईल, OTP सांगा"
        val matches = RegionalScamKeywords.match(transcript, "mr")
        val score = RegionalScamKeywords.score(transcript, "mr")

        assertTrue(matches.any { it.phrase == "OTP सांगा" })
        assertTrue(score >= 60)
    }
}
