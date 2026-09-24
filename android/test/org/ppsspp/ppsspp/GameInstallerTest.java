package org.ppsspp.ppsspp;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Teste do GameInstaller SEM Android: sobe um servidor HTTP de verdade nesta
 * máquina e injeta as falhas que o celular vai enfrentar.
 *
 * Rodar (precisa só de um JDK):
 *   javac -d out android/src/org/ppsspp/ppsspp/GameInstaller.java \
 *                 android/test/org/ppsspp/ppsspp/GameInstallerTest.java
 *   java -cp out org.ppsspp.ppsspp.GameInstallerTest
 */
public class GameInstallerTest {

    // ---- servidor de teste ----
    static byte[] payload;
    static String payloadSha;
    static volatile String mode = "normal";
    static final AtomicInteger requests = new AtomicInteger();
    static final List<String> rangeHeaders = new ArrayList<>();
    static final AtomicInteger bytesServed = new AtomicInteger();

    static int passed = 0, failed = 0;

    public static void main(String[] args) throws Exception {
        GameInstaller.retryBaseMillis = 10;      // não esperar 1s+2s+4s nos testes
        GameInstaller.progressIntervalNanos = 0; // aviso de progresso a cada bloco

        payload = new byte[8 * 1024 * 1024 + 123]; // tamanho "quebrado" de propósito
        new Random(42).nextBytes(payload);
        payloadSha = hex(MessageDigest.getInstance("SHA-256").digest(payload));

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/game", GameInstallerTest::handle);
        server.start();
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/game";

        try {
            testNormalDownload(url);
            testResumeAfterConnectionDrop(url);
            testServerIgnoresRange(url);
            testWrongHashIsDeletedAndRejected(url);
            testCancelKeepsPartAndResumeFinishesIt(url);
            testForbiddenIsNotRetried(url);
            testRangeNotSatisfiableRestarts(url);
            testWrongCatalogSizeRejected(url);
            testAlreadyInstalledSkipsNetwork(url);
            testUnknownSizeStillWorks(url);
            testUnsafeFileNames(url);
            testContentRangeParser();
            testUrlProviderCalledAgainOnRetry(url);
        } finally {
            server.stop(0);
        }

        System.out.println("\n" + passed + " ok, " + failed + " falhas");
        System.exit(failed == 0 ? 0 : 1);
    }

    // ------------------------------------------------------------- handler

    static void handle(HttpExchange ex) throws IOException {
        requests.incrementAndGet();
        String range = ex.getRequestHeaders().getFirst("Range");
        synchronized (rangeHeaders) {
            rangeHeaders.add(range);
        }

        if (mode.equals("403")) {
            ex.sendResponseHeaders(403, -1);
            ex.close();
            return;
        }
        if (mode.equals("416") && range != null) {
            ex.sendResponseHeaders(416, -1);
            ex.close();
            return;
        }

        boolean honorRange = !mode.equals("ignore-range");
        long start = 0;
        if (range != null && honorRange) {
            start = Long.parseLong(range.substring("bytes=".length(), range.indexOf('-')));
        }
        long length = payload.length - start;

        if (range != null && honorRange) {
            ex.getResponseHeaders().add("Content-Range",
                    "bytes " + start + "-" + (payload.length - 1) + "/" + payload.length);
            ex.sendResponseHeaders(206, length);
        } else {
            ex.sendResponseHeaders(200, payload.length);
            start = 0;
            length = payload.length;
        }

        // Queda no meio da PRIMEIRA requisição: manda 3 MB e derruba a conexão.
        boolean dropThisOne = mode.equals("drop-once") && requests.get() == 1;
        long toSend = dropThisOne ? 3L * 1024 * 1024 : length;

        try (OutputStream out = ex.getResponseBody()) {
            long sent = 0;
            while (sent < toSend) {
                int n = (int) Math.min(64 * 1024, toSend - sent);
                out.write(payload, (int) (start + sent), n);
                sent += n;
                bytesServed.addAndGet(n);
            }
        } catch (IOException ignored) {
            // cliente cancelou/derrubou: esperado em alguns testes
        }
    }

    static void reset(String newMode) {
        mode = newMode;
        requests.set(0);
        bytesServed.set(0);
        synchronized (rangeHeaders) {
            rangeHeaders.clear();
        }
    }

    // --------------------------------------------------------------- testes

