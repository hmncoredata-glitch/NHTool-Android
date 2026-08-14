package com.nhtool.app;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {
    private static final int REQ_STORAGE = 7001;
    private static final int REQ_FILE = 7002;
    private static final String SHARE_AUTHORITY = "com.nhtool.stable.sharefiles";
    private WebView webView;
    private ValueCallback<Uri[]> fileCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(238, 243, 251));
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) settings.setSafeBrowsingEnabled(true);

        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/vnd.ms-excel", "text/html", "text/plain"});
                try {
                    startActivityForResult(Intent.createChooser(intent, "Chọn file báo cáo bản cũ"), REQ_FILE);
                    return true;
                } catch (Exception e) {
                    fileCallback = null;
                    Toast.makeText(MainActivity.this, "Không mở được trình chọn file", Toast.LENGTH_LONG).show();
                    return false;
                }
            }
        });
        webView.addJavascriptInterface(new AndroidBridge(), "Android");
        webView.loadUrl("file:///android_asset/www/index.html");
        requestLegacyStoragePermission();
    }

    private void requestLegacyStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
        }
    }

    private boolean hasLegacyStoragePermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                || checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_FILE && fileCallback != null) {
            Uri[] result = null;
            if (resultCode == RESULT_OK && data != null && data.getData() != null) result = new Uri[]{data.getData()};
            fileCallback.onReceiveValue(result);
            fileCallback = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    private Uri writePublicFile(String filename, String mime, byte[] data) throws Exception {
        boolean isImage = mime != null && mime.startsWith("image/");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentResolver resolver = getContentResolver();
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
            values.put(MediaStore.MediaColumns.MIME_TYPE, mime);
            Uri collection;
            if (isImage) {
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/NHTool");
                collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            } else {
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/NHTool");
                collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
            }
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
            Uri uri = resolver.insert(collection, values);
            if (uri == null) throw new IllegalStateException("Không tạo được file");
            try (OutputStream out = resolver.openOutputStream(uri)) {
                if (out == null) throw new IllegalStateException("Không mở được file");
                out.write(data);
            }
            ContentValues done = new ContentValues();
            done.put(MediaStore.MediaColumns.IS_PENDING, 0);
            resolver.update(uri, done, null, null);
            return uri;
        }

        if (!hasLegacyStoragePermission()) {
            requestLegacyStoragePermission();
            throw new SecurityException("Vui lòng bấm Cho phép quyền Bộ nhớ rồi thử lại");
        }
        File base = Environment.getExternalStoragePublicDirectory(isImage ? Environment.DIRECTORY_PICTURES : Environment.DIRECTORY_DOWNLOADS);
        File dir = new File(base, "NHTool");
        if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Không tạo được thư mục NHTool");
        File outFile = new File(dir, filename);
        try (FileOutputStream out = new FileOutputStream(outFile)) { out.write(data); }
        MediaScannerConnection.scanFile(this, new String[]{outFile.getAbsolutePath()}, new String[]{mime}, null);
        return Uri.fromFile(outFile);
    }

    private Uri writeShareCache(String filename, byte[] data) throws Exception {
        File dir = new File(getCacheDir(), "share");
        if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Không tạo được vùng chia sẻ");
        String safe = filename.replaceAll("[^A-Za-z0-9._-]", "_");
        File file = new File(dir, safe);
        try (FileOutputStream out = new FileOutputStream(file)) { out.write(data); }
        return Uri.parse("content://" + SHARE_AUTHORITY + "/" + Uri.encode(safe));
    }

    public class AndroidBridge {
        @JavascriptInterface
        public String saveBase64(String filename, String mime, String b64) {
            try {
                byte[] data = Base64.decode(b64, Base64.DEFAULT);
                writePublicFile(filename, mime, data);
                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                        mime != null && mime.startsWith("image/")
                                ? "Đã lưu ảnh: Thư viện / Pictures / NHTool"
                                : "Đã lưu file: Downloads / NHTool",
                        Toast.LENGTH_LONG).show());
                return "OK_SAVED";
            } catch (Exception e) {
                final String msg = "Không lưu được file: " + e.getMessage();
                runOnUiThread(() -> Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show());
                return msg;
            }
        }

        @JavascriptInterface
        public String shareBase64(String filename, String mime, String b64) {
            try {
                byte[] data = Base64.decode(b64, Base64.DEFAULT);
                Uri uri = writeShareCache(filename, data);
                runOnUiThread(() -> {
                    try {
                        Intent intent = new Intent(Intent.ACTION_SEND);
                        intent.setType(mime == null || mime.isEmpty() ? "application/octet-stream" : mime);
                        intent.putExtra(Intent.EXTRA_STREAM, uri);
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(Intent.createChooser(intent, "Chia sẻ báo cáo"));
                    } catch (Exception ex) {
                        Toast.makeText(MainActivity.this, "Không mở được bảng chia sẻ: " + ex.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
                return "OK_SHARE";
            } catch (Exception e) {
                final String msg = "Không chia sẻ được file: " + e.getMessage();
                runOnUiThread(() -> Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show());
                return msg;
            }
        }
    }
}
