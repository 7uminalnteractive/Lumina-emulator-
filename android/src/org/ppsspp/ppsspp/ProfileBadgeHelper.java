package org.ppsspp.ppsspp;

import android.view.View;
import android.widget.TextView;

/**
 * GMP Gameport: centraliza como a Biblioteca, a Loja e a tela de Conta
 * mostram a conta ativa (inicial no avatar, ponto de status online/offline).
 *
 * Antes, cada tela tinha o nome "G" fixo no layout e o ponto verde sempre
 * ligado, sem nenhuma leitura real da conta. Agora as três chamam este
 * mesmo helper, então o comportamento fica igual em todo lugar e uma
 * futura mudança (ex: avatar com foto de verdade vindo do Supabase) só
 * precisa ser feita aqui.
 */
final class ProfileBadgeHelper {

    private ProfileBadgeHelper() {
    }

    /**
     * Preenche a inicial do avatar e o ponto de status a partir da conta
     * ativa. Se não houver conta ativa (não deveria acontecer nas telas
     * pós-login, mas é tratado com segurança), mostra "?" e status offline.
     */
    static void bind(TextView avatarView, View statusDotView, LocalAccount active) {
        if (avatarView != null) {
            avatarView.setText(active != null ? active.initial() : "?");
        }
        if (statusDotView != null) {
            boolean online = active != null && active.online;
            statusDotView.setBackgroundResource(
                    online ? R.drawable.lumina_online_dot : R.drawable.lumina_offline_dot);
        }
    }
}
