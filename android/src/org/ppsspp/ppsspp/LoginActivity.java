package org.ppsspp.ppsspp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class LoginActivity extends Activity {

    /** Quando true, veio do botão "+" do seletor de perfil (não pular auto-login). */
    public static final String EXTRA_ADDING_ACCOUNT = "adding_account";

    private EditText nameField;
    private EditText emailField;
    private EditText passwordField;
    private Button loginButton;
    private ProgressBar progressBar;
    private TextView errorText;

    private AccountStore accountStore;
    private SupabaseAuthClient authClient;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        GmpWindowHelper.applyFullscreenOnly(this, findViewById(R.id.login_root));

        accountStore = new AccountStore(this);
        authClient = new SupabaseAuthClient();
        sessionManager = new SessionManager(this);

        boolean addingAccount = getIntent().getBooleanExtra(EXTRA_ADDING_ACCOUNT, false);

        // Se já existe pelo menos uma conta salva e não é um "adicionar conta"
        // explícito, pula direto para o seletor de perfil -- o usuário só
        // preenche este formulário uma vez por conta.
        if (!addingAccount && accountStore.hasAnyAccount()) {
            goToProfileSelector();
            return;
        }

        nameField = findViewById(R.id.name_field);
        emailField = findViewById(R.id.email_field);
        passwordField = findViewById(R.id.password_field);
        loginButton = findViewById(R.id.login_button);
        progressBar = findViewById(R.id.login_progress);
        errorText = findViewById(R.id.login_error_text);

        loginButton.setOnClickListener(v -> attemptLogin());

        TextView forgotPasswordLink = findViewById(R.id.forgot_password_link);
        forgotPasswordLink.setOnClickListener(v -> attemptPasswordReset());
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            GmpWindowHelper.reapplyImmersive(this);
        }
    }

    private void attemptLogin() {
        String name = nameField.getText().toString().trim();
        String email = emailField.getText().toString().trim();
        String password = passwordField.getText().toString();

        errorText.setVisibility(View.GONE);

        if (TextUtils.isEmpty(name)) {
            showError("Digite seu nome.");
            return;
        }
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
                sessionManager.saveSession(result);
                // O login real não tem um campo "nome" (isso é cadastro, que este
                // formulário não faz). Usa o nome vindo do servidor se houver;
                // senão, o que a pessoa digitou aqui mesmo.
                String finalName = (result.displayName != null && !result.displayName.isEmpty())
                        ? result.displayName
                        : name;
                accountStore.addOrUpdateAccount(result.userId, result.email, finalName);
                setLoading(false);
                goToProfileSelector();
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                showError(message);
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
                setLoading(false);
                Toast.makeText(LoginActivity.this,
                        "Enviamos um e-mail com instruções para redefinir sua senha.",
                        Toast.LENGTH_LONG).show();
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                showError(message);
            }
        });
    }

    private void goToProfileSelector() {
        Intent intent = new Intent(LoginActivity.this, ProfileSelectorActivity.class);
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
