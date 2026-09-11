package org.ppsspp.ppsspp;

/**
 * Representa uma conta salva localmente no aparelho (GMP Gameport).
 *
 * Por enquanto essas contas vivem só no SharedPreferences do dispositivo
 * (ver AccountStore) -- não há backend real ainda. Quando o Supabase for
 * conectado, esta classe deve continuar representando "a conta logada
 * neste aparelho", só que os dados passam a vir do servidor em vez de
 * serem inventados localmente no primeiro login.
 */
public class LocalAccount {

    public final String id;
    public String displayName;
    public String email;

    public LocalAccount(String id, String displayName, String email) {
        this.id = id;
        this.displayName = displayName;
        this.email = email;
    }

    /** Inicial usada no avatar circular quando não há foto (ex: "L" para "Luan"). */
    public String initial() {
        if (displayName != null && !displayName.isEmpty()) {
            return displayName.substring(0, 1).toUpperCase();
        }
        if (email != null && !email.isEmpty()) {
            return email.substring(0, 1).toUpperCase();
        }
        return "?";
    }
}
