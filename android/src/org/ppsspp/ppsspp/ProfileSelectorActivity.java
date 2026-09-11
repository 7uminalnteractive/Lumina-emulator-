package org.ppsspp.ppsspp;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

/**
 * Tela "Quem está jogando?" -- lista os perfis já salvos neste aparelho
 * (uma bolinha por conta) mais um botão "+" para adicionar uma conta nova.
 * Aparece depois que pelo menos um login já foi feito, para o usuário não
 * precisar digitar e-mail/senha de novo toda vez que abrir o app.
 */
public class ProfileSelectorActivity extends AppCompatActivity {

    private AccountStore accountStore;
    private LinearLayout profilesRow;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile_selector);

        accountStore = new AccountStore(this);
        profilesRow = findViewById(R.id.profiles_row);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Repopula toda vez que a tela volta a aparecer (ex: depois de
        // voltar de "adicionar conta"), para refletir mudanças recentes.
        renderProfiles();
    }

    private void renderProfiles() {
        profilesRow.removeAllViews();
        List<LocalAccount> accounts = accountStore.getAccounts();

        for (LocalAccount account : accounts) {
            profilesRow.addView(buildProfileItem(account));
        }
        profilesRow.addView(buildAddAccountItem());
    }

    private View buildProfileItem(LocalAccount account) {
        View item = LayoutInflater.from(this).inflate(R.layout.item_profile, profilesRow, false);

        TextView avatar = item.findViewById(R.id.profile_avatar_circle);
        avatar.setText(account.initial());

        TextView name = item.findViewById(R.id.profile_name_label);
        name.setText(account.displayName);

        item.setOnClickListener(v -> {
            accountStore.setActiveAccountId(account.id);
            goToLibrary();
        });

        return item;
    }

    private View buildAddAccountItem() {
        View item = LayoutInflater.from(this).inflate(R.layout.item_profile, profilesRow, false);

        TextView avatar = item.findViewById(R.id.profile_avatar_circle);
        avatar.setBackgroundResource(R.drawable.gmp_avatar_add_bg);
        avatar.setText("+");
        avatar.setTextColor(getColor(R.color.lumina_muted));

        TextView name = item.findViewById(R.id.profile_name_label);
        name.setText("Adicionar conta");
        name.setTextColor(getColor(R.color.lumina_muted));

        item.setOnClickListener(v -> {
            Intent intent = new Intent(this, LoginActivity.class);
            intent.putExtra(LoginActivity.EXTRA_ADDING_ACCOUNT, true);
            startActivity(intent);
        });

        return item;
    }

    private void goToLibrary() {
        Intent intent = new Intent(this, LibraryActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
