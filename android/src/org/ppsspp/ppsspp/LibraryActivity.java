package org.ppsspp.ppsspp;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class LibraryActivity extends AppCompatActivity {

    private static final List<String> SUPPORTED_EXTENSIONS =
            List.of("iso", "cso", "pbp", "chd", "elf");
    private static final List<String> IMAGE_EXTENSIONS =
            List.of("jpg", "jpeg", "png", "webp");
    private static final List<String> COVER_FOLDER_NAMES =
            List.of("covers", "capas", "cover", "capa", "boxart");
    private static final int MAX_SCAN_DEPTH = 6;

    private RecyclerView recyclerView;
    private View emptyState;
    private View pickFolderState;
    private ProgressBar progressBar;
    private TextView folderLabel;
    private TextView heroTitle;
    private TextView heroSubtitle;
    private View heroPlayButton;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_library);

        recyclerView = findViewById(R.id.games_grid);
        emptyState = findViewById(R.id.empty_state);
        pickFolderState = findViewById(R.id.pick_folder_state);
        progressBar = findViewById(R.id.library_progress);
        folderLabel = findViewById(R.id.folder_label);
        heroTitle = findViewById(R.id.hero_title);
        heroSubtitle = findViewById(R.id.hero_subtitle);
        heroPlayButton = findViewById(R.id.hero_play_button);

        recyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));

        Button grantAccessButton = findViewById(R.id.btn_pick_folder);
        grantAccessButton.setOnClickListener(v -> requestStorageAccess());

        View settingsButton = findViewById(R.id.btn_settings);
        settingsButton.setOnClickListener(v -> {
            // Lumina: PpssppActivity is launchMode="singleInstance". If an instance is
            // already alive in the background, Android reuses it via onNewIntent()
            // instead of onCreate() - and onNewIntent() treats any shortcut param as a
            // file path to boot (see PpssppActivity.onNewIntent / NativeApp "shortcutParam"
            // message), not as a command-line flag. That silently broke "--start-screen=
            // gamesettings", which only works when parsed fresh in onCreate(). Clearing
            // the task ensures we always get a clean onCreate() so the flag is honored.
            Intent intent = new Intent(this, PpssppActivity.class);
            intent.putExtra(PpssppActivity.ARGS_EXTRA_KEY, "--start-screen=gamesettings");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });

        View profileButton = findViewById(R.id.profile_avatar);
        profileButton.setOnClickListener(v ->
                startActivity(new Intent(this, AccountActivity.class)));

        View patchesTab = findViewById(R.id.tab_media);
        patchesTab.setOnClickListener(v ->
                startActivity(new Intent(this, PatchesActivity.class)));

        heroTitle.setText("Sua biblioteca");
        heroSubtitle.setText("Concedendo acesso, buscamos seus jogos automaticamente.");
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (hasStorageAccess()) {
            scanGamesFolder();
        } else {
            showPickFolderState();
        }
    }

    private boolean hasStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        return true;
    }

    private void requestStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } else {
            requestPermissions(new String[]{
                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, 1001);
        }
    }

    private File gamesFolder() {
        File externalRoot = Environment.getExternalStorageDirectory();
        return new File(externalRoot, "PSP/GAME");
    }

    private void scanGamesFolder() {
        showLoading();
        folderLabel.setText("Armazenamento/PSP/GAME");

        new Thread(() -> {
            List<GameItem> games = new ArrayList<>();
            try {
                File root = gamesFolder();
                if (root.exists() && root.isDirectory()) {
                    collectGames(root, games, 0);
                }
            } catch (Exception | StackOverflowError e) {
                // Lumina: never let an unexpected error (permission denied on a
                // subfolder, a broken symlink, too-deep recursion, etc.) kill the
                // whole app. Log it and fall back to whatever we found so far.
                android.util.Log.e("LibraryActivity", "Erro ao escanear jogos", e);
            }

            List<GameItem> finalGames = games;
            runOnUiThread(() -> {
                if (finalGames.isEmpty()) {
                    showEmptyState();
                } else {
                    showGames(finalGames);
                }
            });
        }).start();
    }

    private void collectGames(File folder, List<GameItem> out, int depth) {
        File[] children;
        try {
            children = folder.listFiles();
        } catch (SecurityException e) {
            // Some subfolders (e.g. Android/data, Android/obb) are off-limits even
            // with MANAGE_EXTERNAL_STORAGE on newer Android versions. Skip them
            // instead of crashing.
            android.util.Log.w("LibraryActivity", "Sem acesso a: " + folder, e);
            return;
        }
        if (children == null) return;

        for (File child : children) {
            try {
                if (child.isDirectory()) {
                    // Lumina: some extraction tools produce a folder containing a PSP_GAME
                    // subfolder (the same layout as a real UMD disc image extracted to disk),
                    // e.g. "PSP/GAME/<Game Name>/PSP_GAME/SYSDIR/EBOOT.BIN". PPSSPP's native
                    // core (Core/Loaders.cpp) already knows how to boot straight from such a
                    // folder, but this scan previously only matched loose .iso/.cso/etc. files,
                    // so these folders were silently skipped. Detect them here and treat the
                    // folder itself as the launchable game, without recursing further into it.
                    if (isExtractedUmdFolder(child)) {
                        Uri cover = findCoverFor(folder, child.getName());
                        out.add(new GameItem(child.getName(), Uri.fromFile(child), folderSizeBytes(child), "umd", cover));
                        continue;
                    }
                    // Skip Android's own restricted directories to avoid SecurityExceptions
                    // and pointless deep recursion into unrelated app data.
                    if ("Android".equals(child.getName()) && depth == 0) {
                        continue;
                    }
                    if (depth < MAX_SCAN_DEPTH) {
                        collectGames(child, out, depth + 1);
                    }
                    continue;
                }
                String name = child.getName();
                String ext = extensionOf(name);
                if (SUPPORTED_EXTENSIONS.contains(ext)) {
                    Uri cover = findCoverFor(folder, name);
                    out.add(new GameItem(name, Uri.fromFile(child), child.length(), ext, cover));
                }
            } catch (Exception e) {
                // Never let a single bad entry (unreadable file, broken symlink, etc.)
                // abort the whole scan.
                android.util.Log.w("LibraryActivity", "Erro ao processar: " + child, e);
            }
        }
    }

    /**
     * True if this folder is (or directly contains) a PSP_GAME directory, matching the
     * layout PPSSPP's native loader already recognizes for an extracted UMD image.
     */
    private boolean isExtractedUmdFolder(File folder) {
        File direct = new File(folder, "PSP_GAME");
        if (direct.isDirectory()) {
            return true;
        }
        // Also match when the folder IS itself named PSP_GAME (nested one level deeper
        // than expected), as seen with some extraction tools.
        return "PSP_GAME".equalsIgnoreCase(folder.getName()) && new File(folder, "SYSDIR").isDirectory();
    }

    private long folderSizeBytes(File folder) {
        long total = 0;
        File[] children = folder.listFiles();
        if (children == null) return 0;
        for (File child : children) {
            total += child.isDirectory() ? folderSizeBytes(child) : child.length();
        }
        return total;
    }

    @Nullable
    private Uri findCoverFor(File folder, String gameFileName) {
        int dot = gameFileName.lastIndexOf('.');
        String baseName = dot > 0 ? gameFileName.substring(0, dot) : gameFileName;

        for (String imgExt : IMAGE_EXTENSIONS) {
            File candidate = new File(folder, baseName + "." + imgExt);
            if (candidate.exists()) {
                return Uri.fromFile(candidate);
            }
        }

        for (String folderName : COVER_FOLDER_NAMES) {
            File coversFolder = new File(folder, folderName);
            if (coversFolder.isDirectory()) {
                for (String imgExt : IMAGE_EXTENSIONS) {
                    File candidate = new File(coversFolder, baseName + "." + imgExt);
                    if (candidate.exists()) {
                        return Uri.fromFile(candidate);
                    }
                }
            }
        }

        return null;
    }

    private static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) return "";
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private void showLoading() {
        progressBar.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
        emptyState.setVisibility(View.GONE);
        pickFolderState.setVisibility(View.GONE);
    }

    private void showPickFolderState() {
        progressBar.setVisibility(View.GONE);
        recyclerView.setVisibility(View.GONE);
        emptyState.setVisibility(View.GONE);
        pickFolderState.setVisibility(View.VISIBLE);
    }

    private void showEmptyState() {
        progressBar.setVisibility(View.GONE);
        recyclerView.setVisibility(View.GONE);
        pickFolderState.setVisibility(View.GONE);
        emptyState.setVisibility(View.VISIBLE);
    }

    private void showGames(List<GameItem> games) {
        progressBar.setVisibility(View.GONE);
        emptyState.setVisibility(View.GONE);
        pickFolderState.setVisibility(View.GONE);
        recyclerView.setVisibility(View.VISIBLE);
        recyclerView.setAdapter(new GameAdapter(games));

        GameItem first = games.get(0);
        heroTitle.setText(first.getTitle());
        heroSubtitle.setText(games.size() == 1
                ? "1 jogo na sua biblioteca"
                : games.size() + " jogos na sua biblioteca");

        heroPlayButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, PpssppActivity.class);
            intent.setData(first.contentUri);
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        });
    }
}
