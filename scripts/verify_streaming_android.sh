#!/usr/bin/env bash
set +e
gradle :app:connectedDebugAndroidTest -PsubmissionDemo=true --stacktrace
streaming_test_status=$?
mkdir -p app/build/reports/streaming-diagnostics
adb logcat -d > app/build/reports/streaming-diagnostics/logcat.txt
adb pull /sdcard/Android/data/com.guardian.app.submission/files/streaming-diagnostics app/build/reports/streaming-diagnostics/ >/dev/null 2>&1
exit "$streaming_test_status"
