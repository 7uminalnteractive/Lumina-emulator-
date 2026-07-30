package org.ppsspp.ppsspp;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

/**
 * Lumina: hosts the site's "patches.html" page (free patches + Lumina+ / LPFL PRO) inside
 * the app, instead of reimplementing the catalog and subscription check natively.
 * lpfl-pro.html (linked from patches.html) already does the real subscription check
 * against Supabase, comparing the logged-in user's email against the "pedidos" table
 * (see lpfl-pro.html's verificarAssinatura()). We don't duplicate that logic here: we
 * just make sure the WebView is "logged in" the same way the site itself expects,
 * by writing the same localStorage key the site's own JS falls back to when there's
 * no full Supabase session (localStorage["lumina_email"], see lpfl-pro.html).
 */
public class PatchesActivity extends AppCompatActivity {

    private static final String PATCHES_URL = "https://lumina-interactive-site-two.vercel.app/patches.html";

    private WebView webView;
    private ProgressBar progressBar;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_patches);

        Toolbar toolbar = findViewById(R.id.patches_toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        webView = findViewById(R.id.patches_webview);
        progressBar = findViewById(R.id.patches_progress);

        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true); // required for localStorage

        String email = new SessionManager(this).getEmail();
        loadWithSession(email);
    }

    /**
     * Loads the patches page. If the user is logged in on the app, injects their email
     * into localStorage before navigation, matching the key the site's own JS reads
     * (see lpfl-pro.html: localStorage.getItem("lumina_email")) so subscription status
     * shows correctly without asking the user to log in again inside the WebView.
     */
    private void loadWithSession(@Nullable String email) {
        if (email == null || email.isEmpty()) {
            webView.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(WebView view, String url) {
                    progressBar.setVisibility(View.GONE);
                }
            });
            webView.loadUrl(PATCHES_URL);
            return;
        }

        // Load the target origin first so localStorage.setItem is allowed for it, then
        // set the key and navigate to the real page. Guard with a flag so we only seed
        // once, not on every subsequent in-app navigation.
        webView.setWebViewClient(new WebViewClient() {
            private boolean seeded = false;

            @Override
            public void onPageFinished(WebView view, String url) {
                if (!seeded) {
                    seeded = true;
                    String escapedEmail = email.replace("\\", "\\\\").replace("'", "\\'");
                    view.evaluateJavascript(
                            "localStorage.setItem('lumina_email', '" + escapedEmail + "');",
                            unused -> view.loadUrl(PATCHES_URL));
                } else {
                    progressBar.setVisibility(View.GONE);
                }
            }
        });
        webView.loadUrl(PATCHES_URL);
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
