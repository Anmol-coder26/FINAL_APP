package com.guardian.app.protect

object RegionalScamKeywords {

    data class ScamKeyword(
        val phrase: String,
        val language: String,        // ISO code: hi, ta, te, bn, mr, kn, ml, pa, gu, en
        val weight: Int,             // 10 = mild, 30 = high risk, 50 = critical
        val category: String         // "otp_request", "account_threat", "authority_impersonation", "urgency", "money_transfer", "kyc_request", "greed"
    )

    val allKeywords: List<ScamKeyword> = listOf(

        // ============ HINDI (hi) ============
        ScamKeyword("OTP बताओ", "hi", 40, "otp_request"),
        ScamKeyword("OTP बताइए", "hi", 40, "otp_request"),
        ScamKeyword("ओटीपी शेयर करें", "hi", 40, "otp_request"),
        ScamKeyword("OTP भेजो", "hi", 40, "otp_request"),
        ScamKeyword("अकाउंट बंद हो जाएगा", "hi", 35, "account_threat"),
        ScamKeyword("खाता बंद", "hi", 30, "account_threat"),
        ScamKeyword("CBI से बोल रहा हूँ", "hi", 45, "authority_impersonation"),
        ScamKeyword("पुलिस से बोल रहा हूँ", "hi", 45, "authority_impersonation"),
        ScamKeyword("डिजिटल अरेस्ट", "hi", 50, "authority_impersonation"),
        ScamKeyword("गिरफ्तार हो जाएंगे", "hi", 45, "urgency"),
        ScamKeyword("तुरंत करें", "hi", 30, "urgency"),
        ScamKeyword("KYC अपडेट करें", "hi", 30, "kyc_request"),
        ScamKeyword("पैसे ट्रांसफर करें", "hi", 40, "money_transfer"),
        ScamKeyword("यूपीआई पिन", "hi", 35, "otp_request"),
        ScamKeyword("लॉटरी", "hi", 25, "greed"),
        ScamKeyword("इनाम", "hi", 20, "greed"),
        ScamKeyword("बैंक से बोल रहा हूँ", "hi", 30, "authority_impersonation"),

        // ============ TAMIL (ta) ============
        ScamKeyword("OTP சொல்லுங்கள்", "ta", 40, "otp_request"),
        ScamKeyword("OTP சொல்லு", "ta", 40, "otp_request"),
        ScamKeyword("கணக்கு முடிகிறது", "ta", 35, "account_threat"),
        ScamKeyword("கணக்கு மூடப்படும்", "ta", 35, "account_threat"),
        ScamKeyword("வங்கியில் இருந்து", "ta", 30, "authority_impersonation"),
        ScamKeyword("போலீஸ்", "ta", 45, "authority_impersonation"),
        ScamKeyword("கைது", "ta", 45, "urgency"),
        ScamKeyword("உடனே", "ta", 30, "urgency"),
        ScamKeyword("பணம் அனுப்புங்கள்", "ta", 40, "money_transfer"),
        ScamKeyword("KYC புதுப்பிக்க", "ta", 30, "kyc_request"),
        ScamKeyword("லாட்டரி", "ta", 25, "greed"),
        ScamKeyword("பரிசு", "ta", 20, "greed"),
        ScamKeyword("ATM", "ta", 25, "otp_request"),

        // ============ TELUGU (te) ============
        ScamKeyword("OTP చెప్పండి", "te", 40, "otp_request"),
        ScamKeyword("OTP చెప్పు", "te", 40, "otp_request"),
        ScamKeyword("ఖాతా బ్లాక్ అవుతుంది", "te", 35, "account_threat"),
        ScamKeyword("ఖాతా మూసివేయబడుతుంది", "te", 35, "account_threat"),
        ScamKeyword("బ్యాంక్ నుండి", "te", 30, "authority_impersonation"),
        ScamKeyword("పోలీస్", "te", 45, "authority_impersonation"),
        ScamKeyword("అరెస్ట్", "te", 45, "urgency"),
        ScamKeyword("వెంటనే", "te", 30, "urgency"),
        ScamKeyword("డబ్బు పంపండి", "te", 40, "money_transfer"),
        ScamKeyword("KYC అప్డేట్", "te", 30, "kyc_request"),
        ScamKeyword("లాటరీ", "te", 25, "greed"),
        ScamKeyword("బహుమతి", "te", 20, "greed"),

        // ============ BENGALI (bn) ============
        ScamKeyword("OTP বলুন", "bn", 40, "otp_request"),
        ScamKeyword("OTP দিন", "bn", 40, "otp_request"),
        ScamKeyword("অ্যাকাউন্ট বন্ধ হয়ে যাবে", "bn", 35, "account_threat"),
        ScamKeyword("অ্যাকাউন্ট ব্লক", "bn", 30, "account_threat"),
        ScamKeyword("ব্যাংক থেকে", "bn", 30, "authority_impersonation"),
        ScamKeyword("পুলিশ", "bn", 45, "authority_impersonation"),
        ScamKeyword("গ্রেপ্তার", "bn", 45, "urgency"),
        ScamKeyword("সাথে সাথে", "bn", 30, "urgency"),
        ScamKeyword("টাকা পাঠান", "bn", 40, "money_transfer"),
        ScamKeyword("KYC আপডেট", "bn", 30, "kyc_request"),
        ScamKeyword("লটারি", "bn", 25, "greed"),
        ScamKeyword("পুরস্কার", "bn", 20, "greed"),

        // ============ MARATHI (mr) ============
        ScamKeyword("OTP सांगा", "mr", 40, "otp_request"),
        ScamKeyword("OTP द्या", "mr", 40, "otp_request"),
        ScamKeyword("खाते बंद होईल", "mr", 35, "account_threat"),
        ScamKeyword("खातं बंद", "mr", 30, "account_threat"),
        ScamKeyword("बँकेतून", "mr", 30, "authority_impersonation"),
        ScamKeyword("पोलीस", "mr", 45, "authority_impersonation"),
        ScamKeyword("अटक", "mr", 45, "urgency"),
        ScamKeyword("लगेच", "mr", 30, "urgency"),
        ScamKeyword("पैसे पाठवा", "mr", 40, "money_transfer"),
        ScamKeyword("KYC अपडेट", "mr", 30, "kyc_request"),
        ScamKeyword("लॉटरी", "mr", 25, "greed"),
        ScamKeyword("बक्षीस", "mr", 20, "greed"),

        // ============ KANNADA (kn) ============
        ScamKeyword("OTP ಹೇಳಿ", "kn", 40, "otp_request"),
        ScamKeyword("OTP ಕೊಡಿ", "kn", 40, "otp_request"),
        ScamKeyword("ಖಾತೆ ಬಂದ್ ಆಗುತ್ತದೆ", "kn", 35, "account_threat"),
        ScamKeyword("ಖಾತೆ ಬ್ಲಾಕ್", "kn", 30, "account_threat"),
        ScamKeyword("ಬ್ಯಾಂಕ್ನಿಂದ", "kn", 30, "authority_impersonation"),
        ScamKeyword("ಪೊಲೀಸ್", "kn", 45, "authority_impersonation"),
        ScamKeyword("ಬಂಧನ", "kn", 45, "urgency"),
        ScamKeyword("ತಕ್ಷಣ", "kn", 30, "urgency"),
        ScamKeyword("ಹಣ ಕಳುಹಿಸಿ", "kn", 40, "money_transfer"),
        ScamKeyword("KYC ಅಪ್ಡೇಟ್", "kn", 30, "kyc_request"),
        ScamKeyword("ಲಾಟರಿ", "kn", 25, "greed"),
        ScamKeyword("ಬಹುಮಾನ", "kn", 20, "greed"),

        // ============ MALAYALAM (ml) ============
        ScamKeyword("OTP പറയൂ", "ml", 40, "otp_request"),
        ScamKeyword("OTP തരൂ", "ml", 40, "otp_request"),
        ScamKeyword("അക്കൗണ്ട് ബ്ലോക്ക് ആകും", "ml", 35, "account_threat"),
        ScamKeyword("അക്കൗണ്ട് അടയ്ക്കും", "ml", 30, "account_threat"),
        ScamKeyword("ബാങ്കിൽ നിന്ന്", "ml", 30, "authority_impersonation"),
        ScamKeyword("പോലീസ്", "ml", 45, "authority_impersonation"),
        ScamKeyword("അറസ്റ്റ്", "ml", 45, "urgency"),
        ScamKeyword("ഉടനെ", "ml", 30, "urgency"),
        ScamKeyword("പണം അയക്കൂ", "ml", 40, "money_transfer"),
        ScamKeyword("KYC അപ്ഡേറ്റ്", "ml", 30, "kyc_request"),
        ScamKeyword("ലോട്ടറി", "ml", 25, "greed"),
        ScamKeyword("സമ്മാനം", "ml", 20, "greed"),

        // ============ PUNJABI (pa) ============
        ScamKeyword("OTP ਦੱਸੋ", "pa", 40, "otp_request"),
        ScamKeyword("OTP ਦਿਓ", "pa", 40, "otp_request"),
        ScamKeyword("ਖਾਤਾ ਬੰਦ ਹੋ ਜਾਵੇਗਾ", "pa", 35, "account_threat"),
        ScamKeyword("ਖਾਤਾ ਬਲੌਕ", "pa", 30, "account_threat"),
        ScamKeyword("ਬੈਂਕ ਤੋਂ", "pa", 30, "authority_impersonation"),
        ScamKeyword("ਪੁਲਿਸ", "pa", 45, "authority_impersonation"),
        ScamKeyword("ਗ੍ਰਿਫਤਾਰ", "pa", 45, "urgency"),
        ScamKeyword("ਤੁਰੰਤ", "pa", 30, "urgency"),
        ScamKeyword("ਪੈਸੇ ਭੇਜੋ", "pa", 40, "money_transfer"),
        ScamKeyword("KYC ਅਪਡੇਟ", "pa", 30, "kyc_request"),
        ScamKeyword("ਲਾਟਰੀ", "pa", 25, "greed"),
        ScamKeyword("ਇਨਾਮ", "pa", 20, "greed"),

        // ============ GUJARATI (gu) ============
        ScamKeyword("OTP આપો", "gu", 40, "otp_request"),
        ScamKeyword("OTP કહો", "gu", 40, "otp_request"),
        ScamKeyword("ખાતું બંધ થઈ જશે", "gu", 35, "account_threat"),
        ScamKeyword("ખાતું બ્લોક", "gu", 30, "account_threat"),
        ScamKeyword("બેંકમાંથી", "gu", 30, "authority_impersonation"),
        ScamKeyword("પોલીસ", "gu", 45, "authority_impersonation"),
        ScamKeyword("ધરપકડ", "gu", 45, "urgency"),
        ScamKeyword("તરત", "gu", 30, "urgency"),
        ScamKeyword("પૈસા મોકલો", "gu", 40, "money_transfer"),
        ScamKeyword("KYC અપડેટ", "gu", 30, "kyc_request"),
        ScamKeyword("લોટરી", "gu", 25, "greed"),
        ScamKeyword("ઇનામ", "gu", 20, "greed"),

        // ============ ENGLISH (en) ============
        ScamKeyword("OTP", "en", 40, "otp_request"),
        ScamKeyword("CVV", "en", 40, "otp_request"),
        ScamKeyword("PIN", "en", 35, "otp_request"),
        ScamKeyword("password", "en", 40, "otp_request"),
        ScamKeyword("account will be blocked", "en", 35, "account_threat"),
        ScamKeyword("account blocked", "en", 30, "account_threat"),
        ScamKeyword("CBI", "en", 45, "authority_impersonation"),
        ScamKeyword("police", "en", 45, "authority_impersonation"),
        ScamKeyword("digital arrest", "en", 50, "authority_impersonation"),
        ScamKeyword("arrest", "en", 45, "urgency"),
        ScamKeyword("immediately", "en", 30, "urgency"),
        ScamKeyword("KYC update", "en", 30, "kyc_request"),
        ScamKeyword("transfer money", "en", 40, "money_transfer"),
        ScamKeyword("UPI PIN", "en", 35, "otp_request"),
        ScamKeyword("lottery", "en", 25, "greed"),
        ScamKeyword("prize", "en", 20, "greed"),
        ScamKeyword("bank", "en", 25, "authority_impersonation")
    )

    fun match(
        transcript: String,
        language: String
    ): List<ScamKeyword> {
        val normalized = transcript.lowercase()
        return allKeywords.filter { keyword ->
            (keyword.language == language || keyword.language == "en") &&
            normalized.contains(keyword.phrase.lowercase())
        }
    }

    fun score(
        transcript: String,
        language: String
    ): Int {
        val matches = match(transcript, language)
        return matches.sumOf { it.weight }.coerceAtMost(98)
    }

    fun categories(
        transcript: String,
        language: String
    ): List<String> {
        return match(transcript, language).map { it.category }.distinct()
    }
}
