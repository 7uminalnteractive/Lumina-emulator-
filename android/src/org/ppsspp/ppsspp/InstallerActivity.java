package org.ppsspp.ppsspp;

import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * GMP Gameport: o Instalador. A Biblioteca abre esta tela ao tocar em "Baixar".
 *
 * Fluxo: confere o acesso do usuário -> descobre a pasta pelo TIPO do conteúdo
 * (GmpFolders) -> baixa com retomada e confere o hash (GameInstaller) -> o
 * arquivo aparece na Biblioteca.
 *
 * Sair (voltar) PAUSA o download: o .part fica no disco e "Baixar" de novo
 * continua de onde parou. Enquanto a tela está aberta, o aparelho não apaga
 * a tela (senão o Android pode suspender o app no meio de um download de ~1 GB).
 *
 * LIMITAÇÃO conhecida: o download vive nesta tela. Se o usuário sair do app, o
 * Android pode encerrá-lo; a retomada cobre isso, mas o ideal a seguir é um
 * serviço em primeiro plano com notificação.
 */
public class InstallerActivity extends AppCompatActivity {

    static final String EXTRA_GAME_ID = "game_id";
    static final String EXTRA_GAME_TITLE = "game_title";
    private static final String TAG = "InstallerActivity";

    /**
     * Downloads em andamento neste processo. Impede que duas threads escrevam no
     * mesmo .part (ex.: usuário sai, a thread antiga ainda está terminando, ele
     * volta e toca em Baixar de novo) -- isso corromperia o arquivo.
     */
    private static final Set<String> ACTIVE = new HashSet<>();

    private enum State {WORKING, DONE, ERROR}

    private final AtomicBoolean cancel = new AtomicBoolean(false);
    private volatile boolean running = false;
    private State state = State.WORKING;

