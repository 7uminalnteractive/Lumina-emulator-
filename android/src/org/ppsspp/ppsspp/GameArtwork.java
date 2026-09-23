package org.ppsspp.ppsspp;

import android.net.Uri;

import androidx.annotation.Nullable;

import java.io.File;
import java.util.Locale;

/**
 * GMP Gameport: localiza as duas imagens que o próprio formato PSP já define
 * para cada jogo, em vez de depender de arquivos de capa soltos.
 *
 *   ICON0.PNG  -- o ícone quadrado do jogo (é o "card azul" da Biblioteca).
 *   PIC1.PNG   -- a imagem de fundo grande do jogo (vai no banner "hero").
 *
 * Onde procuramos, nesta ordem, dentro da pasta do jogo (ex.:
 * Jogo/Game/The Best Conmebol/):
 *   1. dentro de PSP_GAME:            <pasta>/PSP_GAME/ICON0.PNG   (layout do instalador)
 *   2. na própria pasta do jogo:      <pasta>/ICON0.png
 *
 * O nome da subpasta também é aceito como "Psp Game" (com espaço) ou
 * "PSP-GAME": qualquer variação de maiúsculas e separador ("_", " ", "-")
 * entre PSP e GAME é tratada como a mesma pasta.
 *
 * O nome é comparado SEM diferenciar maiúsculas/minúsculas, porque o PSP
 * original grava "ICON0.PNG" em maiúsculas, enquanto o instalador do GMP pode
 * gravar "ICON0.png". No Android (ext4/sdcardfs) "ICON0.png" e "ICON0.PNG"
 * são arquivos diferentes, então um simples new File(dir, "ICON0.PNG")
 * não bastaria.
 */
final class GameArtwork {

    static final String ICON_NAME = "ICON0";
    static final String BACKGROUND_NAME = "PIC1";

    private static final String[] EXTENSIONS = {"png", "jpg", "jpeg", "webp"};

    private GameArtwork() {
    }

    /** Ícone (ICON0) da pasta de jogo, ou null se não existir. */
    @Nullable
    static Uri findIcon(File gameFolder) {
        return find(gameFolder, ICON_NAME);
    }

    /** Fundo (PIC1) da pasta de jogo, ou null se não existir. */
    @Nullable
    static Uri findBackground(File gameFolder) {
        return find(gameFolder, BACKGROUND_NAME);
    }

    @Nullable
    private static Uri find(File gameFolder, String baseName) {
        if (gameFolder == null || !gameFolder.isDirectory()) {
            return null;
        }
        File hit = null;
        File pspGame = findPspGameDirectory(gameFolder);
        if (pspGame != null) {
            hit = findInDirectory(pspGame, baseName);
        }
        if (hit == null) {
            hit = findInDirectory(gameFolder, baseName);
        }
        return hit != null ? Uri.fromFile(hit) : null;
    }

    @Nullable
    private static File findInDirectory(File dir, String baseName) {
        File[] children = dir.listFiles();
        if (children == null) {
            return null;
        }
        for (File child : children) {
            if (!child.isFile()) {
                continue;
            }
            String name = child.getName();
            int dot = name.lastIndexOf('.');
            if (dot <= 0) {
                continue;
            }
            String stem = name.substring(0, dot);
            String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
            if (!stem.equalsIgnoreCase(baseName)) {
                continue;
            }
            for (String allowed : EXTENSIONS) {
                if (allowed.equals(ext)) {
                    return child;
                }
            }
        }
        return null;
    }

    /**
     * Acha a subpasta "PSP_GAME" tolerando "Psp Game", "psp-game", "PSP_GAME"...
     * Normaliza removendo tudo que não for letra/dígito e comparando sem caixa.
     */
    @Nullable
    static File findPspGameDirectory(File dir) {
        File[] children = dir.listFiles();
        if (children == null) {
            return null;
        }
        for (File child : children) {
            if (child.isDirectory() && isPspGameFolderName(child.getName())) {
                return child;
            }
        }
        return null;
    }

    /** True para "PSP_GAME", "Psp Game", "psp-game", "PSPGAME", etc. */
    static boolean isPspGameFolderName(String name) {
        StringBuilder sb = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                sb.append(Character.toLowerCase(c));
            }
        }
        return "pspgame".contentEquals(sb);
    }
}
