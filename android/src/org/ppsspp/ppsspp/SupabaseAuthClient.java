package org.ppsspp.ppsspp;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class SupabaseAuthClient {

    private static final String TAG = "SupabaseAuthClient";

    // Pacote-privado: SupabaseCatalogSource usa os mesmos valores (evita
    // duplicar a URL e a chave em dois arquivos).
    static final String SUPABASE_URL = "https://hbtamibmcvgbxvbktoii.supabase.co";
    static final String SUPABASE_ANON_KEY = "sb_publishable_REpDy97uh9mUGWhBEk2qpw_f5Lj3ofN";

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient client;
    // GMP Gameport: enqueue() do OkHttp chama o callback numa thread do próprio
    // OkHttp, não na principal. Quem usa signIn/sendPasswordReset/refreshSession
    // geralmente mexe em tela (botão, Toast, trocar de Activity) dentro do
    // callback -- sem isso, dá CalledFromWrongThreadException e o app fecha
    // na hora (foi exatamente o que aconteceu no login). refreshSessionBlocking
    // não usa isto: ela já roda bloqueante numa thread de fundo escolhida por
    // quem chama, de propósito.
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface AuthCallback {
        void onSuccess(AuthResult result);
        void onError(String message);
    }

    public interface SimpleCallback {
        void onSuccess();
        void onError(String message);
    }

    public static class AuthResult {
        public String accessToken;
        public String refreshToken;
        public String userId;
        public String email;
        public String displayName;
    }

    public SupabaseAuthClient() {
        client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();
    }

    public void signIn(String email, String password, AuthCallback callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("email", email);
            body.put("password", password);

            Request request = new Request.Builder()
                    .url(SUPABASE_URL + "/auth/v1/token?grant_type=password")
                    .addHeader("apikey", SUPABASE_ANON_KEY)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(body.toString(), JSON))
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Falha de rede no login", e);
                    mainHandler.post(() -> callback.onError(
                            "Não foi possível conectar ao servidor. Verifique sua internet."));
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    if (!response.isSuccessful()) {
                        String message = parseErrorMessage(responseBody, response.code());
                        mainHandler.post(() -> callback.onError(message));
                        return;
                    }
                    try {
                        AuthResult result = parseAuthResult(responseBody);
                        mainHandler.post(() -> callback.onSuccess(result));
                    } catch (JSONException e) {
                        Log.e(TAG, "Erro ao interpretar resposta do login", e);
                        mainHandler.post(() -> callback.onError("Resposta inesperada do servidor."));
                    }
                }
            });
        } catch (JSONException e) {
            callback.onError("Erro interno ao montar requisição.");
        }
    }

    /**
     * GMP Gameport: grava o nome digitado no cadastro como user_metadata.full_name
     * no Supabase. Sem isso, o nome que a pessoa digita em LoginActivity nunca é
     * salvo no servidor -- ele só serve de fallback local caso o servidor não
     * tenha nome nenhum, e como nenhuma conta tinha full_name ainda, o próximo
     * login sempre devolvia o e-mail como "nome" (era exatamente o bug de "mostra
     * o e-mail em vez do nome"). Chamar isto depois de um login cujo
     * AuthResult.displayName veio vazio/igual ao e-mail corrige isso de vez: da
     * próxima vez o servidor já devolve o nome certo.
     */
    public void updateDisplayName(String accessToken, String fullName, SimpleCallback callback) {
        try {
            JSONObject data = new JSONObject();
            data.put("full_name", fullName);
            JSONObject body = new JSONObject();
            body.put("data", data);

            Request request = new Request.Builder()
                    .url(SUPABASE_URL + "/auth/v1/user")
                    .addHeader("apikey", SUPABASE_ANON_KEY)
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .addHeader("Content-Type", "application/json")
                    .put(RequestBody.create(body.toString(), JSON))
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Falha de rede ao salvar o nome", e);
                    mainHandler.post(() -> callback.onError("Não foi possível salvar seu nome agora."));
                }

                @Override
                public void onResponse(Call call, Response response) {
                    if (response.isSuccessful()) {
                        mainHandler.post(callback::onSuccess);
                    } else {
                        mainHandler.post(() -> callback.onError("Não foi possível salvar seu nome agora."));
                    }
                }
            });
        } catch (JSONException e) {
            callback.onError("Erro interno ao montar requisição.");
        }
    }

    /**
     * GMP Gameport: troca a senha da conta logada (tela "Minha conta > Trocar
     * senha"), diferente de sendPasswordReset (que é o fluxo de "esqueci minha
     * senha" por e-mail, sem precisar estar logado).
     */
    public void updatePassword(String accessToken, String newPassword, SimpleCallback callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("password", newPassword);

            Request request = new Request.Builder()
                    .url(SUPABASE_URL + "/auth/v1/user")
                    .addHeader("apikey", SUPABASE_ANON_KEY)
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .addHeader("Content-Type", "application/json")
                    .put(RequestBody.create(body.toString(), JSON))
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Falha de rede ao trocar a senha", e);
                    mainHandler.post(() -> callback.onError("Não foi possível conectar ao servidor."));
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful()) {
                        mainHandler.post(callback::onSuccess);
                    } else {
                        String responseBody = response.body() != null ? response.body().string() : "";
                        String message = parseErrorMessage(responseBody, response.code());
                        mainHandler.post(() -> callback.onError(message));
                    }
                }
            });
        } catch (JSONException e) {
            callback.onError("Erro interno ao montar requisição.");
        }
    }

    public void sendPasswordReset(String email, SimpleCallback callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("email", email);

            Request request = new Request.Builder()
                    .url(SUPABASE_URL + "/auth/v1/recover")
                    .addHeader("apikey", SUPABASE_ANON_KEY)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(body.toString(), JSON))
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    mainHandler.post(() -> callback.onError("Não foi possível conectar ao servidor."));
                }

                @Override
                public void onResponse(Call call, Response response) {
                    if (response.isSuccessful()) {
                        mainHandler.post(callback::onSuccess);
                    } else {
                        mainHandler.post(() -> callback.onError(
                                "Não foi possível enviar o e-mail de redefinição."));
                    }
                }
            });
        } catch (JSONException e) {
            callback.onError("Erro interno ao montar requisição.");
        }
    }

    public void refreshSession(String refreshToken, AuthCallback callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("refresh_token", refreshToken);

            Request request = new Request.Builder()
                    .url(SUPABASE_URL + "/auth/v1/token?grant_type=refresh_token")
                    .addHeader("apikey", SUPABASE_ANON_KEY)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(body.toString(), JSON))
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    mainHandler.post(() -> callback.onError("Não foi possível validar a sessão."));
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    if (!response.isSuccessful()) {
                        mainHandler.post(() -> callback.onError("Sessão expirada, faça login novamente."));
                        return;
                    }
                    try {
                        AuthResult result = parseAuthResult(responseBody);
                        mainHandler.post(() -> callback.onSuccess(result));
                    } catch (JSONException e) {
                        mainHandler.post(() -> callback.onError("Resposta inesperada do servidor."));
                    }
                }
            });
        } catch (JSONException e) {
            callback.onError("Erro interno ao montar requisição.");
        }
    }

    /**
     * Igual a refreshSession, mas bloqueante (chama call.execute() em vez de
     * enqueue()). Usado pelo SupabaseCatalogSource, que já roda em thread de
     * fundo e precisa do resultado antes de repetir a requisição que falhou
     * com 401.
     */
    AuthResult refreshSessionBlocking(String refreshToken) throws IOException {
        try {
            JSONObject body = new JSONObject();
            body.put("refresh_token", refreshToken);

            Request request = new Request.Builder()
                    .url(SUPABASE_URL + "/auth/v1/token?grant_type=refresh_token")
                    .addHeader("apikey", SUPABASE_ANON_KEY)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(body.toString(), JSON))
                    .build();

            try (Response response = client.newCall(request).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    throw new IOException("Sessão expirada, faça login novamente.");
                }
                return parseAuthResult(responseBody);
            }
        } catch (JSONException e) {
            throw new IOException("Erro interno ao montar requisição.", e);
        }
    }

    private AuthResult parseAuthResult(String responseBody) throws JSONException {
        JSONObject json = new JSONObject(responseBody);
        AuthResult result = new AuthResult();
        result.accessToken = json.optString("access_token", null);
        result.refreshToken = json.optString("refresh_token", null);

        JSONObject user = json.optJSONObject("user");
        if (user != null) {
            result.userId = user.optString("id", null);
            result.email = user.optString("email", null);
            JSONObject metadata = user.optJSONObject("user_metadata");
            if (metadata != null) {
                result.displayName = metadata.optString("full_name", result.email);
            } else {
                result.displayName = result.email;
            }
        }
        return result;
    }

    private String parseErrorMessage(String responseBody, int httpCode) {
        try {
            JSONObject json = new JSONObject(responseBody);
            String msg = json.optString("error_description", null);
            if (msg == null) {
                msg = json.optString("msg", null);
            }
            if (msg != null) {
                if (msg.toLowerCase().contains("invalid login credentials")) {
                    return "E-mail ou senha incorretos.";
                }
                return msg;
            }
        } catch (JSONException ignored) {
        }
        if (httpCode == 400 || httpCode == 401) {
            return "E-mail ou senha incorretos.";
        }
        return "Erro ao entrar (código " + httpCode + ").";
    }
}
