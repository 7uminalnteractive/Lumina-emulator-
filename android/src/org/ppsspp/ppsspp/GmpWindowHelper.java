package org.ppsspp.ppsspp;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.Window;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/**
 * GMP Gameport: deixa as telas do launcher (Login, Perfis, Biblioteca, Loja,
 * Conta) realmente em TELA CHEIA, em vez de parar antes do notch/câmera e da
 * barra de navegação.
 *
 * Por que isso existia como problema:
 *  - O tema (values-v28/styles.xml) já pede windowLayoutInDisplayCutoutMode =
 *    shortEdges, mas isso só PERMITE desenhar sob o notch. O app ainda precisa
 *    dizer que quer desenhar por baixo das barras do sistema
 *    (setDecorFitsSystemWindows(false)). Sem isso, o Android deixa uma faixa
 *    cinza/preta onde o notch está -- era o "cinza" na lateral esquerda.
 *  - A barra de navegação (gestos) ficava por cima do último item da sidebar,
 *    cortando o ícone de perfil.
 *
 * O que este helper faz:
 *  1. Estende a janela por baixo do status bar, da nav bar e do notch.
 *  2. Esconde as barras (modo imersivo). O usuário ainda as vê ao deslizar
 *     da borda (BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE), igual ao emulador.
 *  3. Aplica os insets como PADDING apenas na barra lateral e no conteúdo
 *     que precisam se manter longe do notch/gesto, para que nenhum ícone
 *     fique cortado -- mas o FUNDO continua ocupando 100% da tela.
 */
final class GmpWindowHelper {

    private GmpWindowHelper() {
    }

    /**
     * Chame logo depois de setContentView().
     *
     * @param activity   a tela
     * @param root       a view raiz do layout (usada para receber os insets)
     * @param sidebar    a barra lateral (pode ser null nas telas sem sidebar).
     *                   Recebe padding do notch (esquerda) e da nav bar
     *                   (embaixo), para o ícone de perfil nunca ser cortado.
     * @param content    a área de conteúdo (pode ser null). Recebe só o padding
     *                   da direita e de baixo, para não ficar sob o notch/gesto.
     */
    static void apply(Activity activity, View root, View sidebar, View content) {
        Window window = activity.getWindow();

        // (1) Desenhar por baixo das barras do sistema.
        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Sem isso o Android escurece a barra de gestos automaticamente,
            // recriando a faixa preta que estamos tentando eliminar.
            window.setNavigationBarContrastEnforced(false);
            window.setStatusBarContrastEnforced(false);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Reforça o que o tema já declara, caso o tema seja trocado depois.
            window.getAttributes().layoutInDisplayCutoutMode =
                    android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        // (2) Modo imersivo: esconde as barras, mas elas voltam com um swipe.
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(window, window.getDecorView());
        controller.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        controller.hide(WindowInsetsCompat.Type.systemBars());

        // (3) Insets viram padding só onde é necessário.
        final int sidebarPadL = sidebar != null ? sidebar.getPaddingLeft() : 0;
        final int sidebarPadT = sidebar != null ? sidebar.getPaddingTop() : 0;
        final int sidebarPadB = sidebar != null ? sidebar.getPaddingBottom() : 0;
        final int contentPadR = content != null ? content.getPaddingRight() : 0;
        final int contentPadB = content != null ? content.getPaddingBottom() : 0;

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            // displayCutout inclui o notch/câmera; systemBars inclui status/nav.
            // Mesmo com as barras escondidas, o notch continua existindo, então
            // somamos os dois para nunca cortar nada.
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets cutout = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout());
            int left = Math.max(bars.left, cutout.left);
            int top = Math.max(bars.top, cutout.top);
            int right = Math.max(bars.right, cutout.right);
            int bottom = Math.max(bars.bottom, cutout.bottom);

            if (sidebar != null) {
                sidebar.setPadding(
                        sidebarPadL + left,
                        sidebarPadT + top,
                        sidebar.getPaddingRight(),
                        sidebarPadB + bottom);
            }
            if (content != null) {
                content.setPadding(
                        content.getPaddingLeft(),
                        content.getPaddingTop(),
                        contentPadR + right,
                        contentPadB + bottom);
            }
            // Telas sem sidebar/conteúdo dedicado (Login/Perfil/Conta) NÃO recebem
            // padding no root: o fundo delas precisa cobrir 100% da tela, inclusive
            // sob o notch. O conteúdo dessas telas já é centralizado ou tem padding
            // próprio suficiente para ficar longe das bordas.
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    /** Tela cheia simples, sem tratar insets (para telas com fundo full-bleed). */
    static void applyFullscreenOnly(Activity activity, View root) {
        apply(activity, root, null, null);
    }

    /**
     * Reaplica o modo imersivo. Chame em onWindowFocusChanged(true), porque o
     * Android mostra as barras de novo depois de um diálogo, permissão ou
     * retorno do jogo.
     */
    static void reapplyImmersive(Activity activity) {
        Window window = activity.getWindow();
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(window, window.getDecorView());
        controller.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        controller.hide(WindowInsetsCompat.Type.systemBars());
    }
}
