package org.ppsspp.ppsspp;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * GMP Gameport: motor do instalador. Baixa UM arquivo para uma pasta do GMP e só
 * o entrega, já validado, com o nome final.
 *
 * É Java PURO de propósito (nenhuma classe do Android): assim ele é testado no
 * computador contra um servidor HTTP de verdade (retomada, queda de conexão,
 * hash errado, cancelamento). A tela é InstallerActivity; este arquivo só sabe
 * baixar.
 *
 * Garantias:
 *  - Nunca aparece um arquivo final incompleto. O download vai para
 *    "<nome>.part" e só é renomeado para "<nome>" depois de conferido. Se o app
 *    for fechado no meio, sobra só o .part (que a Biblioteca não lista).
 *  - Retoma de onde parou (HTTP Range) -- importante: um jogo tem ~1 GB e a
 *    conexão do celular cai. Também retenta sozinho em falhas de rede.
 *  - Confere o tamanho e o SHA-256 do catálogo. Arquivo corrompido é apagado.
 *  - O nome do arquivo vem do servidor, então é validado (sem "..", "/" etc.):
 *    o servidor não consegue gravar fora da pasta escolhida pelo app.
 *  - A URL é pedida de novo a cada tentativa (UrlProvider), porque links
 *    assinados expiram.
 */
final class GameInstaller {

    enum Phase {
        DOWNLOADING,
        VERIFYING
    }

    interface Listener {
        /** total = 0 quando o tamanho é desconhecido. Chamado de thread de fundo. */
        void onProgress(Phase phase, long done, long total);
    }

    interface UrlProvider {
        String get() throws IOException;
    }

    /** O usuário cancelou. O .part é mantido para retomar depois. */
    static final class CancelledException extends IOException {
        CancelledException() {
            super("cancelado");
        }
    }

    /** O arquivo baixado não bate com o catálogo. O .part foi apagado. */
    static final class VerificationException extends IOException {
        VerificationException(String message) {
            super(message);
        }
    }

    static final class HttpStatusException extends IOException {
        final int code;

        HttpStatusException(int code) {
            super("HTTP " + code);
            this.code = code;
        }

        /** 408/429/5xx podem passar sozinhos; os demais 4xx (403, 404...) não. */
        boolean isRetryable() {
            return code == 408 || code == 429 || code >= 500;
        }
    }

    static final int MAX_ATTEMPTS = 4;
    /** Espera base entre tentativas (dobra a cada uma). Package-private para os testes. */
    static long retryBaseMillis = 1000L;

    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 30_000;
    /** Intervalo mínimo entre avisos de progresso. Package-private para os testes. */
    static long progressIntervalNanos = 200_000_000L;
    private static final long SPACE_MARGIN_BYTES = 50L * 1024 * 1024;

    private GameInstaller() {
    }

    /**
     * @param expectedSize tamanho esperado em bytes, ou 0 se desconhecido
     * @param sha256       SHA-256 em hexadecimal, ou null/"" para não conferir
     * @return o arquivo final, já no lugar
     */
    static File install(UrlProvider urls, File destDir, String fileName, long expectedSize,
                        String sha256, Listener listener, AtomicBoolean cancel) throws IOException {
        requireSafeFileName(fileName);
        if (!destDir.isDirectory() && !destDir.mkdirs() && !destDir.isDirectory()) {
            throw new IOException("Não foi possível criar a pasta " + destDir.getPath());
        }

        File dest = new File(destDir, fileName);
        File part = new File(destDir, fileName + ".part");

        // Já instalado com o tamanho certo: nada a fazer.
        if (expectedSize > 0 && dest.isFile() && dest.length() == expectedSize) {
            return dest;
        }

        if (expectedSize > 0) {
            long already = part.isFile() ? Math.min(part.length(), expectedSize) : 0;
            long usable = destDir.getUsableSpace();
            if (usable > 0 && usable < (expectedSize - already) + SPACE_MARGIN_BYTES) {
                throw new IOException("Espaço insuficiente no aparelho. Libere pelo menos "
                        + formatMb(expectedSize - already + SPACE_MARGIN_BYTES) + " e tente de novo.");
            }
        }

        long serverTotal = downloadWithRetry(urls, part, expectedSize, listener, cancel);

        long length = part.length();
        if (expectedSize > 0 && length != expectedSize) {
            deleteQuietly(part);
            throw new VerificationException("O arquivo baixado está incompleto ou diferente do esperado.");
        }
        if (serverTotal > 0 && length != serverTotal) {
            deleteQuietly(part);
            throw new VerificationException("O arquivo baixado está incompleto.");
        }

        if (sha256 != null && !sha256.trim().isEmpty()) {
            String actual = sha256Of(part, listener, cancel);
            if (!actual.equalsIgnoreCase(sha256.trim())) {
                deleteQuietly(part);
                throw new VerificationException("O arquivo baixado está corrompido. Tente baixar de novo.");
            }
        }

        // Mesma pasta => renomear é atômico. Só aqui o jogo "passa a existir".
        if (dest.exists() && !dest.delete()) {
            throw new IOException("Não foi possível substituir " + dest.getName());
        }
        if (!part.renameTo(dest)) {
            throw new IOException("Não foi possível finalizar a instalação.");
        }
        return dest;
    }

