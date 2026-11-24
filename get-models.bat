@echo off
chcp 65001 >nul
set D=%~dp0app\src\main\assets\www\models
if not exist "%D%" mkdir "%D%"
curl -L --fail -o "%D%\face_landmarker.task" "https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task"
curl -L --fail -o "%D%\hand_landmarker.task" "https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task"
dir "%D%"
echo Done. Now build the app in Android Studio: Build - Build APK.
pause
