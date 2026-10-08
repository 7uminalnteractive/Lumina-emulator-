package org.ppsspp.ppsspp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

/**
 * GMP Gameport: mostra o card de "conquista desbloqueada" (ver
 * view_achievement_toast.xml) sobreposto a tela atual, no estilo da
 * referencia visual que o usuario mandou (cartao escuro, icone circular,
 * nome da conquista + o que foi ganho).
 *
 * Nao depende de nenhuma Activity especifica: funciona em qualquer Activity
 * chamando show(activity, client, accessToken, achievement). O card entra
 * deslizando/esmaecendo a partir do topo, fica alguns segundos, e some
 * sozinho -- ai sim chama AchievementsClient.markNotified(), pra essa
 * conquista nao aparecer de novo no proximo recordPlayTime().
 */
final class AchievementNotifier {

    private static final long ANIM_IN_MS = 280;
    private static final long VISIBLE_MS = 4200;
    private static final long ANIM_OUT_MS = 220;

    private AchievementNotifier() {
    }

    /**
     * Mostra uma ou mais conquistas desbloqueadas na mesma chamada de
     * recordPlayTime(), uma de cada vez (em fila), pra nao sobrepor cards.
     * E o metodo recomendado para usar com o resultado de
     * AchievementsClient.AchievementsCallback.onSuccess().
     */
    static void showAll(Activity activity, AchievementsClient client, String accessToken,
                         List<AchievementsClient.Achievement> achievements) {
        if (achievements == null || achievements.isEmpty()) {
            return;
        }
        Queue<AchievementsClient.Achievement> queue = new ArrayDeque<>(achievements);
        showNext(activity, client, accessToken, queue);
    }

    private static void showNext(Activity activity, AchievementsClient client, String accessToken,
                                  Queue<AchievementsClient.Achievement> queue) {
        AchievementsClient.Achievement next = queue.poll();
        if (next == null) {
            return;
        }
        show(activity, client, accessToken, next, () -> showNext(activity, client, accessToken, queue));
    }

    /** Mostra uma unica conquista, sem encadear com nenhuma outra. */
    static void show(Activity activity, AchievementsClient client, String accessToken,
                      AchievementsClient.Achievement achievement) {
        show(activity, client, accessToken, achievement, null);
    }

    private static void show(Activity activity, AchievementsClient client, String accessToken,
                              AchievementsClient.Achievement achievement, Runnable onDismissed) {
        if (activity == null || activity.isFinishing() || achievement == null) {
            if (onDismissed != null) {
                onDismissed.run();
            }
            return;
        }

        ViewGroup decorContent = activity.findViewById(android.R.id.content);
        if (decorContent == null) {
            if (onDismissed != null) {
                onDismissed.run();
            }
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(activity);
        View toast = inflater.inflate(R.layout.view_achievement_toast, decorContent, false);

        ImageView icon = toast.findViewById(R.id.achv_toast_icon);
        TextView nameView = toast.findViewById(R.id.achv_toast_name);
        TextView rewardView = toast.findViewById(R.id.achv_toast_reward);

        icon.setImageResource(iconFor(achievement.icon));
        nameView.setText(achievement.name != null ? achievement.name : "");

        String rewardMessage = achievement.rewardMessage();
        if (rewardMessage != null) {
            rewardView.setText(rewardMessage);
            rewardView.setVisibility(View.VISIBLE);
        } else if (achievement.description != null && !achievement.description.isEmpty()) {
            rewardView.setText(achievement.description);
            rewardView.setVisibility(View.VISIBLE);
        } else {
            rewardView.setVisibility(View.GONE);
        }

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.TOP;
        int marginPx = (int) (16 * activity.getResources().getDisplayMetrics().density);
        params.setMargins(marginPx, marginPx * 2, marginPx, marginPx);
        decorContent.addView(toast, params);

        toast.setAlpha(0f);
        toast.setTranslationY(-marginPx * 2);
        toast.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(ANIM_IN_MS)
                .withEndAction(() -> scheduleDismiss(activity, client, accessToken, achievement, toast, onDismissed))
                .start();
    }

    private static void scheduleDismiss(Activity activity, AchievementsClient client, String accessToken,
                                         AchievementsClient.Achievement achievement, View toast,
                                         Runnable onDismissed) {
        if (activity.isFinishing()) {
            removeImmediately(toast);
            if (onDismissed != null) {
                onDismissed.run();
            }
            return;
        }
        toast.postDelayed(() -> dismiss(activity, client, accessToken, achievement, toast, onDismissed), VISIBLE_MS);
    }

    private static void dismiss(Activity activity, AchievementsClient client, String accessToken,
                                 AchievementsClient.Achievement achievement, View toast,
                                 Runnable onDismissed) {
        if (activity.isFinishing()) {
            removeImmediately(toast);
            if (onDismissed != null) {
                onDismissed.run();
            }
            return;
        }
        toast.animate()
                .alpha(0f)
                .translationY(-toast.getHeight())
                .setDuration(ANIM_OUT_MS)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        removeImmediately(toast);
                        if (client != null && accessToken != null && achievement.id != null) {
                            client.markNotified(accessToken, achievement.id);
                        }
                        if (onDismissed != null) {
                            onDismissed.run();
                        }
                    }
                })
                .start();
    }

    private static void removeImmediately(View toast) {
        ViewGroup parent = (ViewGroup) toast.getParent();
        if (parent != null) {
            parent.removeView(toast);
        }
    }

    private static int iconFor(String icon) {
        if (icon == null) {
            return R.drawable.ic_achv_gift;
        }
        switch (icon) {
            case "clock":
                return R.drawable.ic_achv_clock;
            case "calendar":
                return R.drawable.ic_achv_calendar;
            case "heart":
                return R.drawable.ic_achv_heart;
            default:
                return R.drawable.ic_achv_gift;
        }
    }
}
