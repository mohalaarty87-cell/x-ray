# تطبيق مرآة الأشعة السينية (Android، يعمل بلا إنترنت)

كل المكتبات مضمّنة داخل المشروع في `app/src/main/assets/www/`:
- `lib/three.module.js` (Three.js 0.160.0)
- `lib/mediapipe/` (MediaPipe Tasks Vision 0.10.14 + ملفات WASM)
- `fonts/` (خط Tajawal)
- `models/` ← **نموذجا MediaPipe (face_landmarker.task و hand_landmarker.task)** ويجب أن يكونا هنا ليعمل التطبيق دون إنترنت.

## الطريقة 1: Android Studio (على كمبيوتر)
1. شغّل `get-models.bat` (ويندوز) أو `./get-models.sh` (ماك/لينكس) مرة واحدة وأنت متصل بالإنترنت.
2. افتح مجلد المشروع في Android Studio (Hedgehog أو أحدث) وانتظر مزامنة Gradle.
3. Build ← Build Bundle(s) / APK(s) ← Build APK(s).
4. الملف في: `app/build/outputs/apk/debug/app-debug.apk` ثم انقله إلى الهاتف وثبّته (فعّل «التثبيت من مصادر غير معروفة»).

## الطريقة 2: GitHub Actions (دون تثبيت شيء)
1. أنشئ مستودعًا خاصًا على GitHub وارفع محتويات هذا المجلد كما هي.
2. تبويب Actions ← Build APK ← Run workflow.
3. بعد دقائق نزّل `xray-mirror-apk` من Artifacts وفكّ ضغطه وثبّت الـ APK.

## ملاحظات
- لو لم تضع النموذجين داخل `models/` فسيحاول التطبيق تنزيلهما عند أول تشغيل بوجود إنترنت، ثم يعملان دون إنترنت بعد ذلك.
- الحد الأدنى: Android 10 (API 29). التطبيق يطلب إذن الكاميرا عند أول «ابدأ المرآة».
- التسجيل والصور تُحفظ في: التنزيلات/XRayMirror.
- تراخيص: Three.js (MIT)، MediaPipe (Apache-2.0)، خط Tajawal (OFL-1.1).
- يمكنك أيضًا تجربة الصفحة على الكمبيوتر: شغّل `python3 -m http.server` داخل `app/src/main/assets/www` وافتح `http://localhost:8000`.
