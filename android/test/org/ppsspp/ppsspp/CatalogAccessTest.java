package org.ppsspp.ppsspp;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Regra de acesso: patch OU plano. Rodar:
 *   javac -d out android/src/org/ppsspp/ppsspp/{CatalogGame,CatalogAccess,GmpFolders}.java \
 *                 android/test/org/ppsspp/ppsspp/CatalogAccessTest.java
 *   java -cp out org.ppsspp.ppsspp.CatalogAccessTest
 */
public class CatalogAccessTest {

    static int passed = 0, failed = 0;

    static Set<String> set(String... v) {
        return new HashSet<>(java.util.Arrays.asList(v));
    }

    static CatalogGame game(Set<String> products, Set<String> plans) {
        return new CatalogGame("g", "Jogo", "game", "Jogo.gmp", 0, "", "", products, plans);
    }

    public static void main(String[] args) {
        Set<String> none = Collections.emptySet();

        // Jogo liberado pelo patch "conmebol" e por qualquer plano.
        CatalogGame conmebol = game(set("conmebol"), set("*"));
        check("comprou o patch certo -> baixa", CatalogAccess.canDownload(conmebol, set("conmebol"), null));
        check("comprou OUTRO patch -> NÃO baixa", !CatalogAccess.canDownload(conmebol, set("europeu"), null));
        check("sem patch e sem plano -> NÃO baixa", !CatalogAccess.canDownload(conmebol, none, null));
        check("plano ativo (qualquer) -> baixa", CatalogAccess.canDownload(conmebol, none, "premium"));
        check("plano vazio conta como sem plano", !CatalogAccess.canDownload(conmebol, none, ""));

        // Jogo só de um plano específico (sem patch que libere).
        CatalogGame soPremium = game(none, set("premium"));
        check("plano específico certo -> baixa", CatalogAccess.canDownload(soPremium, none, "premium"));
        check("plano específico errado -> NÃO baixa", !CatalogAccess.canDownload(soPremium, none, "basico"));
        check("só patch, item de plano -> NÃO baixa", !CatalogAccess.canDownload(soPremium, set("conmebol"), null));

        // Jogo que nada libera nunca é baixável (fail-closed).
        CatalogGame ninguem = game(none, none);
        check("item que nada libera -> NÃO baixa (nem com patch+plano)",
                !CatalogAccess.canDownload(ninguem, set("conmebol", "europeu"), "premium"));

        // Pastas.
        check("pasta game", "Jogo/Game".equals(GmpFolders.relativeFolderFor("game")));
        check("pasta save", "Jogo/Save".equals(GmpFolders.relativeFolderFor("save")));
        check("pasta texture", "Textura".equals(GmpFolders.relativeFolderFor("texture")));
        check("pasta system", "Sistema".equals(GmpFolders.relativeFolderFor("system")));
        check("tipo desconhecido -> null (instalador recusa)", GmpFolders.relativeFolderFor("../../etc") == null
                && GmpFolders.relativeFolderFor(null) == null && GmpFolders.relativeFolderFor("") == null);

        // Rótulo de tamanho.
        check("tamanho desconhecido -> vazio", game(none, none).sizeLabel().isEmpty());
        CatalogGame mb = new CatalogGame("g", "J", "game", "J.gmp", 953_000_000L, "", "", none, none);
        CatalogGame gb = new CatalogGame("g", "J", "game", "J.gmp", 1_181_116_006L, "", "", none, none);
        check("909 MB (" + mb.sizeLabel() + ")", "909 MB".equals(mb.sizeLabel()));
        check("1,1 GB (" + gb.sizeLabel() + ")", "1,1 GB".equals(gb.sizeLabel()));

        System.out.println("\n" + passed + " ok, " + failed + " falhas");
        System.exit(failed == 0 ? 0 : 1);
    }

    static void check(String name, boolean ok) {
        if (ok) { passed++; System.out.println("  ok   " + name); }
        else { failed++; System.out.println("  FALHOU " + name); }
    }
}
