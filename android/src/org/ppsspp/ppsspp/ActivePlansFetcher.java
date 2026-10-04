package org.ppsspp.ppsspp;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * GMP Gameport: busca, pra tela "Minha conta > Planos ativos", os patches
 * comprados (public.user_products) e a assinatura ativa (public.subscriptions)
 * do usuário logado.
 *
 * As duas tabelas têm RLS ligada com uma policy "só minhas próprias linhas"
 * (user_id = auth.uid()), então basta mandar o token de acesso certo -- o
 * filtro de "só o que é meu" roda no servidor, igual ao catálogo de jogos em
 * SupabaseCatalogSource. Esta classe duplica de propósito o pedaço pequeno de
 * "GET autenticado com retry de refresh_token" em vez de reaproveitar aquela
 * classe, pra não arriscar quebrar o catálogo de downloads (que já funciona)
 * só por causa de uma tela nova.
 */
final class ActivePlansFetcher {

    private static final String TAG = "ActivePlansFetcher";
    private static final SimpleDateFormat ISO_FORMAT;
    static {
        ISO_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
        ISO_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    static final class OwnedProduct {
        final String name;
        final String grantedAt;
        final String expiresAt; // null = nunca expira

        OwnedProduct(String name, String grantedAt, String expiresAt) {
            this.name = name;
            this.grantedAt = grantedAt;
            this.expiresAt = expiresAt;
        }
    }

    static final class ActiveSubscription {
        final String planName;
        final String currentPeriodEnd; // pode ser null

        ActiveSubscription(String planName, String currentPeriodEnd) {
            this.planName = planName;
            this.currentPeriodEnd = currentPeriodEnd;
        }
    }

    static final class Result {
        final List<OwnedProduct> products;
        final List<ActiveSubscription> subscriptions;

        Result(List<OwnedProduct> products, List<ActiveSubscription> subscriptions) {
            this.products = products;
            this.subscriptions = subscriptions;
        }
    }

    private final SessionManager sessionManager;
    private final SupabaseAuthClient authClient;
    private final OkHttpClient http;

    ActivePlansFetcher(Context appContext) {
        this.sessionManager = new SessionManager(appContext);
        this.authClient = new SupabaseAuthClient();
        this.http = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();
    }

    /** Bloqueante -- chamar numa thread de fundo, nunca na thread principal. */
    Result fetch() throws IOException {
        if (!sessionManager.hasSession()) {
            return new Result(Collections.emptyList(), Collections.emptyList());
        }
        List<OwnedProduct> products = fetchOwnedProducts();
        List<ActiveSubscription> subscriptions = fetchActiveSubscriptions();
        return new Result(products, subscriptions);
    }

    private List<OwnedProduct> fetchOwnedProducts() throws IOException {
        // products(name) usa o embed de relacionamento do PostgREST (vem via a
        // foreign key user_products.product_id -> products.id).
        Response response = authorizedGet("/rest/v1/user_products?select=granted_at,expires_at,products(name)");
        try {
            String body = response.body() != null ? response.body().string() : "[]";
            if (!response.isSuccessful()) {
                throw new IOException("Não foi possível consultar seus patches (código " + response.code() + ").");
            }
            List<OwnedProduct> result = new ArrayList<>();
            JSONArray array = new JSONArray(body);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                String expiresAt = obj.isNull("expires_at") ? null : obj.optString("expires_at", null);
                if (expiresAt != null && isPast(expiresAt)) {
                    // Já expirou -- não é mais um plano "ativo".
                    continue;
                }
                JSONObject productObj = obj.optJSONObject("products");
                String name = productObj != null ? productObj.optString("name", "Patch") : "Patch";
                result.add(new OwnedProduct(name, obj.optString("granted_at", ""), expiresAt));
            }
            return result;
        } catch (JSONException e) {
            throw new IOException("Resposta inesperada do servidor.", e);
        } finally {
            response.close();
        }
    }

    private List<ActiveSubscription> fetchActiveSubscriptions() throws IOException {
        Response response = authorizedGet("/rest/v1/subscriptions?select=current_period_end,plans(name)&status=eq.active");
        try {
            String body = response.body() != null ? response.body().string() : "[]";
            if (!response.isSuccessful()) {
                throw new IOException("Não foi possível consultar sua assinatura (código " + response.code() + ").");
            }
            List<ActiveSubscription> result = new ArrayList<>();
            JSONArray array = new JSONArray(body);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                JSONObject planObj = obj.optJSONObject("plans");
                String name = planObj != null ? planObj.optString("name", "Plano") : "Plano";
                String periodEnd = obj.isNull("current_period_end") ? null : obj.optString("current_period_end", null);
                result.add(new ActiveSubscription(name, periodEnd));
            }
            return result;
        } catch (JSONException e) {
            throw new IOException("Resposta inesperada do servidor.", e);
        } finally {
            response.close();
        }
    }

    private static boolean isPast(String isoTimestamp) {
        try {
            // PostgREST devolve algo como "2026-01-01T12:00:00+00:00"; cortamos
            // o timezone explícito (sempre UTC aqui) pra bater com ISO_FORMAT.
            String normalized = isoTimestamp.replace("Z", "").split("\\+")[0];
            Date date = ISO_FORMAT.parse(normalized);
            return date != null && date.before(new Date());
        } catch (ParseException e) {
            // Não conseguiu interpretar a data -- por segurança, não trata como expirado.
            return false;
        }
    }

    // ------------------------------------------------------------- requisições

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
}
