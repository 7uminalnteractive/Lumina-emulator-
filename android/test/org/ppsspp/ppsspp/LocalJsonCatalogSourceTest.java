package org.ppsspp.ppsspp;

import android.content.Context;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Roda o LocalJsonCatalogSource REAL contra o gmp_catalog.json REAL (com
 * substitutos de org.json/Context só no teste). Ver stubs/ e LEIA-ME.
 */
public class LocalJsonCatalogSourceTest {

    static int passed = 0, failed = 0;

    public static void main(String[] args) throws Exception {
        String realJson = new String(Files.readAllBytes(Paths.get(args[0])), StandardCharsets.UTF_8);
        CatalogSource real = new LocalJsonCatalogSource(new Context(realJson.getBytes(StandardCharsets.UTF_8)));

        System.out.println("== catálogo REAL (assets/gmp_catalog.json) ==");
        check("compra do patch (conmebol@teste.com) vê o jogo", ids(real, "conmebol@teste.com").equals("[the-best-conmebol]"));
        check("e-mail com MAIÚSCULAS e espaços também", ids(real, "  CONMEBOL@Teste.COM ").equals("[the-best-conmebol]"));
        check("plano ativo (plano@teste.com) vê o jogo", ids(real, "plano@teste.com").equals("[the-best-conmebol]"));
        check("usuário sem patch nem plano NÃO vê nada", ids(real, "semnada@teste.com").equals("[]"));
        check("e-mail desconhecido cai no 'unknownUser' de teste", ids(real, "qualquer@x.com").equals("[the-best-conmebol]"));

        CatalogGame game = real.fetchDownloadableGames("conmebol@teste.com").get(0);
        check("campos lidos: título/arquivo/tipo",
                game.title.equals("The Best Conmebol") && game.fileName.equals("The Best Conmebol.gmp") && game.kind.equals("game"));
        check("campos lidos: produtos e planos", game.unlockedByProducts.contains("conmebol") && game.unlockedByPlans.contains("*"));

        expectError("sem downloadUrl no catálogo -> erro claro",
                () -> real.resolveDownloadUrl("conmebol@teste.com", game), "link de download");
        expectError("sem acesso -> resolveDownloadUrl recusa",
                () -> real.resolveDownloadUrl("semnada@teste.com", game), "não tem acesso");

        System.out.println("\n== casos de borda ==");
        String edge = "{"
                + "\"games\":["
                + " {\"id\":\"ok\",\"title\":\"Bom\",\"kind\":\"game\",\"fileName\":\"Bom.gmp\",\"sizeBytes\":123,\"sha256\":\"AB\","
                + "  \"downloadUrl\":\"https://x.test/bom.gmp\",\"unlockedByProducts\":[\" Europeu \"],\"unlockedByPlans\":[]},"
                + " {\"id\":\"traversal\",\"title\":\"Mau\",\"kind\":\"game\",\"fileName\":\"../fora.gmp\",\"unlockedByProducts\":[\"europeu\"]},"
                + " {\"id\":\"semtitulo\",\"kind\":\"game\",\"fileName\":\"a.gmp\",\"unlockedByProducts\":[\"europeu\"]},"
                + " {\"id\":\"http\",\"title\":\"Http\",\"kind\":\"game\",\"fileName\":\"h.gmp\",\"downloadUrl\":\"http://x.test/h.gmp\",\"unlockedByProducts\":[\"europeu\"]},"
                + " {\"id\":\"soplano\",\"title\":\"Só plano\",\"kind\":\"game\",\"fileName\":\"p.gmp\",\"unlockedByPlans\":[\"premium\"]}"
                + ",{\"id\":\"coringa\",\"title\":\"Coringa\",\"kind\":\"game\",\"fileName\":\"c.gmp\",\"unlockedByPlans\":[\"*\"]}"
                + "],"
                + "\"users\":["
                + " {\"email\":\"a@x.com\",\"products\":[\"europeu\"],\"plan\":null},"
                + " {\"email\":\"b@x.com\",\"plan\":\"Premium\"},"
                + " {\"email\":\"c@x.com\",\"products\":[null,\"\",\"europeu\"]}"
                + "]}";  // sem unknownUser: desconhecido não ganha nada
        CatalogSource s = new LocalJsonCatalogSource(new Context(edge.getBytes(StandardCharsets.UTF_8)));

        check("fileName com '../' é ignorado (nunca chega ao instalador)", !ids(s, "a@x.com").contains("traversal"));
        check("item sem título é ignorado", !ids(s, "a@x.com").contains("semtitulo"));
        check("produto ' Europeu ' normaliza para 'europeu'", ids(s, "a@x.com").contains("ok"));
        check("\"plan\": null NÃO vira o plano 'null' e NÃO libera item de 'qualquer plano' (pegadinha do Android)",
                !ids(s, "a@x.com").contains("coringa") && !ids(s, "a@x.com").contains("soplano"));
        check("plano 'Premium' normaliza para 'premium' e libera o específico e o coringa",
                ids(s, "b@x.com").equals("[soplano, coringa]"));
        check("null/vazio dentro de products é ignorado", ids(s, "c@x.com").contains("ok"));
        check("sem 'unknownUser', e-mail desconhecido não vê nada", ids(s, "zz@x.com").equals("[]"));
        check("sha256 e tamanho lidos", s.fetchDownloadableGames("a@x.com").stream().filter(g -> g.id.equals("ok")).findFirst()
                .map(g -> g.sizeBytes == 123 && g.sha256.equals("AB")).orElse(false));

        CatalogGame ok = s.fetchDownloadableGames("a@x.com").stream().filter(g -> g.id.equals("ok")).findFirst().get();
        check("https válido -> devolve o link", "https://x.test/bom.gmp".equals(s.resolveDownloadUrl("a@x.com", ok)));
        CatalogGame http = s.fetchDownloadableGames("a@x.com").stream().filter(g -> g.id.equals("http")).findFirst().get();
        expectError("link http:// é recusado", () -> s.resolveDownloadUrl("a@x.com", http), "https");

        expectError("JSON quebrado -> IOException clara",
                () -> new LocalJsonCatalogSource(new Context("{ quebrado".getBytes())).fetchDownloadableGames("a@x.com"), "inválido");

        System.out.println("\n" + passed + " ok, " + failed + " falhas");
        System.exit(failed == 0 ? 0 : 1);
    }

    interface Thrower { Object run() throws Exception; }

    static void expectError(String name, Thrower t, String messagePart) {
        try {
            t.run();
            check(name + " (deveria falhar)", false);
        } catch (IOException e) {
            check(name, e.getMessage() != null && e.getMessage().contains(messagePart));
            if (e.getMessage() == null || !e.getMessage().contains(messagePart)) System.out.println("      msg: " + e.getMessage());
        } catch (Exception e) {
            check(name + " (exceção inesperada " + e + ")", false);
        }
    }

    static String ids(CatalogSource s, String email) throws IOException {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (CatalogGame g : s.fetchDownloadableGames(email)) out.add(g.id);
        return out.toString();
    }

    static void check(String name, boolean ok) {
        if (ok) { passed++; System.out.println("  ok   " + name); }
        else { failed++; System.out.println("  FALHOU " + name); }
    }
}
