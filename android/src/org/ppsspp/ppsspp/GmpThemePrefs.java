package org.ppsspp.ppsspp;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * GMP Gameport: guarda qual tema foi escolhido em "Minha conta > Trocar
 * tema" (AccountActivity), para ser aplicado nativamente assim que a lib
 * nativa carregar (ver PpssppActivity.Initialize()/onResume() e
 * NativeApp.setThemeName / app-android.cpp).
 *
 * Isto existe porque AccountActivity não carrega libppsspp_jni -- chamar
 * NativeApp.setThemeName direto de lá causaria UnsatisfiedLinkError. Mesma
 * ideia de AccountStore guardando a conta ativa pra PpssppActivity ler
 * depois, só que para o tema.
 */
final class GmpThemePrefs {

    private static final String PREFS_NAME = "gmp_theme";
    private static final String KEY_THEME_NAME = "theme_name";

    // GMP Gameport: único tema com nome confirmado neste build (ver
    // assets/themes/gmp_gameport.ini). Quando mais temas de marca forem
    // adicionados, só precisa crescer esta lista -- tanto aqui quanto em
    // AccountActivity.THEME_CHOICES.
    static final String DEFAULT_THEME_NAME = "GMP Gameport";

    private GmpThemePrefs() {
    }

    static String getSavedThemeName(Context context) {
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_THEME_NAME, DEFAULT_THEME_NAME);
    }

    static void setSavedThemeName(Context context, String themeName) {
        context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_THEME_NAME, themeName)
                .apply();
    }
}
