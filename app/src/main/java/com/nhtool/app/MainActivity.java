package com.nhtool.app;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
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
import android.os.Looper;
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

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class MainActivity extends Activity {
    private static final int REQ_STORAGE = 7001;
    private static final int REQ_FILE = 7002;
    private static final String FILE_PROVIDER_AUTHORITY = "com.nhtool.stable.fileprovider";
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
            long now = System.currentTimeMillis();
            Uri collection;
            if (isImage) {
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/NHTool");
                values.put(MediaStore.Images.Media.DATE_TAKEN, now);
                collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            } else {
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/NHTool");
                collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
            }
            values.put(MediaStore.MediaColumns.DATE_ADDED, now / 1000L);
            values.put(MediaStore.MediaColumns.DATE_MODIFIED, now / 1000L);
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
            Uri uri = resolver.insert(collection, values);
            if (uri == null) throw new IllegalStateException("Không tạo được file trong bộ nhớ máy");
            boolean ok = false;
            try (OutputStream out = resolver.openOutputStream(uri, "w")) {
                if (out == null) throw new IllegalStateException("Không mở được file để ghi");
                out.write(data);
                out.flush();
                ok = true;
            } finally {
                if (!ok) resolver.delete(uri, null, null);
            }
            ContentValues done = new ContentValues();
            done.put(MediaStore.MediaColumns.IS_PENDING, 0);
            resolver.update(uri, done, null, null);
            resolver.notifyChange(uri, null);
            return uri;
        }

        if (!hasLegacyStoragePermission()) {
            requestLegacyStoragePermission();
            throw new SecurityException("Vui lòng cho phép quyền Bộ nhớ rồi thử lại");
        }
        File base = Environment.getExternalStoragePublicDirectory(isImage ? Environment.DIRECTORY_DCIM : Environment.DIRECTORY_DOWNLOADS);
        File dir = new File(base, "NHTool");
        if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Không tạo được thư mục NHTool");
        File outFile = new File(dir, filename);
        try (FileOutputStream out = new FileOutputStream(outFile)) {
            out.write(data);
            out.flush();
        }
        MediaScannerConnection.scanFile(this, new String[]{outFile.getAbsolutePath()}, new String[]{mime}, null);
        return Uri.fromFile(outFile);
    }

    private File writeShareCache(String filename, byte[] data) throws Exception {
        File dir = new File(getCacheDir(), "share");
        if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Không tạo được vùng chia sẻ");
        String safe = filename.replaceAll("[^A-Za-z0-9._-]", "_");
        File file = new File(dir, safe);
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(data);
            out.flush();
        }
        if (!file.isFile() || file.length() <= 0) throw new IllegalStateException("File chia sẻ rỗng");
        return file;
    }

    private boolean launchShareAndWait(String filename, String mime, File file) throws Exception {
        AtomicReference<Exception> error = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        Runnable task = () -> {
            try {
                Uri uri = FileProvider.getUriForFile(MainActivity.this, FILE_PROVIDER_AUTHORITY, file);
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType(mime == null || mime.isEmpty() ? "application/octet-stream" : mime);
                send.putExtra(Intent.EXTRA_STREAM, uri);
                send.putExtra(Intent.EXTRA_SUBJECT, filename);
                send.setClipData(ClipData.newRawUri(filename, uri));
                send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                Intent chooser = Intent.createChooser(send, "Chia sẻ báo cáo");
                chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(chooser);
            } catch (Exception e) {
                error.set(e);
            } finally {
                latch.countDown();
            }
        };
        if (Looper.myLooper() == Looper.getMainLooper()) task.run();
        else runOnUiThread(task);
        if (!latch.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Bảng chia sẻ phản hồi quá lâu");
        if (error.get() != null) throw error.get();
        return true;
    }

    public class AndroidBridge {
        @JavascriptInterface
        public String saveBase64(String filename, String mime, String b64) {
            try {
                byte[] data = Base64.decode(b64, Base64.DEFAULT);
                if (data.length == 0) throw new IllegalStateException("Dữ liệu ảnh rỗng");
                Uri uri = writePublicFile(filename, mime, data);
                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                        mime != null && mime.startsWith("image/")
                                ? "Đã lưu ảnh vào Thư viện / album NHTool"
                                : "Đã lưu file vào Downloads / NHTool",
                        Toast.LENGTH_LONG).show());
                return "OK_SAVED:" + uri;
            } catch (Exception e) {
                final String msg = "Không lưu được file: " + e.getMessage();
                runOnUiThread(() -> Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show());
                return "ERROR:" + msg;
            }
        }

        @JavascriptInterface
        public String shareBase64(String filename, String mime, String b64) {
            try {
                byte[] data = Base64.decode(b64, Base64.DEFAULT);
                if (data.length == 0) throw new IllegalStateException("Dữ liệu ảnh rỗng");
                File file = writeShareCache(filename, data);
                launchShareAndWait(filename, mime, file);
                return "OK_SHARE";
            } catch (Exception e) {
                final String msg = "Không mở được bảng chia sẻ: " + e.getMessage();
                runOnUiThread(() -> Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show());
                return "ERROR:" + msg;
            }
        }
    }
}
