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
 * GMP Gameport: hospeda a página de patches do site GMPES dentro do app via
 * WebView, em vez de reimplementar o catálogo nativamente.
 *
 * NOTA: esta tela ainda aponta para o domínio antigo do fork Lumina
 * (ver PATCHES_URL abaixo) e injeta o e-mail da conta ativa em
 * localStorage["lumina_email"], que era a chave que o site antigo lia.
 * Isso deve ser substituído quando a Loja de Games nativa (Fase C do
 * redesign GMP Gameport) estiver pronta -- ver conversa sobre roadmap.
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

        String email = null;
        LocalAccount active = new AccountStore(this).getActiveAccount();
        if (active != null) {
            email = active.email;
        }
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
