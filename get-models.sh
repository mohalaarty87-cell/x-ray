#!/usr/bin/env bash
# ينزّل نموذجي MediaPipe داخل التطبيق ليعمل بلا إنترنت (يُشغَّل مرة واحدة على جهاز متصل)
set -e
D="$(cd "$(dirname "$0")" && pwd)/app/src/main/assets/www/models"
mkdir -p "$D"
curl -L --fail -o "$D/face_landmarker.task" "https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task"
curl -L --fail -o "$D/hand_landmarker.task" "https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task"
ls -l "$D"
echo "تم. الآن ابنِ التطبيق (Android Studio ← Build ← Build APK)."
