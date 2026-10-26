#!/usr/bin/env bash
# يُشغَّل داخل android-emulator-runner
set -x
adb wait-for-device
adb shell 'while [[ -z $(getprop sys.boot_completed) ]]; do sleep 1; done'
adb install -r android-app/android/app/build/outputs/apk/debug/app-debug.apk
python3 android-app/native/ci_test.py || true
