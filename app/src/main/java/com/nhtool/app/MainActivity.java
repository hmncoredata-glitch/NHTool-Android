package com.nhtool.app;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
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
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {
    private WebView webView;

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
        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new AndroidBridge(), "Android");
        webView.loadUrl("file:///android_asset/www/index.html");
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    private Uri writeFile(String filename, String mime, byte[] data) throws Exception {
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

        File base = Environment.getExternalStoragePublicDirectory(isImage ? Environment.DIRECTORY_PICTURES : Environment.DIRECTORY_DOWNLOADS);
        File dir = new File(base, "NHTool");
        if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Không tạo được thư mục NHTool");
        File outFile = new File(dir, filename);
        try (FileOutputStream out = new FileOutputStream(outFile)) {
            out.write(data);
        }
        MediaScannerConnection.scanFile(this, new String[]{outFile.getAbsolutePath()}, new String[]{mime}, null);
        return Uri.fromFile(outFile);
    }

    public class AndroidBridge {
        @JavascriptInterface
        public String saveBase64(String filename, String mime, String b64) {
            try {
                byte[] data = Base64.decode(b64, Base64.DEFAULT);
                writeFile(filename, mime, data);
                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                        mime != null && mime.startsWith("image/")
                                ? "Đã lưu ảnh vào Thư viện/Pictures/NHTool"
                                : "Đã lưu file vào Downloads/NHTool",
                        Toast.LENGTH_LONG).show());
                return "OK";
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
                Uri uri = writeFile(filename, mime, data);
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this,
                            "Ảnh đã lưu vào thư viện. Thiết bị Android cũ có thể cần chia sẻ từ ứng dụng Ảnh.",
                            Toast.LENGTH_LONG).show());
                    return "OK_SAVED";
                }
                runOnUiThread(() -> {
                    Intent intent = new Intent(Intent.ACTION_SEND);
                    intent.setType(mime);
                    intent.putExtra(Intent.EXTRA_STREAM, uri);
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(intent, "Chia sẻ báo cáo"));
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
