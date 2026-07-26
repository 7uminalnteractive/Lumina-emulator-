package org.ppsspp.ppsspp;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;

import java.util.List;

public class GameAdapter extends RecyclerView.Adapter<GameAdapter.GameViewHolder> {

    private static final int[][] GRADIENTS = {
            {0xFF1A6BFF, 0xFF04070F},
            {0xFF6C5CE7, 0xFF0C1528},
            {0xFF00C2A8, 0xFF04070F},
            {0xFFFF6B6B, 0xFF0C1528},
            {0xFFFDCB6E, 0xFF04070F},
    };

    private final List<GameItem> games;

    public GameAdapter(List<GameItem> games) {
        this.games = games;
    }

    @NonNull
    @Override
    public GameViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_game_card, parent, false);
        return new GameViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull GameViewHolder holder, int position) {
        GameItem game = games.get(position);

        holder.title.setText(game.getTitle());
        holder.subtitle.setText(game.extension.toUpperCase() + " · " + game.getSizeLabel());

        if (game.coverUri != null) {
            holder.coverImage.setVisibility(View.VISIBLE);
            holder.initials.setVisibility(View.GONE);
            holder.cover.setBackground(null);
            Glide.with(holder.itemView.getContext())
                    .load(game.coverUri)
                    .apply(RequestOptions.bitmapTransform(
                            new com.bumptech.glide.load.MultiTransformation<>(
                                    new CenterCrop(),
                                    new RoundedCorners((int) dp(holder.itemView, 18))
                            )
                    ))
                    .into(holder.coverImage);
        } else {
            holder.coverImage.setVisibility(View.GONE);
            holder.initials.setVisibility(View.VISIBLE);

            int paletteIndex = Math.abs(game.displayName.hashCode()) % GRADIENTS.length;
            int[] colors = GRADIENTS[paletteIndex];
            GradientDrawable gradient = new GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    new int[]{colors[0], colors[1]}
            );
            gradient.setCornerRadius(dp(holder.itemView, 18));
            holder.cover.setBackground(gradient);
            holder.initials.setText(initialsOf(game.getTitle()));
        }

        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(v.getContext(), PpssppActivity.class);
            intent.setData(game.contentUri);
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            v.getContext().startActivity(intent);
        });
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
        return sb.length() > 0 ? sb.toString() : "?";
    }

    private static float dp(View view, int value) {
        return value * view.getResources().getDisplayMetrics().density;
    }

    static class GameViewHolder extends RecyclerView.ViewHolder {
        final View cover;
        final ImageView coverImage;
        final TextView initials;
        final TextView title;
        final TextView subtitle;

        GameViewHolder(@NonNull View itemView) {
            super(itemView);
            cover = itemView.findViewById(R.id.game_cover);
            coverImage = itemView.findViewById(R.id.game_cover_image);
            initials = itemView.findViewById(R.id.game_initials);
            title = itemView.findViewById(R.id.game_title);
            subtitle = itemView.findViewById(R.id.game_subtitle);
        }
    }
}
