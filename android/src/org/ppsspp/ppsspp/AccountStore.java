package org.ppsspp.ppsspp;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Guarda a lista de contas (perfis) já usadas neste aparelho, e qual delas
 * está ativa agora -- como o seletor de perfil da Netflix/PlayStation.
 *
 * FASE ATUAL: tudo 100% local (SharedPreferences), sem servidor. O usuário
 * faz "login" preenchendo o formulário uma vez; a partir daí o perfil fica
 * salvo no aparelho e aparece no seletor nas próximas vezes, sem precisar
 * digitar e-mail/senha de novo.
 *
 * FASE FUTURA (quando o Supabase for conectado): o método addOrUpdateAccount
 * deve passar a ser alimentado pela resposta real do servidor (id do
 * usuário, nome, e-mail confirmados), em vez de aceitar qualquer valor
 * digitado. O restante (lista de perfis, perfil ativo, "adicionar conta")
 * continua igual.
 */
public class AccountStore {

    private static final String PREFS_NAME = "gmp_accounts";
    private static final String KEY_ACCOUNTS = "accounts_json";
    private static final String KEY_ACTIVE_ID = "active_account_id";

    private final SharedPreferences prefs;

    public AccountStore(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** Todas as contas já usadas neste aparelho, na ordem em que foram adicionadas. */
    public List<LocalAccount> getAccounts() {
        List<LocalAccount> result = new ArrayList<>();
        String raw = prefs.getString(KEY_ACCOUNTS, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                result.add(new LocalAccount(
                        obj.getString("id"),
                        obj.optString("displayName", ""),
                        obj.optString("email", "")
                ));
            }
        } catch (JSONException e) {
            // Dados corrompidos: melhor voltar com lista vazia do que travar o app.
        }
        return result;
    }

    /**
     * Cria uma conta nova ou atualiza uma existente com o mesmo e-mail, e a
     * marca como ativa. É isso que o formulário de login chama ao terminar.
     */
    public LocalAccount addOrUpdateAccount(String email, String displayName) {
        List<LocalAccount> accounts = getAccounts();

        for (LocalAccount acc : accounts) {
            if (acc.email != null && acc.email.equalsIgnoreCase(email)) {
                acc.displayName = displayName;
                saveAccounts(accounts);
                setActiveAccountId(acc.id);
                return acc;
            }
        }

        LocalAccount created = new LocalAccount(UUID.randomUUID().toString(), displayName, email);
        accounts.add(created);
        saveAccounts(accounts);
        setActiveAccountId(created.id);
        return created;
    }

    public LocalAccount getActiveAccount() {
        String activeId = prefs.getString(KEY_ACTIVE_ID, null);
        if (activeId == null) return null;
        for (LocalAccount acc : getAccounts()) {
            if (acc.id.equals(activeId)) return acc;
        }
        return null;
    }

    public void setActiveAccountId(String id) {
        prefs.edit().putString(KEY_ACTIVE_ID, id).apply();
    }

    /** "Sair" só desmarca o perfil ativo -- a conta continua salva no seletor. */
    public void clearActiveAccount() {
        prefs.edit().remove(KEY_ACTIVE_ID).apply();
    }

    /** Remove a conta por completo do aparelho (ex: "remover perfil"). */
    public void removeAccount(String id) {
        List<LocalAccount> accounts = getAccounts();
        accounts.removeIf(acc -> acc.id.equals(id));
        saveAccounts(accounts);
        if (id.equals(prefs.getString(KEY_ACTIVE_ID, null))) {
            clearActiveAccount();
        }
    }

    public boolean hasAnyAccount() {
        return !getAccounts().isEmpty();
    }

    private void saveAccounts(List<LocalAccount> accounts) {
        JSONArray array = new JSONArray();
        try {
            for (LocalAccount acc : accounts) {
                JSONObject obj = new JSONObject();
                obj.put("id", acc.id);
                obj.put("displayName", acc.displayName);
                obj.put("email", acc.email);
                array.put(obj);
            }
        } catch (JSONException e) {
            // Não deveria acontecer com esses campos simples; se acontecer,
            // preferimos salvar o que der certo a travar o app.
        }
        prefs.edit().putString(KEY_ACCOUNTS, array.toString()).apply();
    }
}
