Download **SuSagi-Demo.apk** below directly on your Android phone, then open the downloaded file to install **SuSagi Demo**. Android 8.0 or newer is required. Allow installation from your browser when Android asks.

This demo installs separately from your configured SuSagi app. If an older **SuSagi Demo** says the update has a different signature, uninstall only that demo before installing this build. That removes the demo's local data.

The published APK passes an Android 15 emulator check for installation, launch, opening transcription with and without microphone permission, starting recognition, leaving and returning to the screen, and reopening the launcher. Unit tests are also run before publication. These checks do not reproduce every physical phone or verify real cellular call audio.

For live transcription, open the call transcription screen, allow microphone access, select the language, and tap **Start live transcription**. An installed Android speech recognition provider is required. For the most reliable cellular-call setup, use **call on another device** and place the listening device next to a speakerphone call. Same-device call audio depends on Android's microphone restrictions. The screen displays provider errors if recognition is unavailable.

This is a demonstration build, with no private Firebase, BHASHINI, Gemini, or backend credentials. Those integrations require the configured app build for final submission.
