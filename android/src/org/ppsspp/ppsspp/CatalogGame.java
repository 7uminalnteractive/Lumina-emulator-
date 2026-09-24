package org.ppsspp.ppsspp;

import java.util.Collections;
import java.util.Locale;
import java.util.Set;

/**
 * Um item do catálogo do GMP que o usuário pode baixar (em oposição a GameItem,
 * que é um jogo JÁ instalado no aparelho).
 *
 * Java puro (sem Android) para poder ser testado no computador.
 */
final class CatalogGame {

    final String id;
    final String title;
    /** Tipo do conteúdo; decide a pasta (ver GmpFolders): game, save, texture, system. */
    final String kind;
    /** Nome do arquivo final, ex.: "The Best Conmebol.gmp". Já validado. */
    final String fileName;
    /** Tamanho em bytes; 0 = desconhecido. */
    final long sizeBytes;
    /** SHA-256 em hexadecimal; vazio = não conferir (só aceitável no catálogo de teste). */
    final String sha256;
    /** Link direto (catálogo de teste). No servidor real vem de um link assinado. */
    final String downloadUrl;
    /** Patches/produtos que liberam este item (ex.: "conmebol", "europeu"). */
    final Set<String> unlockedByProducts;
    /** Planos que liberam este item. "*" = qualquer plano ativo. */
    final Set<String> unlockedByPlans;

    CatalogGame(String id, String title, String kind, String fileName, long sizeBytes,
                String sha256, String downloadUrl,
                Set<String> unlockedByProducts, Set<String> unlockedByPlans) {
        this.id = id;
        this.title = title;
        this.kind = kind;
        this.fileName = fileName;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256 == null ? "" : sha256;
        this.downloadUrl = downloadUrl == null ? "" : downloadUrl;
        this.unlockedByProducts = Collections.unmodifiableSet(unlockedByProducts);
        this.unlockedByPlans = Collections.unmodifiableSet(unlockedByPlans);
    }

    /** "909 MB" / "1,1 GB" (vírgula, como o resto do app). Vazio se desconhecido. */
    String sizeLabel() {
        if (sizeBytes <= 0) {
            return "";
        }
        double gb = sizeBytes / (1024.0 * 1024.0 * 1024.0);
        if (gb >= 1.0) {
            return String.format(Locale.US, "%.1f GB", gb).replace('.', ',');
        }
        return Math.max(1, Math.round(sizeBytes / (1024.0 * 1024.0))) + " MB";
    }
}
