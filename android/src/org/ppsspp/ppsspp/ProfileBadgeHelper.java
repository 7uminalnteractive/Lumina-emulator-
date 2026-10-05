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
 * mostram a conta ativa no avatar (foto, se o usuário escolheu uma, senão a
 * inicial do nome).
 *
 * O parâmetro statusDotView continua existindo por compatibilidade com as
 * três telas que o passam, mas o ponto verde/cinza de online-offline foi
 * removido a pedido do usuário -- bind() sempre o esconde agora.
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
            // GMP Gameport: a bolinha de status online/offline foi removida
            // a pedido do usuário -- fica sempre escondida, em todo lugar
            // que usa este helper (Biblioteca, Loja, Conta).
            statusDotView.setVisibility(View.GONE);
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
