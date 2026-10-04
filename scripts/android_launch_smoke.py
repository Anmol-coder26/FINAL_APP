"""Install the distributed APK and check real Android activity startup/lifecycle."""
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path


PACKAGE = "com.guardian.app.submission"
REPORT = Path("app/build/reports/android-launch")
REPORT.mkdir(parents=True, exist_ok=True)


def adb(*args, check=True):
    return subprocess.run(
        ["adb", *args], check=check, capture_output=True, timeout=45
    ).stdout.decode(errors="replace")


def assert_alive(activity):
    assert adb("shell", "pidof", PACKAGE).strip(), "App process exited"
    state = adb("shell", "dumpsys", "activity", "activities")
    assert any(
        "ResumedActivity" in line and activity in line
        for line in state.splitlines()
    ), f"{activity} is not the foreground activity"


def snapshot(name):
    adb("shell", "uiautomator", "dump", "/sdcard/guardian-window.xml")
    xml = adb("shell", "cat", "/sdcard/guardian-window.xml")
    (REPORT / f"{name}.xml").write_text(xml)
    picture = subprocess.run(
        ["adb", "exec-out", "screencap", "-p"],
        capture_output=True, check=True, timeout=30,
    ).stdout
    (REPORT / f"{name}.png").write_bytes(picture)
    return ET.fromstring(xml)


def launch(activity, name, expected):
    result = adb("shell", "am", "start", "-W", "-n", f"{PACKAGE}/{activity}")
    (REPORT / f"{name}-launch.txt").write_text(result)
    assert "Error:" not in result, result
    time.sleep(4)
    assert_alive(activity)
    tree = snapshot(name)
    assert any(expected in node.get("text", "") for node in tree.iter()), (
        f"Expected screen text missing: {expected}"
    )
    print(f"PASS: {name}", flush=True)
    return tree


try:
    adb("install", "-r", "app/build/outputs/apk/debug/app-debug.apk")
    adb("shell", "input", "keyevent", "82")
    adb("logcat", "-c")
    adb("shell", "am", "force-stop", PACKAGE)
    launch("com.guardian.app.MainActivity", "fresh-launch", "Guardian Defense")
    launch("com.guardian.app.CallRiskActivity", "transcription-permission-not-granted", "Start live transcription")
    adb("shell", "am", "force-stop", PACKAGE)
    adb("shell", "pm", "grant", PACKAGE, "android.permission.RECORD_AUDIO")
    tree = launch("com.guardian.app.CallRiskActivity", "transcription-permission-granted", "Start live transcription")
    button = next(node for node in tree.iter() if node.get("text") == "Start live transcription")
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", button.get("bounds")))
    adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
    time.sleep(5)
    assert_alive("com.guardian.app.CallRiskActivity")
    snapshot("recognition-started")
    print("PASS: start transcription without an activity crash", flush=True)
    adb("shell", "input", "keyevent", "3")
    time.sleep(1)
    launch("com.guardian.app.CallRiskActivity", "transcription-resume", "Start live transcription")
    launch("com.guardian.app.MainActivity", "launcher-reopen", "Guardian Defense")
    time.sleep(4)
    assert_alive("com.guardian.app.MainActivity")
    crashes = adb("logcat", "-d", "-b", "crash")
    assert PACKAGE not in crashes, crashes
    (REPORT / "result.txt").write_text("PASS: install, launch, microphone permissions, listening start, pause/resume, reopen; no app crash.\n")
finally:
    (REPORT / "logcat.txt").write_text(adb("logcat", "-d", check=False))
    (REPORT / "crashes.txt").write_text(adb("logcat", "-d", "-b", "crash", check=False))
