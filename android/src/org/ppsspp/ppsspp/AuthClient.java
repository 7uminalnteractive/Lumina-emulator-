package org.ppsspp.ppsspp;

import android.os.Handler;
import android.os.Looper;

/**
 * Autenticação do GMP Gameport.
 *
 * FASE ATUAL: não existe backend ainda. Este cliente aceita qualquer
 * e-mail em formato válido + senha não vazia, e "loga" localmente,
 * criando/reconhecendo o perfil no AccountStore. Isso existe para o
 * app já funcionar de ponta a ponta (login → seletor de perfil →
 * biblioteca) enquanto o Supabase não está pronto.
 *
 * FASE FUTURA: quando o Supabase (ou outro backend) for conectado,
 * a implementação de signIn() deve ser trocada para uma chamada de
 * rede de verdade -- a assinatura do método (mesmos parâmetros,
 * mesmo AuthCallback) foi pensada para que LoginActivity não precise
 * mudar quase nada nesse momento.
 */
public class AuthClient {

    public interface AuthCallback {
        void onSuccess(LocalAccount account);
        void onError(String message);
    }

    private final AccountStore accountStore;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public AuthClient(AccountStore accountStore) {
        this.accountStore = accountStore;
    }

    public void signIn(String email, String password, AuthCallback callback) {
        // Simula uma pequena latência de rede para o loading não "piscar"
        // instantaneamente -- fica mais fácil perceber se o spinner está
        // funcionando, e já deixa o fluxo parecido com uma chamada real.
        mainHandler.postDelayed(() -> {
            String displayName = guessDisplayNameFromEmail(email);
            LocalAccount account = accountStore.addOrUpdateAccount(email, displayName);
            callback.onSuccess(account);
        }, 450);
    }

    public void sendPasswordReset(String email, Runnable onDone) {
        // Sem backend ainda, então não há e-mail de verdade para enviar.
        // Mantemos o mesmo atraso simulado para a UI se comportar igual
        // ao fluxo real (mostra loading, depois mostra confirmação).
        mainHandler.postDelayed(onDone, 450);
    }

    private String guessDisplayNameFromEmail(String email) {
        if (email == null || !email.contains("@")) return "Jogador";
        String namePart = email.substring(0, email.indexOf('@'));
        if (namePart.isEmpty()) return "Jogador";
        return Character.toUpperCase(namePart.charAt(0)) + namePart.substring(1);
    }
}
