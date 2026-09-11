package org.ppsspp.ppsspp;

import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.MultiTransformation;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;

import java.util.List;

public class StoreAdapter extends RecyclerView.Adapter<StoreAdapter.StoreViewHolder> {

    private static final int[][] GRADIENTS = {
            {0xFF9AE637, 0xFF1B240C},
            {0xFF6FBF3D, 0xFF141C08},
            {0xFFC4F26B, 0xFF222E10},
    };

    private final List<StoreItem> items;

    public StoreAdapter(List<StoreItem> items) {
        this.items = items;
    }

    @NonNull
    @Override
    public StoreViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_store_card, parent, false);
        return new StoreViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull StoreViewHolder holder, int position) {
        StoreItem item = items.get(position);

        holder.category.setText(item.category.toUpperCase());
        holder.title.setText(item.title);
        holder.price.setText(item.getPriceLabel());

        if (item.badge != null && !item.badge.isEmpty()) {
            holder.badge.setVisibility(View.VISIBLE);
            holder.badge.setText(item.badge);
        } else {
            holder.badge.setVisibility(View.GONE);
        }

        if (item.coverUrl != null && !item.coverUrl.isEmpty()) {
            holder.coverImage.setVisibility(View.VISIBLE);
            holder.initials.setVisibility(View.GONE);
            holder.cover.setBackground(null);
            Glide.with(holder.itemView.getContext())
                    .load(item.coverUrl)
                    .apply(RequestOptions.bitmapTransform(
                            new MultiTransformation<>(
                                    new CenterCrop(),
                                    new RoundedCorners((int) dp(holder.itemView, 14))
                            )
                    ))
                    .into(holder.coverImage);
        } else {
            holder.coverImage.setVisibility(View.GONE);
            holder.initials.setVisibility(View.VISIBLE);

            int paletteIndex = Math.abs(item.title.hashCode()) % GRADIENTS.length;
            int[] colors = GRADIENTS[paletteIndex];
            GradientDrawable gradient = new GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    new int[]{colors[0], colors[1]}
            );
            gradient.setCornerRadius(dp(holder.itemView, 14));
            holder.cover.setBackground(gradient);
            holder.initials.setText(initialsOf(item.title));
        }

        holder.actionButton.setOnClickListener(v -> {
            // FASE ATUAL: sem backend/download real ainda -- só confirma
            // visualmente a intenção de compra/download, para validar o
            // fluxo de ponta a ponta antes do Supabase entrar.
            Toast.makeText(v.getContext(),
                    "Download de \"" + item.title + "\" ainda não está disponível -- chega junto com o backend.",
                    Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
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

    static class StoreViewHolder extends RecyclerView.ViewHolder {
        final View cover;
        final ImageView coverImage;
        final TextView initials;
        final TextView badge;
        final TextView category;
        final TextView title;
        final TextView price;
        final TextView actionButton;

        StoreViewHolder(@NonNull View itemView) {
            super(itemView);
            cover = itemView.findViewById(R.id.store_cover);
            coverImage = itemView.findViewById(R.id.store_cover_image);
            initials = itemView.findViewById(R.id.store_initials);
            badge = itemView.findViewById(R.id.store_badge);
            category = itemView.findViewById(R.id.store_category);
            title = itemView.findViewById(R.id.store_title);
            price = itemView.findViewById(R.id.store_price);
            actionButton = itemView.findViewById(R.id.store_action_button);
        }
    }
}
