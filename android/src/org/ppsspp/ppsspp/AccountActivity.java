package org.ppsspp.ppsspp;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

public class AccountActivity extends AppCompatActivity {

    private AccountStore accountStore;
    private SupabaseAuthClient authClient;
    private SessionManager sessionManager;
    private LocalAccount active;

    private TextView nameView;
    private TextView emailView;
    private TextView avatarView;
    private View statusDotView;

    private ActivityResultLauncher<String> photoPickerLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account);
        GmpWindowHelper.applyFullscreenOnly(this, findViewById(R.id.account_root));

        accountStore = new AccountStore(this);
        authClient = new SupabaseAuthClient();
        sessionManager = new SessionManager(this);
        active = accountStore.getActiveAccount();

        // GMP Gameport: registra o seletor de imagem aqui (não dentro de um
        // listener) porque o contrato do AndroidX Activity Result exige ser
        // registrado antes de STARTED -- registrar sob demanda, só quando o
        // usuário toca no avatar, derruba o app com IllegalStateException.
        photoPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        onPhotoPicked(uri);
                    }
                });

        emailView = findViewById(R.id.account_email);
        nameView = findViewById(R.id.account_name);
        avatarView = findViewById(R.id.account_avatar);
        statusDotView = findViewById(R.id.profile_status_dot);
        refreshProfileHeader();

        View avatarFrame = findViewById(R.id.account_avatar_frame);
        avatarFrame.setOnClickListener(v -> photoPickerLauncher.launch("image/*"));
        findViewById(R.id.account_avatar_edit_badge).setOnClickListener(v -> photoPickerLauncher.launch("image/*"));

        findViewById(R.id.btn_edit_profile).setOnClickListener(v -> showEditProfileDialog());
        findViewById(R.id.btn_change_password).setOnClickListener(v -> showChangePasswordDialog());
        findViewById(R.id.btn_change_theme).setOnClickListener(v -> showChangeThemeDialog());

        findViewById(R.id.btn_switch_profile).setOnClickListener(v -> {
            Intent intent = new Intent(this, ProfileSelectorActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        findViewById(R.id.btn_active_plans).setOnClickListener(v ->
                startActivity(new Intent(this, PlansActivity.class)));

        findViewById(R.id.btn_logout).setOnClickListener(v -> {
            accountStore.clearActiveAccount();
            // GMP Gameport: também derruba a sessão real do Supabase -- sem
            // isso, o próximo perfil escolhido no seletor herdaria o token
            // de acesso desta conta (ver aviso no topo da SupabaseCatalogSource).
            sessionManager.clearSession();
            Intent intent = new Intent(this, ProfileSelectorActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        // O Android mostra as barras de novo depois de diálogo/permissão/retorno
        // do jogo; reaplica o modo imersivo para manter a tela cheia.
        if (hasFocus) {
            GmpWindowHelper.reapplyImmersive(this);
        }
    }

    private void refreshProfileHeader() {
        emailView.setText(active != null ? active.email : "");
        nameView.setText(active != null ? active.displayName : "Bem-vindo(a)");
        ProfileBadgeHelper.bind(avatarView, statusDotView, active);
    }

    // ------------------------------------------------------------- editar perfil

    private void showEditProfileDialog() {
        if (active == null) return;

        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(active.displayName);
        input.setSelection(input.getText().length());

        new AlertDialog.Builder(this)
                .setTitle("Editar perfil")
                .setMessage("Nome de exibição")
                .setView(wrapWithPadding(input))
                .setPositiveButton("Salvar", (dialog, which) -> {
                    String newName = input.getText().toString().trim();
                    if (TextUtils.isEmpty(newName)) {
                        Toast.makeText(this, "Digite um nome.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    accountStore.addOrUpdateAccount(active.id, active.email, newName);
                    active.displayName = newName;
                    refreshProfileHeader();

                    if (sessionManager.hasSession()) {
                        authClient.updateDisplayName(sessionManager.getAccessToken(), newName, new SupabaseAuthClient.SimpleCallback() {
                            @Override
                            public void onSuccess() {
                                // Nada a fazer -- a tela já está atualizada.
                            }

                            @Override
                            public void onError(String message) {
                                Toast.makeText(AccountActivity.this,
                                        "O nome foi salvo neste aparelho, mas não deu pra sincronizar com o servidor agora.",
                                        Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    // ------------------------------------------------------------- trocar senha

    private void showChangePasswordDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);

        EditText newPasswordInput = new EditText(this);
        newPasswordInput.setHint("Nova senha");
        newPasswordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(newPasswordInput);

        EditText confirmInput = new EditText(this);
        confirmInput.setHint("Confirmar nova senha");
        confirmInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(confirmInput);

        new AlertDialog.Builder(this)
                .setTitle("Trocar senha")
                .setView(wrapWithPadding(layout))
                .setPositiveButton("Salvar", (dialog, which) -> {
                    String newPassword = newPasswordInput.getText().toString();
                    String confirm = confirmInput.getText().toString();
                    if (newPassword.length() < 6) {
                        Toast.makeText(this, "A senha precisa ter pelo menos 6 caracteres.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!newPassword.equals(confirm)) {
                        Toast.makeText(this, "As senhas não coincidem.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!sessionManager.hasSession()) {
                        Toast.makeText(this, "Sua sessão expirou -- saia e entre de novo antes de trocar a senha.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    authClient.updatePassword(sessionManager.getAccessToken(), newPassword, new SupabaseAuthClient.SimpleCallback() {
                        @Override
                        public void onSuccess() {
                            Toast.makeText(AccountActivity.this, "Senha atualizada!", Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onError(String message) {
                            Toast.makeText(AccountActivity.this, message, Toast.LENGTH_LONG).show();
                        }
                    });
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    // ------------------------------------------------------------- trocar tema

    private void showChangeThemeDialog() {
        // GMP Gameport: por enquanto só existe um tema de marca de verdade
        // (ver assets/themes/gmp_gameport.ini) -- quando mais forem
        // adicionados, é só crescer este array. A escolha é salva e aplicada
        // na próxima vez que a tela do jogo abrir (ver GmpThemePrefs e
        // NativeApp.setThemeName).
        String[] themeNames = { GmpThemePrefs.DEFAULT_THEME_NAME };
        String current = GmpThemePrefs.getSavedThemeName(this);
        int checkedIndex = 0;
        for (int i = 0; i < themeNames.length; i++) {
            if (themeNames[i].equals(current)) {
                checkedIndex = i;
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("Trocar tema")
                .setSingleChoiceItems(themeNames, checkedIndex, (dialog, which) -> {
                    GmpThemePrefs.setSavedThemeName(this, themeNames[which]);
                    Toast.makeText(this, "Tema aplicado na próxima vez que o jogo abrir.", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                })
                .setNegativeButton("Fechar", null)
                .show();
    }

    // ------------------------------------------------------------- foto de perfil

    private void onPhotoPicked(Uri uri) {
        if (active == null) return;
        try {
            Bitmap cropped = decodeAndCropSquare(uri);
            if (cropped == null) {
                Toast.makeText(this, "Não foi possível abrir essa imagem.", Toast.LENGTH_SHORT).show();
                return;
            }
            File outFile = new File(getFilesDir(), "profile_photo_" + active.id + ".jpg");
            try (FileOutputStream out = new FileOutputStream(outFile)) {
                cropped.compress(Bitmap.CompressFormat.JPEG, 90, out);
            }
            active.photoPath = outFile.getAbsolutePath();
            accountStore.setPhotoPath(active.id, active.photoPath);
            refreshProfileHeader();
        } catch (IOException e) {
            Toast.makeText(this, "Não foi possível salvar essa foto.", Toast.LENGTH_SHORT).show();
        }
    }

    /** Lê a imagem escolhida e já recorta em quadrado (o círculo final vem de ProfileBadgeHelper). */
    @Nullable
    private Bitmap decodeAndCropSquare(Uri uri) throws IOException {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) return null;
            Bitmap source = BitmapFactory.decodeStream(in);
            if (source == null) return null;
            int size = Math.min(source.getWidth(), source.getHeight());
            int x = (source.getWidth() - size) / 2;
            int y = (source.getHeight() - size) / 2;
            // Limita a 512px -- é só um avatar, não precisa da foto em resolução total.
            Bitmap square = Bitmap.createBitmap(source, x, y, size, size);
            int targetSize = Math.min(size, 512);
            if (targetSize != size) {
                return Bitmap.createScaledBitmap(square, targetSize, targetSize, true);
            }
            return square;
        }
    }

    private View wrapWithPadding(View view) {
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        LinearLayout container = new LinearLayout(this);
        container.setPadding(padding, padding, padding, 0);
        container.addView(view);
        return container;
    }
}
