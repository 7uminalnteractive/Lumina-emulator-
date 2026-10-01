package org.ppsspp.ppsspp;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * BANCO REAL: catálogo do projeto Supabase "TBP".
 *
 * A tabela public.games tem RLS ligada com a policy "games_read_entitled",
 * que só devolve as linhas que private.can_access_game(id) aprova para O
 * USUÁRIO DO TOKEN enviado -- ou seja, o filtro de quem-pode-baixar-o-quê
 * roda inteiro no servidor. Este cliente só faz duas coisas: mandar o token
 * certo, e converter JSON em CatalogGame.
 *
 * O parâmetro "email" dos métodos da interface é ignorado aqui: a
 * identidade de quem está perguntando vem do token de acesso salvo pelo
 * SessionManager (resultado do login real via SupabaseAuthClient), não do
 * e-mail passado por quem chama.
 */
final class SupabaseCatalogSource implements CatalogSource {

    private static final String TAG = "SupabaseCatalogSource";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final int SIGNED_URL_TTL_SECONDS = 120;

    private final Context context;
    private final SessionManager sessionManager;
    private final SupabaseAuthClient authClient;
    private final OkHttpClient http;

    SupabaseCatalogSource(Context appContext) {
        this.context = appContext;
        this.sessionManager = new SessionManager(appContext);
        this.authClient = new SupabaseAuthClient();
        this.http = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public List<CatalogGame> fetchDownloadableGames(String email) throws IOException {
        if (!sessionManager.hasSession()) {
            // Sem sessão real (login ainda simulado, ou usuário deslogado):
            // não há token para o servidor conferir, então não há o que mostrar.
            return Collections.emptyList();
        }

        Response response = authorizedGet("/rest/v1/games?select=*");
        try {
            String body = response.body() != null ? response.body().string() : "[]";
            if (!response.isSuccessful()) {
                throw new IOException("Não foi possível consultar o catálogo (código "
                        + response.code() + ").");
            }
            return parseGames(body);
        } catch (JSONException e) {
            throw new IOException("Resposta inesperada do catálogo.", e);
        } finally {
            response.close();
        }
    }

    @Override
    public String resolveDownloadUrl(String email, CatalogGame game) throws IOException {
        if (game.storagePath.isEmpty()) {
            throw new IOException("Este jogo não tem arquivo associado no catálogo.");
        }
        if (!sessionManager.hasSession()) {
            throw new IOException("Você precisa estar logado para baixar.");
        }

        JSONObject body = new JSONObject();
        try {
            body.put("expiresIn", SIGNED_URL_TTL_SECONDS);
        } catch (JSONException e) {
            throw new IOException("Erro interno ao montar requisição.", e);
        }

        String path = "/storage/v1/object/sign/games/" + game.storagePath;
        Response response = authorizedPost(path, body.toString());
        try {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                // A policy do bucket "games" já confere o acesso de novo aqui; um
                // 403/404 normalmente é falta de direito (ou storage_path errado).
                throw new IOException("Você não tem acesso a este jogo (código "
                        + response.code() + ").");
            }
            JSONObject json = new JSONObject(responseBody);
            String signedUrl = json.optString("signedURL", json.optString("signedUrl", ""));
            if (signedUrl.isEmpty()) {
                throw new IOException("O servidor não retornou um link de download.");
            }
            return SupabaseAuthClient.SUPABASE_URL + "/storage/v1" + signedUrl;
        } catch (JSONException e) {
            throw new IOException("Resposta inesperada do servidor de arquivos.", e);
        } finally {
            response.close();
        }
    }

    // ------------------------------------------------------------- requisições

    /**
     * GET autenticado com o token salvo; se o servidor disser que expirou
     * (401), tenta renovar UMA vez com o refresh_token e repete a chamada.
     */
    private Response authorizedGet(String path) throws IOException {
        Request request = new Request.Builder()
                .url(SupabaseAuthClient.SUPABASE_URL + path)
                .addHeader("apikey", SupabaseAuthClient.SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer " + sessionManager.getAccessToken())
                .get()
                .build();

        Response response = http.newCall(request).execute();
        if (response.code() == 401 && refreshAccessToken()) {
            response.close();
            request = request.newBuilder()
                    .header("Authorization", "Bearer " + sessionManager.getAccessToken())
                    .build();
            response = http.newCall(request).execute();
        }
        return response;
    }

    private Response authorizedPost(String path, String jsonBody) throws IOException {
        Request request = new Request.Builder()
                .url(SupabaseAuthClient.SUPABASE_URL + path)
                .addHeader("apikey", SupabaseAuthClient.SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer " + sessionManager.getAccessToken())
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(jsonBody, JSON))
                .build();

        Response response = http.newCall(request).execute();
        if (response.code() == 401 && refreshAccessToken()) {
            response.close();
            request = request.newBuilder()
                    .header("Authorization", "Bearer " + sessionManager.getAccessToken())
                    .build();
            response = http.newCall(request).execute();
        }
        return response;
    }

    /** @return true se conseguiu renovar (e já salvou o novo token). */
    private boolean refreshAccessToken() {
        String refreshToken = sessionManager.getRefreshToken();
        if (refreshToken == null) {
            return false;
        }
        try {
            SupabaseAuthClient.AuthResult result = authClient.refreshSessionBlocking(refreshToken);
            sessionManager.saveSession(result);
            return true;
        } catch (IOException e) {
            Log.w(TAG, "Não foi possível renovar a sessão", e);
            return false;
        }
    }

    // ------------------------------------------------------------------ parsing

    private List<CatalogGame> parseGames(String jsonArray) throws JSONException {
        JSONArray array = new JSONArray(jsonArray);
        List<CatalogGame> result = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            CatalogGame game = parseGame(array.getJSONObject(i));
            if (game != null) {
                result.add(game);
            }
        }
        return result;
    }

    /** Entrada inválida é ignorada (com aviso no log) em vez de derrubar o catálogo todo. */
    private CatalogGame parseGame(JSONObject o) {
        String id = o.optString("id", "").trim();
        String title = o.optString("title", "").trim();
        String kind = o.optString("kind", "").trim().toLowerCase();
        String fileName = o.optString("file_name", "").trim();
        try {
            GameInstaller.requireSafeFileName(fileName);
        } catch (IOException e) {
            Log.w(TAG, "Ignorando item com file_name inválido: " + fileName);
            return null;
        }
        if (id.isEmpty() || title.isEmpty() || kind.isEmpty()) {
            Log.w(TAG, "Ignorando item sem id/title/kind: " + o);
            return null;
        }
        // unlockedByProducts/unlockedByPlans não voltam do servidor: o acesso já
        // veio filtrado pela RLS antes de a linha chegar aqui (ver comentário da
        // classe). Ficam vazios; nada volta a checá-los para este catálogo.
        return new CatalogGame(
                id, title, kind, fileName,
                Math.max(0, o.optLong("size_bytes", 0)),
                o.optString("sha256", "").trim(),
                "",
                o.optString("storage_path", "").trim(),
                Collections.emptySet(),
                Collections.emptySet());
    }
}