    static void testNormalDownload(String url) throws Exception {
        File dir = tmp();
        reset("normal");
        File f = GameInstaller.install(() -> url, dir, "Jogo.gmp", payload.length, payloadSha, noop(), new AtomicBoolean());
        check("download normal: conteúdo idêntico", Files.readAllBytes(f.toPath()).length == payload.length
                && sha(f).equals(payloadSha));
        check("download normal: sem .part sobrando", !new File(dir, "Jogo.gmp.part").exists());
        check("download normal: 1 requisição", requests.get() == 1);
    }

    static void testResumeAfterConnectionDrop(String url) throws Exception {
        File dir = tmp();
        reset("drop-once");
        File f = GameInstaller.install(() -> url, dir, "Jogo.gmp", payload.length, payloadSha, noop(), new AtomicBoolean());
        check("queda no meio: arquivo final idêntico", sha(f).equals(payloadSha));
        check("queda no meio: retomou com Range (não recomeçou)",
                requests.get() == 2 && rangeHeaders.get(1) != null && rangeHeaders.get(1).startsWith("bytes="));
        check("queda no meio: não baixou tudo de novo (" + bytesServed.get() + " bytes servidos)",
                bytesServed.get() <= payload.length + 64 * 1024);
    }

    static void testServerIgnoresRange(String url) throws Exception {
        File dir = tmp();
        // deixa um .part de 1 MB (dados corretos) e um servidor que ignora Range
        Files.write(new File(dir, "Jogo.gmp.part").toPath(), java.util.Arrays.copyOf(payload, 1024 * 1024));
        reset("ignore-range");
        File f = GameInstaller.install(() -> url, dir, "Jogo.gmp", payload.length, payloadSha, noop(), new AtomicBoolean());
        check("servidor ignora Range: recomeça do zero e fecha certo", sha(f).equals(payloadSha));
    }

    static void testWrongHashIsDeletedAndRejected(String url) throws Exception {
        File dir = tmp();
        reset("normal");
        String wrong = payloadSha.substring(0, 60) + "0000";
        try {
            GameInstaller.install(() -> url, dir, "Jogo.gmp", payload.length, wrong, noop(), new AtomicBoolean());
            check("hash errado: deveria falhar", false);
        } catch (GameInstaller.VerificationException e) {
            check("hash errado: rejeitado", true);
        }
        check("hash errado: NÃO criou o arquivo final", !new File(dir, "Jogo.gmp").exists());
        check("hash errado: apagou o .part corrompido", !new File(dir, "Jogo.gmp.part").exists());
    }

    static void testCancelKeepsPartAndResumeFinishesIt(String url) throws Exception {
        File dir = tmp();
        reset("normal");
        AtomicBoolean cancel = new AtomicBoolean();
        try {
            GameInstaller.install(() -> url, dir, "Jogo.gmp", payload.length, payloadSha,
                    (phase, done, total) -> {
                        if (done >= 2 * 1024 * 1024) cancel.set(true);
                    }, cancel);
            check("cancelar: deveria lançar", false);
        } catch (GameInstaller.CancelledException e) {
            check("cancelar: lançou CancelledException", true);
        }
        File part = new File(dir, "Jogo.gmp.part");
        check("cancelar: manteve o .part parcial", part.exists() && part.length() > 0 && part.length() < payload.length);
        check("cancelar: NÃO criou o arquivo final", !new File(dir, "Jogo.gmp").exists());

        long partial = part.length();
        reset("normal");
        File f = GameInstaller.install(() -> url, dir, "Jogo.gmp", payload.length, payloadSha, noop(), new AtomicBoolean());
        check("retomar depois de cancelar: arquivo idêntico", sha(f).equals(payloadSha));
        check("retomar depois de cancelar: pediu só o resto (Range de " + partial + ")",
                rangeHeaders.get(0) != null && rangeHeaders.get(0).equals("bytes=" + partial + "-"));
    }

    static void testForbiddenIsNotRetried(String url) throws Exception {
        File dir = tmp();
        reset("403");
        try {
            GameInstaller.install(() -> url, dir, "Jogo.gmp", payload.length, payloadSha, noop(), new AtomicBoolean());
            check("403: deveria falhar", false);
        } catch (GameInstaller.HttpStatusException e) {
            check("403: HttpStatusException com código 403", e.code == 403);
        }
        check("403: não insistiu (1 requisição)", requests.get() == 1);
    }

