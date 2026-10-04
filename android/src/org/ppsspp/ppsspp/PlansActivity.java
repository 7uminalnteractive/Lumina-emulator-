package org.ppsspp.ppsspp;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * GMP Gameport: "Minha conta > Planos ativos" -- mostra os patches comprados
 * (public.user_products) e a assinatura ativa (public.subscriptions), lidos
 * de verdade do Supabase via ActivePlansFetcher.
 */
public class PlansActivity extends AppCompatActivity {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private TextView statusView;
    private LinearLayout listContainer;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_plans);
        GmpWindowHelper.applyFullscreenOnly(this, findViewById(R.id.plans_root));

        statusView = findViewById(R.id.plans_status);
        listContainer = findViewById(R.id.plans_list);
        findViewById(R.id.plans_btn_back).setOnClickListener(v -> finish());

        loadPlans();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            GmpWindowHelper.reapplyImmersive(this);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }

    private void loadPlans() {
        statusView.setText("Carregando...");
        statusView.setVisibility(View.VISIBLE);
        listContainer.removeAllViews();

        ActivePlansFetcher fetcher = new ActivePlansFetcher(this);
        executor.execute(() -> {
            try {
                ActivePlansFetcher.Result result = fetcher.fetch();
                runOnUiThread(() -> showResult(result));
            } catch (IOException e) {
                runOnUiThread(() -> {
                    statusView.setText("Não foi possível carregar seus planos agora. Verifique sua internet e tente de novo.");
                    statusView.setVisibility(View.VISIBLE);
                });
            }
        });
    }

    private void showResult(ActivePlansFetcher.Result result) {
        listContainer.removeAllViews();

        boolean hasAnything = !result.products.isEmpty() || !result.subscriptions.isEmpty();
        if (!hasAnything) {
            statusView.setText("Você ainda não tem nenhum patch ou assinatura ativa.");
            statusView.setVisibility(View.VISIBLE);
            return;
        }
        statusView.setVisibility(View.GONE);

        if (!result.subscriptions.isEmpty()) {
            addSectionHeader("Assinatura");
            for (ActivePlansFetcher.ActiveSubscription sub : result.subscriptions) {
                String subtitle = sub.currentPeriodEnd != null
                        ? "Renova em " + sub.currentPeriodEnd.substring(0, Math.min(10, sub.currentPeriodEnd.length()))
                        : "Ativa";
                addCard(sub.planName, subtitle);
            }
        }

        if (!result.products.isEmpty()) {
            addSectionHeader("Patches comprados");
            for (ActivePlansFetcher.OwnedProduct product : result.products) {
                String subtitle = product.expiresAt != null
                        ? "Expira em " + product.expiresAt.substring(0, Math.min(10, product.expiresAt.length()))
                        : "Acesso permanente";
                addCard(product.name, subtitle);
            }
        }
    }

    private void addSectionHeader(String text) {
        TextView header = new TextView(this);
        header.setText(text);
        header.setTextColor(getColor(R.color.lumina_muted));
        header.setTextSize(12f);
        header.setAllCaps(true);
        header.setPadding(0, dp(12), 0, dp(6));
        listContainer.addView(header);
    }

    private void addCard(String title, String subtitle) {
        View card = LayoutInflater.from(this).inflate(R.layout.item_plan_card, listContainer, false);
        ((TextView) card.findViewById(R.id.plan_card_title)).setText(title);
        ((TextView) card.findViewById(R.id.plan_card_subtitle)).setText(subtitle);
        listContainer.addView(card);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
