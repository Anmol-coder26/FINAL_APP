import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")

}

// The downloadable demo installs beside the configured app, with its own data.
val submissionDemo = providers.gradleProperty("submissionDemo").map { it.toBoolean() }.getOrElse(false)

// Configured builds keep the existing Firebase configuration and package identity.
if (!submissionDemo && file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

val localProperties = Properties().apply {
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) {
        load(localFile.inputStream())
    }
}

android {
    namespace = "com.guardian.app"
    compileSdk = 35

    defaultConfig {
        applicationId = if (submissionDemo) "com.guardian.app.submission" else "com.guardian.app"
        manifestPlaceholders["appLabel"] = if (submissionDemo) "SuSagi Demo" else "@string/app_name"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        val geminiKey = localProperties.getProperty("GEMINI_API_KEY") ?: ""
        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiKey\"")

        val agoraAppId = localProperties.getProperty("AGORA_APP_ID")?.ifBlank { "d575bd8b35004ad896366419f3a8a8f1" } ?: "d575bd8b35004ad896366419f3a8a8f1"
        buildConfigField("String", "AGORA_APP_ID", "\"$agoraAppId\"")

        val agoraAppCert = localProperties.getProperty("AGORA_APP_CERTIFICATE") ?: ""
        buildConfigField("String", "AGORA_APP_CERTIFICATE", "\"$agoraAppCert\"")

        val bhashiniUserId = localProperties.getProperty("BHASHINI_USER_ID") ?: ""
        buildConfigField("String", "BHASHINI_USER_ID", "\"$bhashiniUserId\"")

        val bhashiniUlcaKey = localProperties.getProperty("BHASHINI_ULCA_API_KEY") ?: ""
        buildConfigField("String", "BHASHINI_ULCA_API_KEY", "\"$bhashiniUlcaKey\"")

        val bhashiniInferenceKey = localProperties.getProperty("BHASHINI_INFERENCE_API_KEY") ?: ""
        buildConfigField("String", "BHASHINI_INFERENCE_API_KEY", "\"$bhashiniInferenceKey\"")

        val bhashiniPipelineId = localProperties.getProperty("BHASHINI_PIPELINE_ID") ?: ""
        buildConfigField("String", "BHASHINI_PIPELINE_ID", "\"$bhashiniPipelineId\"")

        val bhashiniSttEndpoint = localProperties.getProperty("BHASHINI_STT_ENDPOINT") ?: ""
        buildConfigField("String", "BHASHINI_STT_ENDPOINT", "\"$bhashiniSttEndpoint\"")
        val bhashiniAsrServiceId = localProperties.getProperty("BHASHINI_ASR_SERVICE_ID") ?: ""
        buildConfigField("String", "BHASHINI_ASR_SERVICE_ID", "\"$bhashiniAsrServiceId\"")

        val bhashiniRestEndpoint = localProperties.getProperty("BHASHINI_REST_ENDPOINT") ?: ""
        buildConfigField("String", "BHASHINI_REST_ENDPOINT", "\"$bhashiniRestEndpoint\"")

        val backendUrl = localProperties.getProperty("BACKEND_URL") ?: "http://192.168.29.62:3001"
        buildConfigField("String", "BACKEND_URL", "\"$backendUrl\"")
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    if (submissionDemo) {
        System.getenv("SUSAGI_DEMO_KEYSTORE")?.takeIf { it.isNotBlank() }?.let {
            signingConfigs.getByName("debug").storeFile = file(it)
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = true
            pickFirsts.addAll(listOf(
                "**/libaosl.so",
                "**/libc++_shared.so",
                "lib/**/libaosl.so",
                "lib/arm64-v8a/libaosl.so",
                "lib/armeabi-v7a/libaosl.so",
                "lib/x86/libaosl.so",
                "lib/x86_64/libaosl.so"
            ))
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    
    // Agora Voice & Audio RTC SDK
    implementation("io.agora.rtc:voice-sdk:4.4.1")
    // Agora RTM (Signaling) SDK for Real-Time Transcripts
    implementation("io.agora.rtm:rtm-sdk:2.2.1")

    // CameraX & ML Kit for QR / Barcode Scanner
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    implementation("com.google.mlkit:barcode-scanning:17.2.0")

    // Encrypted Session Storage
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Room Database & Coroutines for Number Reputation & Call History
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // OkHttp for Bhashini Streaming STT WebSocket & REST Translation/TTS
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.socket:socket.io-client:2.1.0") {
        exclude(group = "org.json", module = "json")
    }

    // Firebase Cloud Messaging
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-messaging-ktx")

    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("org.mockito:mockito-core:5.12.0")
    testImplementation("org.json:json:20231013")
}
