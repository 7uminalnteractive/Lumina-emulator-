package org.ppsspp.ppsspp;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.view.View;
import android.widget.TextView;

import java.io.File;

/**
 * GMP Gameport: centraliza como a Biblioteca, a Loja e a tela de Conta
 * mostram a conta ativa (foto ou inicial no avatar, ponto de status
 * online/offline).
 *
 * Antes, cada tela tinha o nome "G" fixo no layout e o ponto verde sempre
 * ligado, sem nenhuma leitura real da conta. Agora as três chamam este
 * mesmo helper, então o comportamento fica igual em todo lugar.
 */
final class ProfileBadgeHelper {

    private ProfileBadgeHelper() {
    }

    /**
     * Preenche o avatar (foto, se o usuário escolheu uma em "Minha conta";
     * senão a inicial) e o ponto de status a partir da conta ativa. Se não
     * houver conta ativa (não deveria acontecer nas telas pós-login, mas é
     * tratado com segurança), mostra "?" e status offline.
     */
    static void bind(TextView avatarView, View statusDotView, LocalAccount active) {
        if (avatarView != null) {
            Bitmap photo = loadCircularPhoto(active);
            if (photo != null) {
                avatarView.setText("");
                avatarView.setBackground(new BitmapDrawable(avatarView.getResources(), photo));
            } else {
                // Sem foto: volta pro fundo circular padrão (cor sólida) e a inicial --
                // importante reverter aqui porque a mesma TextView é reusada pela
                // Activity ao recarregar (ex: depois de trocar/remover a foto).
                avatarView.setBackgroundResource(R.drawable.gmp_avatar_bg);
                avatarView.setText(active != null ? active.initial() : "?");
            }
        }
        if (statusDotView != null) {
            boolean online = active != null && active.online;
            statusDotView.setBackgroundResource(
                    online ? R.drawable.lumina_online_dot : R.drawable.lumina_offline_dot);
        }
    }

    /** Lê e recorta em círculo a foto de perfil da conta, ou null se não houver uma válida. */
    private static Bitmap loadCircularPhoto(LocalAccount active) {
        if (active == null || active.photoPath == null || active.photoPath.isEmpty()) {
            return null;
        }
        File file = new File(active.photoPath);
        if (!file.exists()) {
            return null;
        }
        Bitmap source = BitmapFactory.decodeFile(active.photoPath);
        if (source == null) {
            return null;
        }
        return cropToCircle(source);
    }

    private static Bitmap cropToCircle(Bitmap source) {
        int size = Math.min(source.getWidth(), source.getHeight());
        Bitmap output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        BitmapShader shader = new BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        paint.setShader(shader);

        float left = (source.getWidth() - size) / 2f;
        float top = (source.getHeight() - size) / 2f;
        canvas.translate(-left, -top);
        canvas.drawCircle(left + size / 2f, top + size / 2f, size / 2f, paint);
        return output;
    }
}
