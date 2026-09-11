package org.ppsspp.ppsspp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
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

    /** Quando true, veio do botão "+" do seletor de perfil (não pular auto-login). */
    public static final String EXTRA_ADDING_ACCOUNT = "adding_account";

    private EditText emailField;
    private EditText passwordField;
    private CheckBox stayLoggedInCheckbox;
    private Button loginButton;
    private ProgressBar progressBar;
    private TextView errorText;

    private AccountStore accountStore;
    private AuthClient authClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        accountStore = new AccountStore(this);
        authClient = new AuthClient(accountStore);

        boolean addingAccount = getIntent().getBooleanExtra(EXTRA_ADDING_ACCOUNT, false);

        // Se já existe pelo menos uma conta salva e não é um "adicionar conta"
        // explícito, pula direto para o seletor de perfil -- o usuário só
        // preenche este formulário uma vez por conta.
        if (!addingAccount && accountStore.hasAnyAccount()) {
            goToProfileSelector();
            return;
        }

        emailField = findViewById(R.id.email_field);
        passwordField = findViewById(R.id.password_field);
        stayLoggedInCheckbox = findViewById(R.id.stay_logged_in_checkbox);
        loginButton = findViewById(R.id.login_button);
        progressBar = findViewById(R.id.login_progress);
        errorText = findViewById(R.id.login_error_text);

        loginButton.setOnClickListener(v -> attemptLogin());

        TextView forgotPasswordLink = findViewById(R.id.forgot_password_link);
        forgotPasswordLink.setOnClickListener(v -> attemptPasswordReset());
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

        authClient.signIn(email, password, new AuthClient.AuthCallback() {
            @Override
            public void onSuccess(LocalAccount account) {
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
        authClient.sendPasswordReset(email, () -> {
            setLoading(false);
            Toast.makeText(LoginActivity.this,
                    "Ainda não temos envio de e-mail real -- essa parte chega junto com o backend.",
                    Toast.LENGTH_LONG).show();
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
