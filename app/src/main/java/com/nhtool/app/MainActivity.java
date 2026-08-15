package com.nhtool.app;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class MainActivity extends Activity {
    private static final int REQ_STORAGE = 7001;
    private static final int REQ_FILE = 7002;
    private static final String SHARE_AUTHORITY = "com.nhtool.stable.sharefiles";
    private static final int MAX_TRANSFER_B64 = 40 * 1024 * 1024;

    private WebView webView;
    private ValueCallback<Uri[]> fileCallback;

    private final Object transferLock = new Object();
    private StringBuilder transferBase64;
    private String transferFilename;
    private String transferMime;

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

    private static boolean isPng(byte[] data) {
        return data != null && data.length >= 8
                && (data[0] & 0xff) == 0x89 && data[1] == 0x50 && data[2] == 0x4e && data[3] == 0x47
                && data[4] == 0x0d && data[5] == 0x0a && data[6] == 0x1a && data[7] == 0x0a;
    }

    private void verifyDecodedData(String mime, byte[] data) {
        if (data == null || data.length < 8) throw new IllegalStateException("Dữ liệu file rỗng hoặc không đầy đủ");
        if (mime != null && mime.equalsIgnoreCase("image/png") && !isPng(data)) {
            throw new IllegalStateException("Dữ liệu PNG không hợp lệ");
        }
    }

    private Uri writePublicFile(String filename, String mime, byte[] data) throws Exception {
        verifyDecodedData(mime, data);
        boolean isImage = mime != null && mime.startsWith("image/");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentResolver resolver = getContentResolver();
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
            values.put(MediaStore.MediaColumns.MIME_TYPE, mime);
            long now = System.currentTimeMillis();
            Uri collection;
            if (isImage) {
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/NHTool");
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
            verifyPublicUri(uri, mime);
            resolver.notifyChange(uri, null);
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
        try (FileOutputStream out = new FileOutputStream(outFile)) {
            out.write(data);
            out.flush();
        }
        if (!outFile.isFile() || outFile.length() < 8) throw new IllegalStateException("File sau khi lưu không hợp lệ");
        if (mime != null && mime.equalsIgnoreCase("image/png")) {
            byte[] sig = new byte[8];
            try (FileInputStream in = new FileInputStream(outFile)) {
                if (in.read(sig) != 8 || !isPng(sig)) throw new IllegalStateException("PNG sau khi lưu bị lỗi");
            }
        }
        MediaScannerConnection.scanFile(this, new String[]{outFile.getAbsolutePath()}, new String[]{mime}, null);
        return Uri.fromFile(outFile);
    }

    private void verifyPublicUri(Uri uri, String mime) throws Exception {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IllegalStateException("Không đọc lại được file vừa lưu");
            byte[] sig = new byte[8];
            int n = in.read(sig);
            if (n < 8) throw new IllegalStateException("File vừa lưu bị rỗng");
            if (mime != null && mime.equalsIgnoreCase("image/png") && !isPng(sig)) {
                throw new IllegalStateException("PNG vừa lưu không hợp lệ");
            }
        }
    }

    private Uri writeShareCache(String filename, byte[] data) throws Exception {
        File dir = new File(getCacheDir(), "share");
        if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Không tạo được vùng chia sẻ");
        String safe = filename.replaceAll("[^A-Za-z0-9._-]", "_");
        File file = new File(dir, safe);
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(data);
            out.flush();
        }
        if (!file.isFile() || file.length() < 8) throw new IllegalStateException("File chia sẻ bị rỗng");
        return Uri.parse("content://" + SHARE_AUTHORITY + "/" + Uri.encode(safe));
    }

    private String openShareSheet(String filename, String mime, Uri uri) throws Exception {
        AtomicReference<String> result = new AtomicReference<>("ERROR:Không mở được bảng chia sẻ");
        CountDownLatch latch = new CountDownLatch(1);
        Runnable task = () -> {
            try {
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType(mime == null || mime.isEmpty() ? "application/octet-stream" : mime);
                send.putExtra(Intent.EXTRA_STREAM, uri);
                send.putExtra(Intent.EXTRA_SUBJECT, filename);
                send.setClipData(ClipData.newRawUri(filename, uri));
                send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                List<ResolveInfo> targets = getPackageManager().queryIntentActivities(send, PackageManager.MATCH_DEFAULT_ONLY);
                for (ResolveInfo info : targets) {
                    if (info.activityInfo != null && info.activityInfo.packageName != null) {
                        grantUriPermission(info.activityInfo.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    }
                }

                Intent chooser = Intent.createChooser(send, "Chia sẻ báo cáo");
                chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(chooser);
                result.set("OK_SHARE");
            } catch (Exception e) {
                result.set("ERROR:" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            } finally {
                latch.countDown();
            }
        };

        if (Looper.myLooper() == Looper.getMainLooper()) {
            task.run();
        } else {
            runOnUiThread(task);
            if (!latch.await(5, TimeUnit.SECONDS)) return "ERROR:Bảng chia sẻ phản hồi quá lâu";
        }
        return result.get();
    }

    private byte[] consumeTransfer() {
        synchronized (transferLock) {
            if (transferBase64 == null) throw new IllegalStateException("Chưa bắt đầu truyền dữ liệu");
            String b64 = transferBase64.toString();
            transferBase64 = null;
            if (b64.isEmpty()) throw new IllegalStateException("Dữ liệu truyền rỗng");
            return Base64.decode(b64, Base64.DEFAULT);
        }
    }

    private String currentTransferFilename() {
        synchronized (transferLock) {
            return transferFilename == null || transferFilename.trim().isEmpty() ? "nhtool-report" : transferFilename;
        }
    }

    private String currentTransferMime() {
        synchronized (transferLock) {
            return transferMime == null || transferMime.trim().isEmpty() ? "application/octet-stream" : transferMime;
        }
    }

    public class AndroidBridge {
        @JavascriptInterface
        public String beginTransfer(String filename, String mime) {
            synchronized (transferLock) {
                transferFilename = filename;
                transferMime = mime;
                transferBase64 = new StringBuilder();
                return "OK_BEGIN";
            }
        }

        @JavascriptInterface
        public String appendTransferChunk(String chunk) {
            synchronized (transferLock) {
                if (transferBase64 == null) return "ERROR:Chưa bắt đầu truyền dữ liệu";
                if (chunk == null) return "ERROR:Chunk rỗng";
                if (transferBase64.length() + chunk.length() > MAX_TRANSFER_B64) {
                    transferBase64 = null;
                    return "ERROR:Báo cáo quá lớn để xuất";
                }
                transferBase64.append(chunk);
                return "OK_CHUNK";
            }
        }

        @JavascriptInterface
        public String finishSaveTransfer() {
            try {
                String filename = currentTransferFilename();
                String mime = currentTransferMime();
                byte[] data = consumeTransfer();
                verifyDecodedData(mime, data);
                Uri uri = writePublicFile(filename, mime, data);
                final String toastText = mime.startsWith("image/")
                        ? "Đã lưu ảnh vào Thư viện / Pictures / NHTool"
                        : "Đã lưu file vào Downloads / NHTool";
                runOnUiThread(() -> Toast.makeText(MainActivity.this, toastText, Toast.LENGTH_LONG).show());
                return "OK_SAVED:" + uri.toString();
            } catch (Exception e) {
                String msg = "ERROR:" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                runOnUiThread(() -> Toast.makeText(MainActivity.this, msg.substring(6), Toast.LENGTH_LONG).show());
                return msg;
            }
        }

        @JavascriptInterface
        public String finishShareTransfer() {
            try {
                String filename = currentTransferFilename();
                String mime = currentTransferMime();
                byte[] data = consumeTransfer();
                verifyDecodedData(mime, data);
                Uri uri = writeShareCache(filename, data);
                String result = openShareSheet(filename, mime, uri);
                if (!result.startsWith("OK")) {
                    final String msg = result.startsWith("ERROR:") ? result.substring(6) : result;
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show());
                }
                return result;
            } catch (Exception e) {
                String msg = "ERROR:" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                runOnUiThread(() -> Toast.makeText(MainActivity.this, msg.substring(6), Toast.LENGTH_LONG).show());
                return msg;
            }
        }

        // Giữ tương thích với các bản JavaScript cũ.
        @JavascriptInterface
        public String saveBase64(String filename, String mime, String b64) {
            try {
                byte[] data = Base64.decode(b64, Base64.DEFAULT);
                verifyDecodedData(mime, data);
                Uri uri = writePublicFile(filename, mime, data);
                return "OK_SAVED:" + uri.toString();
            } catch (Exception e) {
                return "ERROR:" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            }
        }

        @JavascriptInterface
        public String shareBase64(String filename, String mime, String b64) {
            try {
                byte[] data = Base64.decode(b64, Base64.DEFAULT);
                verifyDecodedData(mime, data);
                Uri uri = writeShareCache(filename, data);
                return openShareSheet(filename, mime, uri);
            } catch (Exception e) {
                return "ERROR:" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            }
        }
    }
}
