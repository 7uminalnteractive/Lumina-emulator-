package org.ppsspp.ppsspp;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * GMP Gameport: sistema de conquistas (horas de jogo, meses de assinatura,
 * cliente fiel...), cada uma com uma recompensa opcional (desconto,
 * atualização grátis, mês de assinatura grátis). As tabelas e funções vivem
 * no Supabase (achievements / user_achievement_progress / user_achievements +
 * as funções gmp_record_play_time / gmp_mark_achievement_notified).
 *
 * Esta classe só fala com o servidor; quem decide ONDE e QUANDO chamar
 * recordPlayTime() (ex.: ao sair de uma sessão de jogo) e como mostrar a
 * notificação é quem usa esta classe -- ver AchievementNotifier para a
 * notificação pronta no estilo da referência visual do usuário.
 */
public class AchievementsClient {

    private static final String TAG = "AchievementsClient";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient client;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface AchievementsCallback {
        void onSuccess(List<Achievement> unlocked);
        void onError(String message);
    }

    public static class Achievement {
        public String id;
        public String name;
        public String description;
        public String rewardType;   // "discount_percent" | "free_update" | "free_month" | "none" | null
        public String rewardValue;  // ex.: "10" para discount_percent
        public String icon;         // ex.: "clock", "calendar", "heart"

        /** Texto pronto pra mostrar na notificação, já traduzido pro usuário. */
        public String rewardMessage() {
            if (rewardType == null) {
                return null;
            }
            switch (rewardType) {
                case "discount_percent":
                    return "Você ganhou " + (rewardValue != null ? rewardValue : "?") + "% de desconto!";
                case "free_update":
                    return "Você ganhou uma atualização gratuita!";
                case "free_month":
                    return "Você ganhou 1 mês de assinatura grátis!";
                default:
                    return null;
            }
        }
    }

    public AchievementsClient() {
        client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Soma segundos jogados ao progresso de "horas de jogo" do usuário logado e
     * devolve as conquistas recém-desbloqueadas (se houver), já com nome,
     * descrição e recompensa prontos pra notificação. Chamar, por exemplo, ao
     * sair de uma sessão de jogo (EmuScreen), com a duração daquela sessão.
     */
    public void recordPlayTime(String accessToken, int seconds, final AchievementsCallback callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("p_seconds", seconds);

            Request request = new Request.Builder()
                    .url(SupabaseAuthClient.SUPABASE_URL + "/rest/v1/rpc/gmp_record_play_time")
                    .addHeader("apikey", SupabaseAuthClient.SUPABASE_ANON_KEY)
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(body.toString(), JSON))
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.w(TAG, "Falha de rede ao registrar tempo de jogo", e);
                    mainHandler.post(() -> callback.onError("Não foi possível conectar ao servidor."));
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    if (!response.isSuccessful()) {
                        Log.w(TAG, "gmp_record_play_time falhou: " + response.code() + " " + responseBody);
                        mainHandler.post(() -> callback.onError("Erro ao registrar tempo de jogo."));
                        return;
                    }
                    try {
                        List<String> ids = new ArrayList<>();
                        JSONArray rows = new JSONArray(responseBody);
                        for (int i = 0; i < rows.length(); i++) {
                            ids.add(rows.getJSONObject(i).getString("achievement_id"));
                        }
                        if (ids.isEmpty()) {
                            mainHandler.post(() -> callback.onSuccess(new ArrayList<>()));
                        } else {
                            fetchAchievementDetails(accessToken, ids, callback);
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "Resposta inesperada de gmp_record_play_time", e);
                        mainHandler.post(() -> callback.onError("Resposta inesperada do servidor."));
                    }
                }
            });
        } catch (JSONException e) {
            callback.onError("Erro interno ao montar requisição.");
        }
    }

    /**
     * O servidor só devolve o ID de cada conquista recém-desbloqueada (ver
     * gmp_record_play_time em Core/Config não, no Supabase); busca aqui
     * nome/descrição/recompensa pra notificação, filtrando por esses IDs.
     * Os IDs vêm do próprio achievement_id (slug só com [a-z0-9_-], validado
     * por CHECK no banco), então é seguro montar a URL direto.
     */
    private void fetchAchievementDetails(String accessToken, List<String> ids, AchievementsCallback callback) {
        StringBuilder idsParam = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) idsParam.append(',');
            idsParam.append(ids.get(i));
        }
        String url = SupabaseAuthClient.SUPABASE_URL + "/rest/v1/achievements"
                + "?select=id,name,description,reward_type,reward_value,icon"
                + "&id=in.(" + idsParam + ")";

        Request request = new Request.Builder()
                .url(url)
                .addHeader("apikey", SupabaseAuthClient.SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer " + accessToken)
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.w(TAG, "Falha de rede ao buscar detalhes das conquistas", e);
                mainHandler.post(() -> callback.onError("Não foi possível conectar ao servidor."));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseBody = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    mainHandler.post(() -> callback.onError("Erro ao buscar detalhes das conquistas."));
                    return;
                }
                try {
                    List<Achievement> result = new ArrayList<>();
                    JSONArray rows = new JSONArray(responseBody);
                    for (int i = 0; i < rows.length(); i++) {
                        JSONObject row = rows.getJSONObject(i);
                        Achievement a = new Achievement();
                        a.id = row.getString("id");
                        a.name = row.optString("name", "");
                        a.description = row.optString("description", "");
                        a.rewardType = row.isNull("reward_type") ? null : row.optString("reward_type", null);
                        a.rewardValue = row.isNull("reward_value") ? null : row.optString("reward_value", null);
                        a.icon = row.isNull("icon") ? null : row.optString("icon", null);
                        result.add(a);
                    }
                    mainHandler.post(() -> callback.onSuccess(result));
                } catch (JSONException e) {
                    Log.e(TAG, "Resposta inesperada ao buscar detalhes das conquistas", e);
                    mainHandler.post(() -> callback.onError("Resposta inesperada do servidor."));
                }
            }
        });
    }

    /**
     * Marca que o app já mostrou a notificação dessa conquista pro usuário, pra
     * não mostrar de novo num próximo recordPlayTime(). Chamar depois que a
     * notificação (AchievementNotifier) terminar de aparecer.
     */
    public void markNotified(String accessToken, String achievementId) {
        try {
            JSONObject body = new JSONObject();
            body.put("p_achievement_id", achievementId);

            Request request = new Request.Builder()
                    .url(SupabaseAuthClient.SUPABASE_URL + "/rest/v1/rpc/gmp_mark_achievement_notified")
                    .addHeader("apikey", SupabaseAuthClient.SUPABASE_ANON_KEY)
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(body.toString(), JSON))
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.w(TAG, "Falha de rede ao marcar conquista como notificada", e);
                }

                @Override
                public void onResponse(Call call, Response response) {
                    if (!response.isSuccessful()) {
                        Log.w(TAG, "gmp_mark_achievement_notified falhou: " + response.code());
                    }
                }
            });
        } catch (JSONException e) {
            Log.e(TAG, "Erro interno ao montar requisição de markNotified", e);
        }
    }
}
