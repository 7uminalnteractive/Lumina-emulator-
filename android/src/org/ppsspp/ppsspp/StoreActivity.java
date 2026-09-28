package org.ppsspp.ppsspp;

import android.content.Intent;
import android.os.Environment;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Loja GMPES: jogos/patches disponíveis para baixar, em oposição a
 * LibraryActivity, que mostra só os jogos já instalados no aparelho.
 *
 * A seção "Disponíveis para baixar" (catálogo filtrado pelo acesso do usuário)
 * vive aqui; antes ficava na Biblioteca.
 */
public class StoreActivity extends AppCompatActivity {

    private AccountStore accountStore;
    private TextView profileAvatar;
    private View profileStatusDot;

    private View downloadSection;
    private TextView downloadStatus;
    private RecyclerView downloadGrid;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_store);
        GmpWindowHelper.apply(this, findViewById(R.id.store_root),
                findViewById(R.id.sidebar), findViewById(R.id.main_content));

        accountStore = new AccountStore(this);
        profileAvatar = findViewById(R.id.profile_avatar);
        profileStatusDot = findViewById(R.id.profile_status_dot);

        downloadSection = findViewById(R.id.download_section);
        downloadStatus = findViewById(R.id.download_status);
        downloadGrid = findViewById(R.id.download_grid);
        downloadGrid.setLayoutManager(new GridLayoutManager(this, calculateGridColumnCount()));

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
        loadDownloadableGames();
    }

    // Mesma conta de colunas da Biblioteca (cards de ~150dp ao lado da sidebar).
    private int calculateGridColumnCount() {
        float density = getResources().getDisplayMetrics().density;
        int screenWidthPx = getResources().getDisplayMetrics().widthPixels;
        float sidebarDp = 84f;
        float horizontalPaddingDp = 24f;
        float targetCardWidthDp = 150f;

        float availableWidthDp = (screenWidthPx / density) - sidebarDp - horizontalPaddingDp;
        return Math.max(2, Math.round(availableWidthDp / targetCardWidthDp));
    }

    private File gamesFolder() {
        return new File(Environment.getExternalStorageDirectory(), "GMP/Jogo/Game");
    }

    /**
     * Pergunta ao catálogo o que ESTE usuário pode baixar (patch comprado ou
     * plano ativo) e mostra o que ainda não está instalado. Sem internet a
     * Loja mostra um aviso e o resto do app continua funcionando.
     */
    private void loadDownloadableGames() {
        LocalAccount account = accountStore.getActiveAccount();
        if (account == null || account.email == null) {
            showDownloadable(new ArrayList<>(), false);
            return;
        }
        final String email = account.email;
        final File gameDir = gamesFolder();
        final CatalogSource source = GmpCatalog.source(getApplicationContext());

        new Thread(() -> {
            List<CatalogGame> pending = new ArrayList<>();
            boolean failed = false;
            try {
                for (CatalogGame game : source.fetchDownloadableGames(email)) {
                    // Por ora só jogos; outros tipos (textura, save...) serão
                    // instalados junto do jogo/patch a que pertencem.
                    if ("game".equals(game.kind) && !new File(gameDir, game.fileName).exists()) {
                        pending.add(game);
                    }
                }
            } catch (Exception e) {
                android.util.Log.w("StoreActivity", "Não foi possível consultar o catálogo", e);
                failed = true;
            }
            final boolean finalFailed = failed;
            runOnUiThread(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    showDownloadable(pending, finalFailed);
                }
            });
        }, "gmp-catalog").start();
    }

    private void showDownloadable(List<CatalogGame> games, boolean failed) {
        downloadSection.setVisibility(View.VISIBLE);
        if (!games.isEmpty()) {
            downloadStatus.setVisibility(View.GONE);
            downloadGrid.setVisibility(View.VISIBLE);
            downloadGrid.setAdapter(new DownloadAdapter(games, this::openInstaller));
        } else {
            downloadGrid.setVisibility(View.GONE);
            downloadStatus.setVisibility(View.VISIBLE);
            downloadStatus.setText(failed
                    ? "Não foi possível verificar os jogos disponíveis. "
                        + "Confira sua internet; seus jogos instalados continuam funcionando."
                    : "Nenhum jogo disponível para baixar no momento.");
        }
    }

    private void openInstaller(CatalogGame game) {
        Intent intent = new Intent(this, InstallerActivity.class);
        intent.putExtra(InstallerActivity.EXTRA_GAME_ID, game.id);
        intent.putExtra(InstallerActivity.EXTRA_GAME_TITLE, game.title);
        startActivity(intent);
    }
}
