package com.example.smart_travel_ai_litimi_issam.adapters;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smart_travel_ai_litimi_issam.R;
import com.example.smart_travel_ai_litimi_issam.models.ChatMessage;

import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ViewHolder> {

    public interface OnShowMapListener {
        void onShowMap(String placeName, double lat, double lng);
    }

    private final List<ChatMessage> messages;
    private final OnShowMapListener mapListener;
    private final Context context;

    public ChatAdapter(Context context, List<ChatMessage> messages, OnShowMapListener mapListener) {
        this.context     = context;
        this.messages    = messages;
        this.mapListener = mapListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_message, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ChatMessage msg = messages.get(position);

        if (msg.getType() == ChatMessage.Type.TYPING) {
            // Indicateur "..." pendant que l'IA répond
            holder.tvMessage.setText("...");
            holder.tvMessage.setTextColor(Color.parseColor("#666666"));
            holder.bubbleCard.setCardBackgroundColor(Color.parseColor("#EEEEEE"));
            holder.messageRoot.setGravity(Gravity.START);
            ((LinearLayout.LayoutParams) holder.bubbleCard.getLayoutParams()).gravity = Gravity.START;
            holder.btnShowOnMap.setVisibility(View.GONE);

        } else if (msg.getType() == ChatMessage.Type.USER) {
            // Bulle utilisateur → droite, fond noir
            holder.tvMessage.setText(msg.getText());
            holder.tvMessage.setTextColor(Color.WHITE);
            holder.bubbleCard.setCardBackgroundColor(Color.BLACK);
            holder.messageRoot.setGravity(Gravity.END);
            ((LinearLayout.LayoutParams) holder.bubbleCard.getLayoutParams()).gravity = Gravity.END;
            holder.btnShowOnMap.setVisibility(View.GONE);

        } else {
            // Bulle IA → gauche, fond gris clair
            holder.tvMessage.setText(msg.getText());
            holder.tvMessage.setTextColor(Color.parseColor("#111111"));
            holder.bubbleCard.setCardBackgroundColor(Color.parseColor("#EEEEEE"));
            holder.messageRoot.setGravity(Gravity.START);
            ((LinearLayout.LayoutParams) holder.bubbleCard.getLayoutParams()).gravity = Gravity.START;

            // Afficher le bouton carte si l'IA a proposé un lieu
            if (msg.hasPlace()) {
                holder.btnShowOnMap.setVisibility(View.VISIBLE);
                holder.btnShowOnMap.setOnClickListener(v -> {
                    if (mapListener != null) {
                        mapListener.onShowMap(msg.getPlaceName(), msg.getPlaceLat(), msg.getPlaceLng());
                    }
                });
            } else {
                holder.btnShowOnMap.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public int getItemCount() { return messages.size(); }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        LinearLayout messageRoot;
        CardView     bubbleCard;
        TextView     tvMessage;
        Button       btnShowOnMap;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            messageRoot  = (LinearLayout) itemView;
            bubbleCard   = itemView.findViewById(R.id.bubbleCard);
            tvMessage    = itemView.findViewById(R.id.tvMessage);
            btnShowOnMap = itemView.findViewById(R.id.btnShowOnMap);
        }
    }
}