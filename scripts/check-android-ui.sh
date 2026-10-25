#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

ui_work=""
ui_pid=""
cleanup() {
    if [[ -n "$ui_pid" ]]; then
        kill "$ui_pid" 2>/dev/null || true
        wait "$ui_pid" 2>/dev/null || true
    fi
    if [[ -n "$ui_work" ]]; then
        rm -rf -- "$ui_work"
    fi
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

if [[ -z "${ANDROID_SERIAL:-}" ]]; then
    : "${ANDROID_UI_HOME:?Run through nix develop .#ui, or set ANDROID_SERIAL to an existing test device}"
    if [[ ! -r /dev/kvm || ! -w /dev/kvm ]]; then
        echo 'A disposable Android test device requires readable/writable /dev/kvm.' >&2
        exit 1
    fi
    ui_work="$(mktemp -d -t campsbridge-ui.XXXXXXXX)"
    export ANDROID_AVD_HOME="$ui_work/avd"
    mkdir -p "$ANDROID_AVD_HOME"
    avd_manager="$ANDROID_UI_HOME/cmdline-tools/latest/bin/avdmanager"
    if [[ ! -x "$avd_manager" ]]; then
        avd_manager="$(find "$ANDROID_UI_HOME/cmdline-tools" -path '*/bin/avdmanager' -print -quit)"
    fi
    if ! printf 'no\n' | ANDROID_HOME="$ANDROID_UI_HOME" ANDROID_SDK_ROOT="$ANDROID_UI_HOME" \
        "$avd_manager" create avd --name campsbridge-tests \
        --package 'system-images;android-35;google_apis;x86_64' --device pixel_5 \
        > "$ui_work/avd.log" 2>&1; then
        cat "$ui_work/avd.log" >&2
        exit 1
    fi
    ui_port="$(python3 - <<'PY'
import socket
for port in range(5580, 5680, 2):
    try:
        with socket.socket() as first, socket.socket() as second:
            first.bind(('127.0.0.1', port))
            second.bind(('127.0.0.1', port + 1))
        print(port)
        break
    except OSError:
        continue
else:
    raise SystemExit('No free emulator port pair')
PY
)"
    export ANDROID_SERIAL="emulator-$ui_port"
    ANDROID_HOME="$ANDROID_UI_HOME" ANDROID_SDK_ROOT="$ANDROID_UI_HOME" \
        "$ANDROID_UI_HOME/emulator/emulator" -avd campsbridge-tests -port "$ui_port" \
        -no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader -accel on \
        > "$ui_work/emulator.log" 2>&1 &
    ui_pid=$!
    ui_ready=false
    for ((attempt = 0; attempt < 180; attempt++)); do
        if [[ "$(adb -s "$ANDROID_SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == 1 ]]; then
            ui_ready=true
            break
        fi
        if ! kill -0 "$ui_pid" 2>/dev/null; then
            break
        fi
        sleep 2
    done
    if [[ "$ui_ready" != true ]]; then
        cat "$ui_work/avd.log" "$ui_work/emulator.log" >&2
        echo 'Android test device failed to boot.' >&2
        exit 1
    fi

    # First-run SDK setup can retain focus or show an ANR over the test app.
    # Provision only this disposable AVD; leave supplied devices unchanged.
    adb -s "$ANDROID_SERIAL" shell settings put global device_provisioned 1
    adb -s "$ANDROID_SERIAL" shell settings put secure user_setup_complete 1
    if [[ "$(adb -s "$ANDROID_SERIAL" shell pm path com.google.android.googlesdksetup)" == package:* ]]; then
        adb -s "$ANDROID_SERIAL" shell pm disable-user --user 0 com.google.android.googlesdksetup
    fi
    adb -s "$ANDROID_SERIAL" shell locksettings set-disabled true
    adb -s "$ANDROID_SERIAL" shell input keyevent KEYCODE_HOME
fi

adb -s "$ANDROID_SERIAL" get-state
adb -s "$ANDROID_SERIAL" shell svc power stayon true
adb -s "$ANDROID_SERIAL" shell input keyevent KEYCODE_WAKEUP
adb -s "$ANDROID_SERIAL" shell wm dismiss-keyguard
adb -s "$ANDROID_SERIAL" shell settings put global window_animation_scale 0
adb -s "$ANDROID_SERIAL" shell settings put global transition_animation_scale 0
adb -s "$ANDROID_SERIAL" shell settings put global animator_duration_scale 0
adb -s "$ANDROID_SERIAL" shell settings put secure show_ime_with_hard_keyboard 1
adb -s "$ANDROID_SERIAL" shell input keyevent 82

if [[ -n "$ui_pid" ]]; then
    # Boot completion precedes SystemUI readiness. Do not test behind its lock screen.
    ui_focused=false
    for ((attempt = 0; attempt < 60; attempt++)); do
        adb -s "$ANDROID_SERIAL" shell wm dismiss-keyguard
        adb -s "$ANDROID_SERIAL" shell input keyevent KEYCODE_HOME
        if adb -s "$ANDROID_SERIAL" shell dumpsys window | python3 -c '
import sys
state = sys.stdin.read()
focused_home = any(
    "mCurrentFocus=Window{" in line and "com.google.android.apps.nexuslauncher" in line
    for line in state.splitlines()
)
sys.exit(0 if focused_home and "isKeyguardShowing=false" in state else 1)
'; then
            ui_focused=true
            break
        fi
        sleep 2
    done
    if [[ "$ui_focused" != true ]]; then
        adb -s "$ANDROID_SERIAL" shell dumpsys window >&2
        echo 'Disposable Android device did not reach its unlocked launcher.' >&2
        exit 1
    fi
fi

# Remove only generated results so a stale report cannot satisfy this run.
results=app/build/outputs/androidTest-results/connected
rm -rf -- "$results"
# AGP can retain a prior instrumentation class filter in its configuration cache.
# Device verification must use this invocation's actual runner arguments.
bash ./gradlew --no-daemon --no-configuration-cache :app:connectedDebugAndroidTest "$@"
python3 - "$results" <<'PY'
import pathlib
import sys
import xml.etree.ElementTree as ET
reports = list(pathlib.Path(sys.argv[1]).rglob('TEST-*.xml'))
if not reports:
    raise SystemExit('No instrumented test reports were produced')
total = 0
for report in reports:
    suite = ET.parse(report).getroot()
    total += int(suite.get('tests', '0'))
    for attribute in ('failures', 'errors', 'skipped'):
        if int(suite.get(attribute, '0')):
            raise SystemExit(f'Instrumented suite contains {attribute}: {report}')
if total == 0:
    raise SystemExit('Instrumented suite executed no tests')
print(f'Instrumented verification passed: {total} tests')
PY
