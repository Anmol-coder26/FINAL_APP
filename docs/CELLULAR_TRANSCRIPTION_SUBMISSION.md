# SuSagi live speakerphone transcription

Independent repository: `Anmol-coder26/FINAL_APP`.
Branch: `feat/cellular-live-transcription`. This work does not depend on the Person 1/2/3 branches.

## Prototype claim

SuSagi transcribes audible speech from a speakerphone conversation while its listening screen is visible. It displays interim recognition text, retains final segments, and submits final conversation context to the existing risk analyzer. It uses one microphone stream, so it does not identify which participant spoke.

Same-device SIM-call capture is device dependent and can be silenced by Android. Speakerphone does not override that restriction. Use a second listening device for the submission demonstration if the phone carrying the call blocks microphone access. The second device hears a real, ongoing call acoustically; this is not simulated transcription.

## Build

Windows, in this repository:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest --tests com.guardian.app.LiveTranscriptBufferTest
```

macOS/Linux:

```sh
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest --tests com.guardian.app.LiveTranscriptBufferTest
```

Use JDK 17 and Android SDK platform 35. Firebase features require your own `app/google-services.json`; it is optional for this speech-listening build. No credentials are included. The branch workflow builds **SuSagi Demo** (`com.guardian.app.submission`) with `-PsubmissionDemo=true`. It installs beside the existing app and keeps separate data, permissions, and notification access. Download the [launch-checked APK directly](https://github.com/Anmol-coder26/FINAL_APP/releases/download/susagi-demo-5/SuSagi-Demo.apk); no ZIP extraction or GitHub sign-in is needed. Future successful builds publish a prerelease with a directly downloadable `SuSagi-Demo.apk`. The earlier APK/QR from run 37187012635 used the original package name; use the separate demo for testing instead. See [installation instructions](DEMO_DOWNLOAD.md).

A normal local build without `-PsubmissionDemo=true` retains `com.guardian.app`, your existing app name, and your ignored configuration files. Cloud features require your own current `local.properties`, backend deployment, and Firebase configuration. The downloadable demo has no private keys and cannot establish that those integrations work. Build with your existing configuration and verify each integration before updating the installed app. Do not uninstall the existing app to install a demo.

The listening device needs a working Android speech recognition service (e.g. an enabled Google speech service). Provider language support, partial-result availability, network needs, and gaps between recognition sessions vary. This is near-real-time transcription, not a promise of lossless or zero-latency streaming.

## Room calls and BHASHINI configuration

Open **Secure VoIP** from Home, then **Call setup**. Save the reachable call-server address, BHASHINI Socket.IO address, inference key, and provisioned ASR service ID. Settings are encrypted on the device and can also be used by the separate demo APK. The server's `APP_ID` must match the Android build's `AGORA_APP_ID`; the certificate stays on the call server.

Phone A chooses **Create room** and shares the six-digit code. Phone B enters that code and chooses **Join call**. The shared channel is `gx_<code>`. There is no automatically joined fixed room. Permission denial keeps the lobby open; failed joins show an error and allow another attempt. Cancel and End release the observer, speech sessions, collectors, foreground service, and call.

Room speech comes from Agora's `RECORD` and `BEFORE_MIXING` PCM callbacks. Local and remote streams have separate BHASHINI PUSH sessions and **You** / **Caller** transcript rows. The controller never starts a second microphone recorder or native recognizer alongside Agora. These labels describe audio identity; they are not claims that a participant is a scammer.

For a configured build, these entries can instead be set in ignored root `local.properties`:

```properties
BHASHINI_STT_ENDPOINT=https://YOUR_PROVISIONED_SOCKET_IO_SERVER
BHASHINI_INFERENCE_API_KEY=YOUR_KEY
BHASHINI_ASR_SERVICE_ID=YOUR_PROVISIONED_ASR_SERVICE_ID
BACKEND_URL=https://YOUR_CALL_SERVER
```

The client uses Socket.IO 2.1.0 with `auth.Authorization`, emits the documented task sequence and streaming configuration, and waits for the server's **ready** event before transmitting audio. It follows the [official BHASHINI guide](https://dibd-bhashini.gitbook.io/bhashini-apis/websocket-asr-api) and its [Java client](https://github.com/bhashini-dibd/bhashini-websockets/blob/main/Java/web-socket-client/src/main/java/client/web_socket_client/ULCASocketClient.java). Agora/microphone capture is mono PCM16 at 16 kHz; the adapter resamples to the documented 8 kHz format and sends 100 ms binary chunks in the Socket.IO `data` event. A one-second response frequency is requested; real provider latency has not been measured.

Socket connection, task readiness, and received PCM are distinct states. The room screen displays them separately. A connected socket alone does not claim successful recognition. Parsed interim text evolves in one row per speaker; a final replaces that row and preserves bounded conversation history. Unknown final flags remain interim. Real service responses must be checked against the provisioned service before submission.

In the standalone speakerphone listening mode, missing configuration or connection failure selects Android recognition after releasing the BHASHINI recorder. Room calls report BHASHINI failure without opening a competing native recognizer. Do not claim real BHASHINI transcription or a working two-device call solely from compilation or synthetic integration tests.

For online reasoning configure `GEMINI_API_KEY` separately. The existing analyzer has a local fallback. Keyword checks run immediately, while semantic requests run away from transcript collection so a slow request does not block live text. Cloud semantic analysis and family-alert delivery still require real service configuration and verification. Do not publish `local.properties` or credentials.

CI uses an explicitly labelled Socket.IO **test fixture** to exercise both PUSH sessions and the standalone microphone-to-screen path on Android. Its `TEST FIXTURE` responses are synthetic; they verify binary transport, session readiness, speaker mapping, partial/final updates, and cleanup rather than ASR accuracy. The fixture is excluded from the distributed application and is not a fallback speech provider.

## Live call demonstration

Incoming-call detection posts an ordinary notification. It does not start a microphone foreground service from the background, which can be rejected by newer Android versions. Tap the notification, then Start, or open Live Defense manually if notifications are disabled.

1. Install SuSagi Demo on the listening device, keeping the current SuSagi app installed. Enable its speech service and grant microphone permission.
2. Open Live Defense. Select Hindi or English before starting.
3. Make/answer a real call on the call phone and enable speakerphone. Tell participants the conversation will be transcribed by a speech provider.
4. First try `Mode: call on this phone` with SuSagi visible on the call phone. Tap `Start live transcription`. Speak ordinary sentences and confirm actual words appear.
5. If Android blocks capture, stop. Open SuSagi on another Android device placed beside the call phone, choose `Mode: call on another device`, and start there. A separate caller can use a third phone, or call from a computer.
6. Say: “Hello, I am calling about your account.” Then, using a scripted test conversation: “Please share your OTP immediately.” Never use an actual OTP or personal information.
7. Show the live utterance changing as recognition arrives, followed by final conversation history and the analyzer's explanation. Measure first-text delay with a screen recording; do not invent a latency number.
8. Tap Stop at the end. Switching apps pauses this foreground listening mode. Returning does not silently restart the microphone. Companion mode requires manual Stop because the listening device cannot observe the other phone's call end.

Keep scripted text simulations separate from live capture. They are labeled as simulated and do not prove microphone or ASR functionality.

## Acceptance before submission

- A real speakerphone sentence produces live words in the selected language.
- Interim text is replaced, not appended repeatedly; final text appears once in context.
- Missing BHASHINI config uses Android recognition and reports the actual provider.
- Denied permission, missing speech service, disconnection, and quiet/blocked capture are visible.
- Stop, app switch, language change, and repeated start do not leave competing recorders.
- Same-device call end stops an observed active call; companion mode does not stop on the listener's idle telephony state.
- The assembled APK and real-device live-call recording have been checked. Compilation alone does not establish cellular capture support.

## Accurate submission wording

“SuSagi's prototype provides live speakerphone-assisted transcription and scam-risk explanation. It can listen on the call device where Android permits microphone access, or on a nearby companion device when cellular-call audio restrictions prevent capture. BHASHINI integration is available for a provisioned compatible streaming service; Android speech recognition provides a fallback.”

## Verification recorded on 4 October 2026

[Run 37204965295](https://github.com/Anmol-coder26/FINAL_APP/actions/runs/37204965295) passed the existing 17 unit tests and the actual APK's Android 15 emulator launch check: fresh launcher, transcription before microphone permission, transcription after granting permission, recognition start without a crash, pause/resume, and launcher reopen. Its screenshots, UI dumps, and Android logs are in `android-launch-check`. The reported phone-specific opening failure was not reproduced by that check; no startup-crash fix is claimed. The physical phone model, Android version, and exact failure remain needed if this APK still fails there. Real cellular call transcription and private cloud integrations still require device verification before final submission.
