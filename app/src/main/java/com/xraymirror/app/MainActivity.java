package com.xraymirror.app;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * يعرض صفحة المرآة من مجلد assets/www عبر عنوان https وهمي، فتعمل الوحدات (ES modules)
 * وملفات WASM والنماذج دون إنترنت، وتُسمح الكاميرا للصفحة نفسها فقط.
 */
public class MainActivity extends Activity {

    private static final String HOST = "appassets.androidplatform.net";
    private static final int REQ_CAMERA = 77;
    private static final String FACE_URL =
            "https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task";
    private static final String HAND_URL =
            "https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task";

    private WebView web;
    private PermissionRequest pendingPermission;

    // حالة حفظ الملفات القادمة من الصفحة (تسجيل الفيديو / الصورة)
    private Uri saveUri;
    private OutputStream saveOut;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        web = new WebView(this);
        setContentView(web);

        WebSettings st = web.getSettings();
        st.setJavaScriptEnabled(true);
        st.setDomStorageEnabled(true);
        st.setMediaPlaybackRequiresUserGesture(false);
        st.setAllowFileAccess(false);
        st.setAllowContentAccess(false);
        st.setCacheMode(WebSettings.LOAD_DEFAULT);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);

        web.addJavascriptInterface(new Bridge(), "AndroidBridge");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if (u != null && "https".equals(u.getScheme()) && HOST.equals(u.getHost())) {
                    return serve(u.getPath(), "HEAD".equalsIgnoreCase(request.getMethod()));
                }
                return null;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                return !(u != null && HOST.equals(u.getHost()));
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> handlePermission(request));
            }

            @Override
            public void onPermissionRequestCanceled(PermissionRequest request) {
                if (pendingPermission == request) pendingPermission = null;
            }
        });

        web.loadUrl("https://" + HOST + "/index.html");
    }

    // ---------------- الأذونات ----------------

    private void handlePermission(PermissionRequest request) {
        if (request.getOrigin() == null || !HOST.equals(request.getOrigin().getHost())) {
            request.deny();
            return;
        }
        boolean wantsVideo = false;
        for (String r : request.getResources()) {
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r)) wantsVideo = true;
        }
        if (!wantsVideo) {
            request.deny();
            return;
        }
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            request.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
        } else {
            pendingPermission = request;
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_CAMERA || pendingPermission == null) return;
        PermissionRequest req = pendingPermission;
        pendingPermission = null;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            req.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
        } else {
            req.deny();
        }
    }

    // ---------------- تقديم الملفات من assets ----------------

    private WebResourceResponse serve(String path, boolean headOnly) {
        if (path == null || path.isEmpty() || "/".equals(path)) path = "/index.html";
        if (path.contains("..")) return notFound();
        String rel = path.substring(1);
        try {
            InputStream in;
            try {
                in = getAssets().open("www/" + rel);
            } catch (IOException e) {
                if (rel.startsWith("models/")) {
                    File f = ensureModel(rel.substring("models/".length()));
                    if (f == null) return notFound();
                    in = new FileInputStream(f);
                } else {
                    return notFound();
                }
            }
            if (headOnly) {
                in.close();
                in = new ByteArrayInputStream(new byte[0]);
            }
            String mime = mimeOf(rel);
            String enc = (mime.startsWith("text/") || mime.contains("json")) ? "utf-8" : null;
            WebResourceResponse r = new WebResourceResponse(mime, enc, in);
            Map<String, String> h = new HashMap<>();
            h.put("Access-Control-Allow-Origin", "*");
            h.put("Cross-Origin-Resource-Policy", "cross-origin");
            r.setResponseHeaders(h);
            return r;
        } catch (Exception e) {
            return notFound();
        }
    }

    private WebResourceResponse notFound() {
        Map<String, String> h = new HashMap<>();
        h.put("Access-Control-Allow-Origin", "*");
        return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found", h,
                new ByteArrayInputStream(new byte[0]));
    }

    private static String mimeOf(String name) {
        String n = name.toLowerCase();
        if (n.endsWith(".html")) return "text/html";
        if (n.endsWith(".js") || n.endsWith(".mjs")) return "text/javascript";
        if (n.endsWith(".css")) return "text/css";
        if (n.endsWith(".wasm")) return "application/wasm";
        if (n.endsWith(".woff2")) return "font/woff2";
        if (n.endsWith(".json")) return "application/json";
        if (n.endsWith(".txt")) return "text/plain";
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".svg")) return "image/svg+xml";
        return "application/octet-stream";
    }

    /** احتياطي: إن لم يكن النموذج مضمّنًا في الـ APK يُنزَّل مرة واحدة ثم يعمل دون إنترنت. */
    private File ensureModel(String name) {
        String url;
        if ("face_landmarker.task".equals(name)) url = FACE_URL;
        else if ("hand_landmarker.task".equals(name)) url = HAND_URL;
        else return null;

        File dir = new File(getFilesDir(), "models");
        if (!dir.exists() && !dir.mkdirs()) return null;
        File f = new File(dir, name);
        if (f.exists() && f.length() > 100000) return f;

        File tmp = new File(dir, name + ".part");
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            if (c.getResponseCode() != 200) return null;
            try (InputStream in = c.getInputStream(); FileOutputStream out = new FileOutputStream(tmp)) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            if (tmp.length() < 100000 || !tmp.renameTo(f)) return null;
            return f;
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
            if (tmp.exists()) tmp.delete();
        }
    }

    // ---------------- حفظ التسجيلات في مجلد التنزيلات ----------------

    public class Bridge {
        @JavascriptInterface
        public synchronized void beginSave(String name, String mime) {
            abortSave();
            try {
                String safe = name.replaceAll("[^A-Za-z0-9._-]", "_");
                ContentValues v = new ContentValues();
                v.put(MediaStore.Downloads.DISPLAY_NAME, safe);
                v.put(MediaStore.Downloads.MIME_TYPE, (mime == null || mime.isEmpty()) ? "application/octet-stream" : mime);
                v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/XRayMirror");
                ContentResolver cr = getContentResolver();
                saveUri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                if (saveUri == null) throw new IOException("insert failed");
                saveOut = cr.openOutputStream(saveUri);
            } catch (Exception e) {
                saveUri = null;
                saveOut = null;
                toast("تعذّر إنشاء الملف");
            }
        }

        @JavascriptInterface
        public synchronized void chunk(String b64) {
            if (saveOut == null) return;
            try {
                saveOut.write(Base64.decode(b64, Base64.DEFAULT));
            } catch (Exception e) {
                abortSave();
                toast("فشل الحفظ");
            }
        }

        @JavascriptInterface
        public synchronized void endSave() {
            if (saveOut == null) return;
            try {
                saveOut.close();
                saveOut = null;
                saveUri = null;
                toast("تم الحفظ في التنزيلات/XRayMirror");
            } catch (Exception e) {
                abortSave();
                toast("فشل الحفظ");
            }
        }

        @JavascriptInterface
        public synchronized void abortSave() {
            try {
                if (saveOut != null) saveOut.close();
            } catch (Exception ignored) {
            }
            try {
                if (saveUri != null) getContentResolver().delete(saveUri, null, null);
            } catch (Exception ignored) {
            }
            saveOut = null;
            saveUri = null;
        }
    }

    private void toast(final String msg) {
        runOnUiThread(() -> Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show());
    }

    @Override
    public void onBackPressed() {
        finish();
    }

    @Override
    protected void onPause() {
        super.onPause();
        web.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
    }

    @Override
    protected void onDestroy() {
        web.destroy();
        super.onDestroy();
    }
}