    static void testRangeNotSatisfiableRestarts(String url) throws Exception {
        File dir = tmp();
        // .part com lixo maior que zero -> pede Range -> servidor responde 416
        Files.write(new File(dir, "Jogo.gmp.part").toPath(), new byte[1000]);
        reset("416");
        File f = GameInstaller.install(() -> url, dir, "Jogo.gmp", 0, payloadSha, noop(), new AtomicBoolean());
        check("416: descartou o .part ruim e baixou tudo de novo", sha(f).equals(payloadSha));
    }

    static void testWrongCatalogSizeRejected(String url) throws Exception {
        File dir = tmp();
        reset("normal");
        try {
            GameInstaller.install(() -> url, dir, "Jogo.gmp", payload.length + 1, null, noop(), new AtomicBoolean());
            check("tamanho do catálogo errado: deveria falhar", false);
        } catch (GameInstaller.VerificationException e) {
            check("tamanho do catálogo errado: rejeitado", true);
        }
        check("tamanho do catálogo errado: não criou o final", !new File(dir, "Jogo.gmp").exists());
    }

    static void testAlreadyInstalledSkipsNetwork(String url) throws Exception {
        File dir = tmp();
        Files.write(new File(dir, "Jogo.gmp").toPath(), payload);
        reset("normal");
        GameInstaller.install(() -> url, dir, "Jogo.gmp", payload.length, payloadSha, noop(), new AtomicBoolean());
        check("já instalado: zero requisições", requests.get() == 0);
    }

    static void testUnknownSizeStillWorks(String url) throws Exception {
        File dir = tmp();
        reset("drop-once");
        File f = GameInstaller.install(() -> url, dir, "Jogo.gmp", 0, null, noop(), new AtomicBoolean());
        check("tamanho desconhecido (0) + queda: ainda entrega o arquivo inteiro",
                f.length() == payload.length && sha(f).equals(payloadSha));
    }

    static void testUnsafeFileNames(String url) throws Exception {
        String[] bad = {"../evil.gmp", "a/b.gmp", "a\\b.gmp", "", ".oculto", "x.gmp.part", null, "/etc/passwd"};
        boolean allRejected = true;
        for (String name : bad) {
            File dir = tmp();
            reset("normal");
            try {
                GameInstaller.install(() -> url, dir, name, payload.length, null, noop(), new AtomicBoolean());
                allRejected = false;
                System.out.println("   ! aceitou nome perigoso: " + name);
            } catch (IOException expected) {
                // ok
            }
            if (requests.get() != 0) {
                allRejected = false;
            }
        }
        check("nomes perigosos rejeitados ANTES de qualquer rede", allRejected);
    }

    static void testContentRangeParser() {
        long[] a = GameInstaller.parseContentRange("bytes 100-999/1000");
        long[] b = GameInstaller.parseContentRange("bytes 5-9/*");
        check("Content-Range normal", a != null && a[0] == 100 && a[1] == 1000);
        check("Content-Range com total '*'", b != null && b[0] == 5 && b[1] == 0);
        check("Content-Range lixo -> null", GameInstaller.parseContentRange("lixo") == null
                && GameInstaller.parseContentRange(null) == null
                && GameInstaller.parseContentRange("bytes x-y/z") == null);
    }

    static void testUrlProviderCalledAgainOnRetry(String url) throws Exception {
        File dir = tmp();
        reset("drop-once");
        AtomicInteger asked = new AtomicInteger();
        GameInstaller.install(() -> {
            asked.incrementAndGet();
            return url;
        }, dir, "Jogo.gmp", payload.length, payloadSha, noop(), new AtomicBoolean());
        check("link pedido de novo a cada tentativa (links assinados expiram)", asked.get() == 2);
    }

    // -------------------------------------------------------------- utilitários

    static GameInstaller.Listener noop() {
        return (phase, done, total) -> {
        };
    }

    static File tmp() throws IOException {
        File d = Files.createTempDirectory("gmp-test").toFile();
        d.deleteOnExit();
        return d;
    }

    static String sha(File f) throws Exception {
        return hex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(f.toPath())));
    }

    static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    static void check(String name, boolean ok) {
        if (ok) {
            passed++;
            System.out.println("  ok   " + name);
        } else {
            failed++;
            System.out.println("  FALHOU " + name);
        }
    }
}
