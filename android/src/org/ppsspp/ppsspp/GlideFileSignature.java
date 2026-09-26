package org.ppsspp.ppsspp;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.signature.ObjectKey;

import java.io.File;

/**
 * GMP Gameport: assinatura de cache do Glide para as capas (ICON0/PIC1) lidas
 * direto de arquivos locais.
 *
 * Por padrão o Glide usa o Uri (o caminho do arquivo) como chave de cache,
 * tanto em memória quanto em disco, e assume que o conteúdo naquele caminho
 * nunca muda. Então se o usuário troca o ICON0.PNG/PIC1.PNG de um jogo (ou
 * apaga e recria a pasta com o mesmo nome, ou apenas renomeia o .iso dentro
 * de uma pasta cujo ICON0/PIC1 continuam com o mesmo caminho), o Glide
 * continua mostrando o bitmap antigo -- a Biblioteca fica com a capa "errada"
 * até o cache do app ser limpo manualmente.
 *
 * Passando esta assinatura (baseada na data de modificação + tamanho do
 * arquivo) em .signature(...), o Glide só reaproveita o cache quando o
 * arquivo realmente não mudou; qualquer alteração de conteúdo gera uma nova
 * chave e força a releitura do disco.
 */
final class GlideFileSignature {

    private GlideFileSignature() {
    }

    @NonNull
    static ObjectKey forFileUri(@Nullable Uri uri) {
        if (uri == null) {
            return new ObjectKey("no-uri");
        }
        String path = uri.getPath();
        if (path == null) {
            // Uri sem caminho local (ex.: content://) -- usa o Uri inteiro como
            // chave; não temos como checar mtime/tamanho aqui.
            return new ObjectKey(uri.toString());
        }
        File file = new File(path);
        // lastModified() e length() retornam 0 se o arquivo não existir, o que
        // já é uma chave estável o bastante para esse caso.
        return new ObjectKey(path + "@" + file.lastModified() + "_" + file.length());
    }
}
