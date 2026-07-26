package org.ppsspp.ppsspp;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Guarda a sessão do usuário localmente (SharedPreferences) para que ele
 * não precise fazer login toda vez que abrir o app ("permanecer conectado").
 */
public class SessionManager {

    private static final String PREFS_NAME = "lumina_session";

    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_DISPLAY_NAME = "display_name";

    private final SharedPreferences prefs;

    public SessionManager(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void saveSession(SupabaseAuthClient.AuthResult result) {
        prefs.edit()
                .putString(KEY_ACCESS_TOKEN, result.accessToken)
                .putString(KEY_REFRESH_TOKEN, result.refreshToken)
                .putString(KEY_USER_ID, result.userId)
                .putString(KEY_EMAIL, result.email)
                .putString(KEY_DISPLAY_NAME, result.displayName)
                .apply();
    }

    public boolean hasSession() {
        return prefs.contains(KEY_REFRESH_TOKEN);
    }

    public String getAccessToken() {
        return prefs.getString(KEY_ACCESS_TOKEN, null);
    }

    public String getRefreshToken() {
        return prefs.getString(KEY_REFRESH_TOKEN, null);
    }

    public String getEmail() {
        return prefs.getString(KEY_EMAIL, null);
    }

    public String getDisplayName() {
        return prefs.getString(KEY_DISPLAY_NAME, null);
    }

    public void clearSession() {
        prefs.edit().clear().apply();
    }
}
