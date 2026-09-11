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

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_store);

        RecyclerView storeGrid = findViewById(R.id.store_grid);
        storeGrid.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        storeGrid.setAdapter(new StoreAdapter(StoreCatalog.getItems()));

        View gamesTab = findViewById(R.id.tab_games);
        gamesTab.setOnClickListener(v -> {
            startActivity(new Intent(this, LibraryActivity.class));
            finish();
        });

        View patchesTab = findViewById(R.id.tab_media);
        patchesTab.setOnClickListener(v ->
                startActivity(new Intent(this, PatchesActivity.class)));

        View settingsButton = findViewById(R.id.btn_settings);
        settingsButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, PpssppActivity.class);
            intent.putExtra(PpssppActivity.ARGS_EXTRA_KEY, "--start-screen=gamesettings");
            startActivity(intent);
        });

        View profileButton = findViewById(R.id.profile_avatar);
        profileButton.setOnClickListener(v ->
                startActivity(new Intent(this, AccountActivity.class)));
    }
}
