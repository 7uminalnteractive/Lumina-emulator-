package org.ppsspp.ppsspp;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Loja de Games: jogos/patches disponíveis para baixar, em oposição a
 * LibraryActivity, que mostra os jogos já instalados no aparelho.
 *
 * FASE ATUAL: catálogo fixo (StoreCatalog), sem backend, sem download real
 * -- serve para validar o fluxo visual completo (Login → Perfil →
 * Biblioteca → Loja) antes do Supabase/backend entrar.
 */
public class StoreActivity extends AppCompatActivity {

    private AccountStore accountStore;
    private android.widget.TextView profileAvatar;
    private View profileStatusDot;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_store);
        GmpWindowHelper.apply(this, findViewById(R.id.store_root),
                findViewById(R.id.sidebar), findViewById(R.id.main_content));

        accountStore = new AccountStore(this);
        profileAvatar = findViewById(R.id.profile_avatar);
        profileStatusDot = findViewById(R.id.profile_status_dot);

        RecyclerView storeGrid = findViewById(R.id.store_grid);
        storeGrid.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        storeGrid.setAdapter(new StoreAdapter(StoreCatalog.getItems()));

        View gamesTab = findViewById(R.id.tab_games);
        gamesTab.setOnClickListener(v -> {
            startActivity(new Intent(this, LibraryActivity.class));
            finish();
        });

        View settingsTab = findViewById(R.id.tab_settings);
        settingsTab.setOnClickListener(v -> {
            Intent intent = new Intent(this, PpssppActivity.class);
            intent.putExtra(PpssppActivity.ARGS_EXTRA_KEY, "--start-screen=gamesettings");
            startActivity(intent);
        });

        View profileTab = findViewById(R.id.tab_profile);
        profileTab.setOnClickListener(v ->
                startActivity(new Intent(this, AccountActivity.class)));
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

    @Override
    protected void onResume() {
        super.onResume();
        ProfileBadgeHelper.bind(profileAvatar, profileStatusDot, accountStore.getActiveAccount());
    }
}
