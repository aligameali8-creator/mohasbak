#!/usr/bin/env bash
# توقيع ملف AAB بمفتاح الرفع الخاص. الاستخدام: tools/sign_aab.sh <ملف.aab> <كلمة السر>
# المفتاح محفوظ مشفّراً في android-app/keystore/upload.jks.enc (فكّه بكلمة السر نفسها)
set -euo pipefail
AAB="$1"; PW="$2"; DIR=$(cd "$(dirname "$0")/.." && pwd); TMP=$(mktemp -d)
openssl enc -d -aes-256-cbc -pbkdf2 -iter 200000 -in "$DIR/android-app/keystore/upload.jks.enc" -out "$TMP/k.jks" -pass pass:"$PW"
jarsigner -sigalg SHA256withRSA -digestalg SHA-256 -keystore "$TMP/k.jks" -storepass "$PW" "$AAB" mohasbak-upload
jarsigner -verify "$AAB" | grep -q "jar verified" && echo "✔ وُقّع: $AAB"
rm -rf "$TMP"
