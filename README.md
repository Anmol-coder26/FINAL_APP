# Guardian AI Defense (FINAL_APP)

Guardian is a real-time scam and fraud protection platform for Android, featuring dual-speaker VoIP audio analysis, Bhashini streaming speech-to-text in 10+ Indian languages, intelligent threat reasoning, zero-trust contact consistency checking, notification/SMS scam interception, and trusted contact alert dispatching.

---

## 🏗️ Architecture Overview

```
                          ┌───────────────────────────┐
                          │     Agora RTC Engine      │
                          │   (PCM 16kHz Streaming)   │
                          └─────────────┬─────────────┘
                                        │
                         AgoraFrameBridge (IAudioFrameObserver)
                         ┌──────────────┴──────────────┐
                         ▼                             ▼
             Local Audio (Microphone)       Remote Audio (Caller)
                         │                             │
                         ▼                             ▼
                Bhashini STT Client           Bhashini STT Client
                   (100ms Chunks)                (100ms Chunks)
                         │                             │
                         └──────────────┬──────────────┘
                                        │
                               DualSttController
                                        │
                                        ▼
                                LiveRiskAnalyzer
                       (2-second sliding window debounce)
                                        │
                                        ▼
                       SemanticAnalyzer & Reasoning Engine
                                        │
                         ┌──────────────┴──────────────┐
                         ▼                             ▼
              VoipCallViewModel / UI           Alert Dispatch
            - Live Pulsing Risk Ring       - Bhashini TTS (≥ 85%)
            - Dual Chat Bubbles            - Trusted Contact FCM (≥ 75%)
            - Reasoning Summary Card       - Call Drop Recommendation
```

---

## 🚀 Key Features

1. **Dual-Speaker VoIP Scam Defense (F1 - F5)**
   - Custom `AgoraFrameBridge` intercepts raw 16kHz uncompressed PCM frames for both local microphone and remote caller streams concurrently.
   - Resamples any frame rates (32kHz/48kHz) down to Linear PCM 16kHz 16-bit mono.
   - Feeds two parallel `BhashiniSttClient` instances via PUSH mode over WebSocket.
   - `LiveRiskAnalyzer` processes combined transcripts in a 2-second debounced sliding window using Gemini 1.5 Flash / on-device semantic fallback.

2. **VoIP Call UI & Threat Warning Engine (F6 - F8)**
   - Dynamic pulsing risk ring (Green < 40%, Yellow 40–74%, Red ≥ 75%).
   - Color-coded dual chat bubbles (User vs Caller) updating in real time.
   - Instant Bhashini TTS audio warning into local earpiece when scam probability exceeds 85%.
   - Push alert dispatch via backend to designated family/trusted contacts when threat exceeds 75%.
   - Persistent call records stored in Room database with risk scores, timestamps, and threat tags.

3. **Cellular Call Speakerphone Assist (F9 - F10)**
   - Android OS architectural boundary honesty: cellular audio cannot be tapped directly via standard APIs.
   - Guardian activates `CellularSpeakerphoneAssist` providing a clear notification and in-app instructions to switch the call to speakerphone, capturing audio via microphone for live Bhashini STT transcription and semantic fraud scanning.

4. **Multi-Channel Threat Protection Suite (F11 - F22)**
   - **Notification & SMS Interception**: Cancels suspicious incoming SMS notifications and replaces them with Guardian interception notifications directing to `ScamWarningActivity`.
   - **Zero-Trust Contact Consistency**: Detects impersonation attacks by flagging sudden phone number or voice characteristic deviations for known contacts.
   - **Voice Synthesis & AI Cloned Voice Detection**: Analyzes acoustic features for synthetic artifacts and robotic intonation.
   - **Plain English & Regional Language Reasoning**: Translates complex fraud heuristics into clear explanations in 10 Indian languages (Hindi, Tamil, Telugu, Bengali, Marathi, Kannada, Malayalam, Gujarati, Punjabi, English).
   - **Call Screening & Number Reputation**: Android `CallScreeningService` integrates local Room cache with external reputation lookups.
   - **Encrypted Session Management**: All tokens and identities secured via AndroidX `EncryptedSharedPreferences` with automatic migration.
   - **QR Code & Phishing Link Scanner**: CameraX + ML Kit Barcode Scanning with real-time domain risk evaluation.

5. **Backend Server (Node.js / Express)**
   - Merged Agora RTC dynamic token generation (`/api/agora/token`).
   - Trusted contact registration (`POST /family/register`).
   - High-priority FCM push alerts dispatch (`POST /alerts/trusted-contact`).

---

## 📦 APK Artifacts

- **Debug APK**: `artifacts/app-debug.apk`
  - Size: `209,959,297 bytes` (~200.2 MB)
  - SHA-256: `149F53ED18121FC1DD06E9225579747DDF1D066CC7744084122CDE3AD7DED490`
- **Release APK**: `artifacts/app-release.apk`
  - Size: `202,879,076 bytes` (~193.5 MB)
  - SHA-256: `076ABEA4E1790B54CAD0985AEAA45102A05B9718C0A60C0AC4CB7317B332C52C`

> **Note on App Size**: Guardian intentionally includes full native libraries (`armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64`) for Agora RTC, ML Kit Vision, CameraX, and full language packs to prioritize zero-setup offline reliability and cross-architecture compatibility.

---

## 🛠️ Building & Running

### Prerequisites
- JDK 17
- Android SDK 35 (Android 15) / Min SDK 26 (Android 8.0)
- Node.js 18+ (for token & alert server)

### Android App
```bash
# Build Debug APK
./gradlew assembleDebug

# Build Release APK
./gradlew assembleRelease
```

### Backend Token & Alert Server
```bash
cd server
npm install
node server.js
```
The server will start on port `3001` (or `PORT` specified in `.env`).

---

## 🧪 Verification Matrix Summary (T1 - T30)

| ID | Test Suite | Scope | Status |
|----|------------|-------|--------|
| T1–T5 | VoIP Audio & STT Pipeline | AgoraFrameBridge, Dual PUSH STT, Resampling | VERIFIED |
| T6–T8 | VoIP Call UI & State Machine | Pulsing ring, Chat bubbles, TTS & FCM dispatch | VERIFIED |
| T9–T10 | Cellular Speakerphone Assist | Honest banner, Mic capture fallback | VERIFIED |
| T11–T13 | Notification & SMS Interceptor | Notification cancellation, Intercept UI, Deep link | VERIFIED |
| T14–T16 | Advanced Protection Suite | Contact consistency, AI Voice clone, Plain reasoning | VERIFIED |
| T17–T20 | Call Screening & History | CallScreeningService, Room persistence, Cybercrime export | VERIFIED |
| T21–T24 | Security & Multi-language | EncryptedSharedPreferences, 10 Indian language packs | VERIFIED |
| T25–T28 | Backend Integration | Agora RTC token generation, FCM alert dispatch | VERIFIED |
| T29–T30 | Full End-to-End Build | `assembleDebug` & `assembleRelease` artifacts | VERIFIED |