    // ------------------------------------------------------------------ download

    /** Retorna o tamanho total informado pelo servidor (0 se não informou). */
    private static long downloadWithRetry(UrlProvider urls, File part, long expectedSize,
                                          Listener listener, AtomicBoolean cancel) throws IOException {
        IOException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            checkCancel(cancel);
            try {
                return downloadOnce(urls.get(), part, expectedSize, listener, cancel);
            } catch (CancelledException e) {
                throw e;
            } catch (HttpStatusException e) {
                if (!e.isRetryable()) {
                    throw e;
                }
                last = e;
            } catch (IOException e) {
                last = e;
            }
            if (attempt < MAX_ATTEMPTS) {
                sleepCancellable(retryBaseMillis << (attempt - 1), cancel);
            }
        }
        throw last != null ? last : new IOException("Falha no download.");
    }

    private static long downloadOnce(String urlString, File part, long expectedSize,
                                     Listener listener, AtomicBoolean cancel) throws IOException {
        if (urlString == null || urlString.trim().isEmpty()) {
            throw new IOException("Este jogo ainda não tem link de download.");
        }

        long offset = part.isFile() ? part.length() : 0;
        if (expectedSize > 0 && offset > expectedSize) {
            deleteQuietly(part);
            offset = 0;
        }
        if (expectedSize > 0 && offset == expectedSize) {
            return expectedSize; // .part já está completo; falta só conferir.
        }

        HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
        try {
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setInstanceFollowRedirects(true);
            // Sem gzip: com compressão o Content-Length não bate com os bytes gravados.
            conn.setRequestProperty("Accept-Encoding", "identity");
            if (offset > 0) {
                conn.setRequestProperty("Range", "bytes=" + offset + "-");
            }

            int code = conn.getResponseCode();
            boolean append;
            long total;
            if (code == 206) {
                long[] range = parseContentRange(conn.getHeaderField("Content-Range"));
                if (range != null && range[0] != offset) {
                    // Servidor devolveu outro trecho: anexar corromperia o arquivo.
                    deleteQuietly(part);
                    throw new IOException("Resposta parcial inconsistente; reiniciando.");
                }
                long remaining = contentLength(conn);
                total = (range != null && range[1] > 0) ? range[1]
                        : (remaining >= 0 ? offset + remaining : 0);
                append = true;
            } else if (code == 200) {
                // Download novo, ou o servidor ignorou o Range: recomeça do zero.
                offset = 0;
                long len = contentLength(conn);
                total = len >= 0 ? len : 0;
                append = false;
            } else if (code == 416) {
                // Nosso .part não cabe no arquivo do servidor: descarta e recomeça.
                deleteQuietly(part);
                throw new IOException("Download parcial inválido; reiniciando.");
            } else {
                throw new HttpStatusException(code);
            }

            long shownTotal = total > 0 ? total : expectedSize;
            long done = offset;
            long lastReport = System.nanoTime() - progressIntervalNanos; // 1º aviso já no 1º bloco
            try (InputStream in = conn.getInputStream();
                 FileOutputStream out = new FileOutputStream(part, append)) {
                byte[] buffer = new byte[64 * 1024];
                int n;
                while ((n = in.read(buffer)) != -1) {
                    if (cancel.get()) {
                        throw new CancelledException();
                    }
                    out.write(buffer, 0, n);
                    done += n;
                    long now = System.nanoTime();
                    if (now - lastReport >= progressIntervalNanos) {
                        lastReport = now;
                        listener.onProgress(Phase.DOWNLOADING, done, shownTotal);
                    }
                }
            }
            if (cancel.get()) {
                throw new CancelledException();
            }
            // Conexão que cai com Content-Length às vezes termina "sem erro" em
            // vez de lançar exceção. Sem essa checagem entregaríamos um arquivo cortado.
            if (total > 0 && done < total) {
                throw new IOException("Conexão interrompida.");
            }
            listener.onProgress(Phase.DOWNLOADING, done, shownTotal);
            return total;
        } finally {
            conn.disconnect();
        }
    }

    // ------------------------------------------------------------------ helpers

    static String sha256Of(File file, Listener listener, AtomicBoolean cancel) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 indisponível neste aparelho.", e);
        }
        long size = file.length();
        long done = 0;
        long lastReport = System.nanoTime() - progressIntervalNanos;
        byte[] buffer = new byte[256 * 1024];
        try (InputStream in = new FileInputStream(file)) {
            int n;
            while ((n = in.read(buffer)) != -1) {
                if (cancel.get()) {
                    throw new CancelledException();
                }
                digest.update(buffer, 0, n);
                done += n;
                long now = System.nanoTime();
                if (now - lastReport >= progressIntervalNanos) {
                    lastReport = now;
                    listener.onProgress(Phase.VERIFYING, done, size);
                }
            }
        }
        listener.onProgress(Phase.VERIFYING, size, size);
        byte[] hash = digest.digest();
        StringBuilder hex = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return hex.toString();
    }

    /** O nome vem de fora (catálogo/servidor): nada que saia da pasta de destino. */
    static void requireSafeFileName(String name) throws IOException {
        boolean bad = name == null
                || name.isEmpty()
                || name.length() > 200
                || name.startsWith(".")
                || name.indexOf('/') >= 0
                || name.indexOf('\\') >= 0
                || name.indexOf('\0') >= 0
                || name.endsWith(".part");
        if (bad) {
            throw new IOException("Nome de arquivo inválido: " + name);
        }
    }

    /** "bytes 100-999/1000" -> {100, 1000}. Total 0 se for "*". null se ilegível. */
    static long[] parseContentRange(String header) {
        if (header == null) {
            return null;
        }
        try {
            String h = header.trim();
            if (!h.regionMatches(true, 0, "bytes ", 0, 6)) {
                return null;
            }
            String spec = h.substring(6).trim();
            int slash = spec.indexOf('/');
            int dash = spec.indexOf('-');
            if (slash < 0 || dash < 0 || dash > slash) {
                return null;
            }
            long start = Long.parseLong(spec.substring(0, dash).trim());
            String totalText = spec.substring(slash + 1).trim();
            long total = "*".equals(totalText) ? 0 : Long.parseLong(totalText);
            return new long[]{start, total};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** getContentLengthLong() só existe no Android 7+ (API 24); o app aceita API 21. */
    private static long contentLength(HttpURLConnection conn) {
        String value = conn.getHeaderField("Content-Length");
        if (value == null) {
            return -1;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static void checkCancel(AtomicBoolean cancel) throws CancelledException {
        if (cancel.get()) {
            throw new CancelledException();
        }
    }

    private static void sleepCancellable(long millis, AtomicBoolean cancel) throws IOException {
        long end = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < end) {
            checkCancel(cancel);
            try {
                Thread.sleep(Math.min(50, Math.max(1, end - System.currentTimeMillis())));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CancelledException();
            }
        }
    }

    private static void deleteQuietly(File f) {
        if (f.exists() && !f.delete()) {
            f.deleteOnExit();
        }
    }

    private static String formatMb(long bytes) {
        return Math.max(1, bytes / (1024 * 1024)) + " MB";
    }
}
