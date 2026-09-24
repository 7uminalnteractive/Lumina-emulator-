package org.ppsspp.ppsspp;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * BANCO DE TESTE: lê o catálogo e os acessos de assets/gmp_catalog.json.
 *
 * Serve para o fluxo inteiro (Biblioteca -> Baixar -> Instalador) funcionar
 * antes de existir o banco real. NÃO protege nada: o arquivo vai dentro do APK
 * e qualquer um o lê. Quando o banco real chegar, escreva outra implementação
 * de CatalogSource e troque a linha em GmpCatalog.
 */
final class LocalJsonCatalogSource implements CatalogSource {

    private static final String TAG = "LocalJsonCatalog";
    private static final String ASSET_NAME = "gmp_catalog.json";

    private final Context context;

    LocalJsonCatalogSource(Context appContext) {
        this.context = appContext;
    }

    @Override
    public List<CatalogGame> fetchDownloadableGames(String email) throws IOException {
        try {
            JSONObject root = new JSONObject(readAsset());

            Set<String> products = new HashSet<>();
            String plan = null;
            JSONObject user = findUser(root, email);
            if (user != null) {
                products = lowerSet(user.optJSONArray("products"));
                plan = optNormalized(user, "plan");
            }

            List<CatalogGame> result = new ArrayList<>();
            JSONArray games = root.optJSONArray("games");
            if (games == null) {
                return result;
            }
            for (int i = 0; i < games.length(); i++) {
                CatalogGame game = parseGame(games.getJSONObject(i));
                if (game != null && CatalogAccess.canDownload(game, products, plan)) {
                    result.add(game);
                }
            }
            return result;
        } catch (JSONException e) {
            throw new IOException("Catálogo de teste inválido: " + e.getMessage(), e);
        }
    }

    @Override
    public String resolveDownloadUrl(String email, CatalogGame game) throws IOException {
        // Confere de novo o acesso (o instalador pode ter sido aberto por engano).
        boolean allowed = false;
        for (CatalogGame g : fetchDownloadableGames(email)) {
            if (g.id.equals(game.id)) {
                allowed = true;
                break;
            }
        }
        if (!allowed) {
            throw new IOException("Você não tem acesso a este jogo.");
        }
        if (game.downloadUrl.isEmpty()) {
            throw new IOException("Este jogo ainda não tem link de download no catálogo de teste "
                    + "(campo downloadUrl de assets/gmp_catalog.json).");
        }
        if (!game.downloadUrl.startsWith("https://")) {
            throw new IOException("O link de download precisa ser https://.");
        }
        return game.downloadUrl;
    }

    // ---------------------------------------------------------------- parsing

    private JSONObject findUser(JSONObject root, String email) {
        JSONArray users = root.optJSONArray("users");
        if (users != null && email != null) {
            for (int i = 0; i < users.length(); i++) {
                JSONObject u = users.optJSONObject(i);
                if (u != null && email.trim().equalsIgnoreCase(u.optString("email", ""))) {
                    return u;
                }
            }
        }
        return root.optJSONObject("unknownUser");
    }

    /** Entrada inválida é ignorada (com aviso no log) em vez de derrubar o catálogo todo. */
    private CatalogGame parseGame(JSONObject o) {
        String id = optNormalized(o, "id");
        String title = o.optString("title", "").trim();
        String kind = optNormalized(o, "kind");
        String fileName = o.optString("fileName", "");
        try {
            GameInstaller.requireSafeFileName(fileName);
        } catch (IOException e) {
            Log.w(TAG, "Ignorando item com fileName inválido: " + fileName);
            return null;
        }
        if (id == null || title.isEmpty() || kind == null) {
            Log.w(TAG, "Ignorando item sem id/title/kind: " + o);
            return null;
        }
        return new CatalogGame(
                id, title, kind, fileName,
                Math.max(0, o.optLong("sizeBytes", 0)),
                o.optString("sha256", "").trim(),
                o.optString("downloadUrl", "").trim(),
                lowerSet(o.optJSONArray("unlockedByProducts")),
                lowerSet(o.optJSONArray("unlockedByPlans")));
    }

    /**
     * No Android, optString() de um "null" literal do JSON devolve a STRING
     * "null". isNull() cobre chave ausente e null literal.
     */
    private static String optNormalized(JSONObject o, String key) {
        if (o.isNull(key)) {
            return null;
        }
        String v = o.optString(key, "").trim().toLowerCase(Locale.ROOT);
        return v.isEmpty() ? null : v;
    }

    private static Set<String> lowerSet(JSONArray array) {
        Set<String> out = new HashSet<>();
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                if (array.isNull(i)) {
                    continue;
                }
                String v = array.optString(i, "").trim().toLowerCase(Locale.ROOT);
                if (!v.isEmpty()) {
                    out.add(v);
                }
            }
        }
        return out;
    }

    private String readAsset() throws IOException {
        try (InputStream in = context.getAssets().open(ASSET_NAME)) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int n;
            while ((n = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, n);
            }
            return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