    private String gameId;
    private TextView statusText;
    private TextView percentText;
    private TextView bytesText;
    private ProgressBar progressBar;
    private Button primaryButton;
    private Button secondaryButton;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_installer);
        GmpWindowHelper.applyFullscreenOnly(this, findViewById(R.id.installer_root));
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        TextView titleText = findViewById(R.id.installer_title);
        statusText = findViewById(R.id.installer_status);
        percentText = findViewById(R.id.installer_percent);
        bytesText = findViewById(R.id.installer_bytes);
        progressBar = findViewById(R.id.installer_progress);
        primaryButton = findViewById(R.id.installer_primary);
        secondaryButton = findViewById(R.id.installer_secondary);

        gameId = getIntent().getStringExtra(EXTRA_GAME_ID);
        String title = getIntent().getStringExtra(EXTRA_GAME_TITLE);
        titleText.setText(title != null ? title : "");
        if (gameId == null) {
            finish();
            return;
        }

        primaryButton.setOnClickListener(v -> onPrimaryClicked());
        secondaryButton.setOnClickListener(v -> finish());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                leave();
            }
        });

        startInstall();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            GmpWindowHelper.reapplyImmersive(this);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (running) {
            cancel.set(true); // a thread termina sozinha; o .part fica para retomar
        }
    }

    // ------------------------------------------------------------------ ações

    private void onPrimaryClicked() {
        switch (state) {
            case WORKING:
                leave();
                break;
            case DONE:
                setResult(RESULT_OK);
                finish();
                break;
            case ERROR:
                startInstall();
                break;
        }
    }

    private void leave() {
        if (running) {
            cancel.set(true);
            Toast.makeText(this, "Download pausado. Toque em Baixar de novo para continuar.",
                    Toast.LENGTH_LONG).show();
        }
        finish();
    }

    private void startInstall() {
        if (running) {
            return;
        }
        synchronized (ACTIVE) {
            if (!ACTIVE.add(gameId)) {
                showError("Este download ainda está sendo finalizado. Aguarde alguns segundos e tente de novo.");
                return;
            }
        }
        cancel.set(false);
        running = true;
        showWorking("Verificando seu acesso…");
        new Thread(this::runInstall, "gmp-installer").start();
    }

    // ------------------------------------------------------- thread de fundo

    private void runInstall() {
        try {
            final LocalAccount account = new AccountStore(this).getActiveAccount();
            if (account == null || account.email == null) {
                throw new IOException("Entre na sua conta para baixar.");
            }

            final CatalogSource source = GmpCatalog.source(getApplicationContext());
            CatalogGame found = null;
            List<CatalogGame> allowed = source.fetchDownloadableGames(account.email);
            for (CatalogGame g : allowed) {
                if (g.id.equals(gameId)) {
                    found = g;
                    break;
                }
            }
            if (found == null) {
                throw new IOException("Você não tem acesso a este jogo.");
            }
            final CatalogGame game = found;

            String relative = GmpFolders.relativeFolderFor(game.kind);
            if (relative == null) {
                throw new IOException("Tipo de conteúdo não suportado: " + game.kind);
            }
            File destDir = new File(new File(Environment.getExternalStorageDirectory(), "GMP"), relative);

            ui(() -> showWorking("Baixando…"));
            GameInstaller.install(
                    () -> source.resolveDownloadUrl(account.email, game),
                    destDir, game.fileName, game.sizeBytes, game.sha256,
                    (phase, done, total) -> ui(() -> showProgress(phase, done, total)),
                    cancel);

            ui(this::showDone);
        } catch (GameInstaller.CancelledException e) {
            Log.i(TAG, "Download pausado pelo usuário");
        } catch (IOException e) {
            Log.w(TAG, "Falha na instalação", e);
            final String message = friendlyMessage(e);
            ui(() -> showError(message));
        } catch (RuntimeException e) {
            Log.e(TAG, "Erro inesperado na instalação", e);
            ui(() -> showError("Erro inesperado ao instalar. Tente de novo."));
        } finally {
            synchronized (ACTIVE) {
                ACTIVE.remove(gameId);
            }
            running = false;
        }
    }

    private void ui(Runnable action) {
        runOnUiThread(() -> {
            if (!isFinishing() && !isDestroyed()) {
                action.run();
            }
        });
    }

    // -------------------------------------------------------------- estados

    private void showWorking(String status) {
        state = State.WORKING;
        statusText.setText(status);
        progressBar.setIndeterminate(true);
        percentText.setText("");
        bytesText.setText("");
        primaryButton.setText("Pausar e sair");
        secondaryButton.setVisibility(View.GONE);
    }

    private void showProgress(GameInstaller.Phase phase, long done, long total) {
        statusText.setText(phase == GameInstaller.Phase.VERIFYING
                ? "Verificando integridade do arquivo…" : "Baixando…");
        if (total > 0) {
            int permille = (int) Math.min(1000, done * 1000 / total);
            progressBar.setIndeterminate(false);
            progressBar.setMax(1000);
            progressBar.setProgress(permille);
            percentText.setText((permille / 10) + "%");
            bytesText.setText(formatMb(done) + " de " + formatMb(total));
        } else {
            progressBar.setIndeterminate(true);
            percentText.setText("");
            bytesText.setText(formatMb(done));
        }
    }

    private void showDone() {
        state = State.DONE;
        statusText.setText("Pronto! O jogo já está na sua Biblioteca.");
        progressBar.setIndeterminate(false);
        progressBar.setMax(1000);
        progressBar.setProgress(1000);
        percentText.setText("100%");
        primaryButton.setText("Concluir");
        secondaryButton.setVisibility(View.GONE);
    }

    private void showError(String message) {
        state = State.ERROR;
        statusText.setText(message);
        progressBar.setIndeterminate(false);
        progressBar.setProgress(0);
        percentText.setText("");
        bytesText.setText("");
        primaryButton.setText("Tentar de novo");
        secondaryButton.setVisibility(View.VISIBLE);
    }

    // -------------------------------------------------------------- textos

    private static String friendlyMessage(IOException e) {
        if (e instanceof GameInstaller.HttpStatusException) {
            int code = ((GameInstaller.HttpStatusException) e).code;
            if (code == 401 || code == 403) {
                return "Seu acesso a este download foi negado ou o link expirou. Tente de novo em instantes.";
            }
            if (code == 404) {
                return "O arquivo não foi encontrado no servidor.";
            }
            return "O servidor respondeu com erro (" + code + "). Tente de novo mais tarde.";
        }
        if (e instanceof UnknownHostException || e instanceof SocketTimeoutException
                || e instanceof ConnectException) {
            return "Sem conexão com a internet. O que já foi baixado foi mantido; tente de novo.";
        }
        String message = e.getMessage();
        return (message == null || message.isEmpty())
                ? "Não foi possível concluir o download." : message;
    }

    private static String formatMb(long bytes) {
        double mb = bytes / (1024.0 * 1024.0);
        if (mb >= 1024) {
            return String.format(Locale.US, "%.2f GB", mb / 1024.0).replace('.', ',');
        }
        return String.format(Locale.US, "%.0f MB", mb);
    }
}
