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

    /**
     * Status online real da conta (não apenas um ponto verde fixo na UI).
     *
     * Persistido junto com o resto da conta em AccountStore. Por padrão toda
     * conta nova nasce online = true. Quando o backend/Supabase de presença
     * real existir, este campo passa a ser atualizado por ele; até lá, ele
     * apenas reflete o valor salvo localmente.
     */
    public boolean online;

    public LocalAccount(String id, String displayName, String email) {
        this(id, displayName, email, true);
    }

    public LocalAccount(String id, String displayName, String email, boolean online) {
        this.id = id;
        this.displayName = displayName;
        this.email = email;
        this.online = online;
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
