package org.ppsspp.ppsspp;

import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * Cards dos jogos "Disponíveis para baixar" na Biblioteca. Reaproveita o layout
 * de card dos jogos instalados (item_game_card) para ficar idêntico ao resto.
 */
final class DownloadAdapter extends RecyclerView.Adapter<DownloadAdapter.Holder> {

    interface OnDownloadClick {
        void onClick(CatalogGame game);
    }

    // Tom mais frio/apagado que o dos instalados, para distinguir "ainda não tenho".
    private static final int[] GRADIENT = {0xFF2A3A1E, 0xFF0B1208};

    private final List<CatalogGame> games;
    private final OnDownloadClick listener;

    DownloadAdapter(List<CatalogGame> games, OnDownloadClick listener) {
        this.games = games;
        this.listener = listener;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_game_card, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        CatalogGame game = games.get(position);

        holder.title.setText(game.title);
        String size = game.sizeLabel();
        holder.subtitle.setText(size.isEmpty() ? "⬇ Baixar" : "⬇ Baixar · " + size);

        holder.coverImage.setVisibility(View.GONE);
        holder.initials.setVisibility(View.VISIBLE);
        holder.initials.setText(initialsOf(game.title));

        GradientDrawable gradient = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR, new int[]{GRADIENT[0], GRADIENT[1]});
        gradient.setCornerRadius(14f * holder.itemView.getResources().getDisplayMetrics().density);
        holder.cover.setBackground(gradient);

        holder.itemView.setOnClickListener(v -> listener.onClick(game));
    }

    @Override
    public int getItemCount() {
        return games.size();
    }

    private static String initialsOf(String title) {
        String[] words = title.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.isEmpty() && sb.length() < 2) {
                sb.append(Character.toUpperCase(w.charAt(0)));
            }
        }
        return sb.length() == 0 ? "?" : sb.toString();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final FrameLayout cover;
        final TextView initials;
        final android.widget.ImageView coverImage;
        final TextView title;
        final TextView subtitle;

        Holder(@NonNull View itemView) {
            super(itemView);
            cover = itemView.findViewById(R.id.game_cover);
            initials = itemView.findViewById(R.id.game_initials);
            coverImage = itemView.findViewById(R.id.game_cover_image);
            title = itemView.findViewById(R.id.game_title);
            subtitle = itemView.findViewById(R.id.game_subtitle);
        }
    }
}
