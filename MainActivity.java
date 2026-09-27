package com.example.yourapp;

import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.webkit.PermissionRequest;
import android.webkit.WebSettings;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import java.util.concurrent.Executor;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private BiometricPrompt biometricPrompt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // ============ إعداد WebView ============
        webView = findViewById(R.id.webView);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);

        // ============ جسر JavaScript ↔ Java ============
        webView.addJavascriptInterface(new WebAppInterface(), "Android");

        // ============ منح صلاحية الميكروفون تلقائيًا ============
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                request.grant(request.getResources());
            }
        });

        // ============ تحميل الصفحة ============
        webView.setWebViewClient(new WebViewClient());
        webView.loadUrl("file:///android_asset/www/index.html");
    }

    // =========================================================
    //   الجسر: الدوال المُتاحة للجافاسكريبت
    // =========================================================
    public class WebAppInterface {

        /** هل الجهاز يدعم البصمة؟ */
        @JavascriptInterface
        public boolean isBiometricAvailable() {
            BiometricManager bm = BiometricManager.from(MainActivity.this);
            int result = bm.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG |
                BiometricManager.Authenticators.BIOMETRIC_WEAK
            );
            return result == BiometricManager.BIOMETRIC_SUCCESS;
        }

        /** تشغيل نافذة البصمة الأصلية */
        @JavascriptInterface
        public void authenticateBiometric() {
            runOnUiThread(() -> showBiometricPrompt());
        }

        /** نسخ نص إلى الحافظة */
        @JavascriptInterface
        public void copyToClipboard(String text) {
            runOnUiThread(() -> {
                android.content.ClipboardManager cm =
                    (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                cm.setPrimaryClip(android.content.ClipData.newPlainText("search", text));
            });
        }
    }
        // =========================================================
    //   BiometricPrompt: نافذة البصمة الأصلية
    // =========================================================
    private void showBiometricPrompt() {
        Executor executor = ContextCompat.getMainExecutor(this);

        biometricPrompt = new BiometricPrompt(this, executor,
            new BiometricPrompt.AuthenticationCallback() {

                @Override
                public void onAuthenticationSucceeded(
                        @NonNull BiometricPrompt.AuthenticationResult result) {
                    super.onAuthenticationSucceeded(result);
                    // إبلاغ JavaScript بالنجاح
                    webView.post(() -> webView.evaluateJavascript(
                        "window.onNativeBiometricSuccess && window.onNativeBiometricSuccess()",
                        null
                    ));
                }

                @Override
                public void onAuthenticationError(int code, @NonNull CharSequence msg) {
                    super.onAuthenticationError(code, msg);
                    // إبلاغ JavaScript بالفشل مع نص الرسالة
                    String safe = msg.toString()
                                     .replace("\\", "\\\\")
                                     .replace("'", "\\'")
                                     .replace("\n", " ")
                                     .replace("\r", " ");
                    webView.post(() -> webView.evaluateJavascript(
                        "window.onNativeBiometricError && window.onNativeBiometricError('" + safe + "')",
                        null
                    ));
                }

                @Override
                public void onAuthenticationFailed() {
                    super.onAuthenticationFailed();
                    // البصمة غير مطابقة — لا نفعل شيئًا، النظام يعرض رسالة تلقائيًا
                }
            });

        BiometricPrompt.PromptInfo promptInfo =
            new BiometricPrompt.PromptInfo.Builder()
                .setTitle("التطبيق مقفل")
                .setSubtitle("ضع بصمتك لفتح التطبيق")
                .setNegativeButtonText("إلغاء")
                .setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG |
                    BiometricManager.Authenticators.BIOMETRIC_WEAK
                )
                .build();

        try {
            biometricPrompt.authenticate(promptInfo);
        } catch (Exception e) {
            // في حال حدوث خطأ غير متوقع، نُبلغ JavaScript
            String safe = (e.getMessage() != null ? e.getMessage() : "خطأ غير معروف")
                            .replace("\\", "\\\\")
                            .replace("'", "\\'")
                            .replace("\n", " ");
            webView.post(() -> webView.evaluateJavascript(
                "window.onNativeBiometricError && window.onNativeBiometricError('" + safe + "')",
                null
            ));
        }
    }
}