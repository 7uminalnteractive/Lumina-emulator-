package org.ppsspp.ppsspp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class LoginActivity extends Activity {

    private EditText emailField;
    private EditText passwordField;
    private CheckBox stayLoggedInCheckbox;
    private Button loginButton;
    private ProgressBar progressBar;
    private TextView errorText;

    private SessionManager sessionManager;
    private SupabaseAuthClient authClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        sessionManager = new SessionManager(this);
        authClient = new SupabaseAuthClient();

        emailField = findViewById(R.id.email_field);
        passwordField = findViewById(R.id.password_field);
        stayLoggedInCheckbox = findViewById(R.id.stay_logged_in_checkbox);
        loginButton = findViewById(R.id.login_button);
        progressBar = findViewById(R.id.login_progress);
        errorText = findViewById(R.id.login_error_text);

        loginButton.setOnClickListener(v -> attemptLogin());

        TextView forgotPasswordLink = findViewById(R.id.forgot_password_link);
        forgotPasswordLink.setOnClickListener(v -> attemptPasswordReset());

        if (sessionManager.hasSession()) {
            tryAutoLogin();
        }
    }

    private void tryAutoLogin() {
        setLoading(true);
        authClient.refreshSession(sessionManager.getRefreshToken(), new SupabaseAuthClient.AuthCallback() {
            @Override
            public void onSuccess(SupabaseAuthClient.AuthResult result) {
                runOnUiThread(() -> {
                    sessionManager.saveSession(result);
                    goToMainApp();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    sessionManager.clearSession();
                    setLoading(false);
                });
            }
        });
    }

    private void attemptLogin() {
        String email = emailField.getText().toString().trim();
        String password = passwordField.getText().toString();

        errorText.setVisibility(View.GONE);

        if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showError("Digite um e-mail válido.");
            return;
        }
        if (TextUtils.isEmpty(password)) {
            showError("Digite sua senha.");
            return;
        }

        setLoading(true);

        authClient.signIn(email, password, new SupabaseAuthClient.AuthCallback() {
            @Override
            public void onSuccess(SupabaseAuthClient.AuthResult result) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    setLoading(false);
                    if (stayLoggedInCheckbox.isChecked()) {
                        sessionManager.saveSession(result);
                    }
                    goToMainApp();
                });
            }

            @Override
            public void onError(String message) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    setLoading(false);
                    showError(message);
                });
            }
        });
    }

    private void attemptPasswordReset() {
        String email = emailField.getText().toString().trim();
        if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showError("Digite seu e-mail no campo acima antes de pedir a redefinição.");
            return;
        }
        setLoading(true);
        authClient.sendPasswordReset(email, new SupabaseAuthClient.SimpleCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    setLoading(false);
                    Toast.makeText(LoginActivity.this,
                            "Se esse e-mail tiver conta, enviamos um link de redefinição.",
                            Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    setLoading(false);
                    showError(message);
                });
            }
        });
    }

    private void goToMainApp() {
        Intent intent = new Intent(LoginActivity.this, LibraryActivity.class);
        startActivity(intent);
        finish();
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        loginButton.setEnabled(!loading);
    }

    private void showError(String message) {
        errorText.setText(message);
        errorText.setVisibility(View.VISIBLE);
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
