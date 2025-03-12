package com.example.redsocial;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.appwrite.Client;
import io.appwrite.coroutines.CoroutineCallback;
import io.appwrite.exceptions.AppwriteException;
import io.appwrite.models.Document;
import io.appwrite.models.DocumentList;
import io.appwrite.services.Databases;

public class CommentsAdapter extends RecyclerView.Adapter<CommentsAdapter.CommentViewHolder> {

    private List<Document<Map<String, Object>>> comments = new ArrayList<>();
    private String currentUserId;
    private Databases databases;
    private Client client;

    public CommentsAdapter(String currentUserId, Client client) {
        this.currentUserId = currentUserId;
        this.client = client;
        this.databases = new Databases(client);
    }

    public void setComments(DocumentList<Map<String, Object>> result) {
        comments.clear();
        comments.addAll(result.getDocuments());
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public CommentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.viewholder_comment, parent, false);
        return new CommentViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CommentViewHolder holder, int position) {
        Document<Map<String, Object>> doc = comments.get(position);
        Map<String, Object> comment = doc.getData();

        holder.contentTextView.setText(comment.get("content").toString());
        holder.autorTextView.setText(comment.get("autor").toString());

        // Cargar la foto de perfil
        String photoUrl = comment.get("authorPhotoUrl") != null ? comment.get("authorPhotoUrl").toString() : "";
        if (!photoUrl.isEmpty()) {
            Glide.with(holder.itemView).load(photoUrl).circleCrop().into(holder.profileImageView);
        } else {
            holder.profileImageView.setImageResource(R.drawable.user);
        }

        // Mostrar el botón de eliminar solo si el comentario es del usuario actual
        if (currentUserId.equals(comment.get("uid"))) {
            holder.deleteButton.setVisibility(View.VISIBLE);
            holder.deleteButton.setOnClickListener(v -> eliminarComentario(doc.getId(), holder));
        } else {
            holder.deleteButton.setVisibility(View.GONE);
        }
    }

    private void eliminarComentario(String commentId, CommentViewHolder holder) {
        databases.deleteDocument(
                holder.itemView.getContext().getString(R.string.APPWRITE_DATABASE_ID),
                holder.itemView.getContext().getString(R.string.APPWRITE_COMMENTS_COLLECTION_ID),
                commentId,
                new CoroutineCallback<>((result, error) -> {
                    holder.itemView.post(() -> {
                        if (error == null) {
                            comments.removeIf(doc -> doc.getId().equals(commentId));
                            notifyDataSetChanged();
                            Toast.makeText(holder.itemView.getContext(), "Comentario eliminado", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(holder.itemView.getContext(), "Error al eliminar: " + error.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                })
        );
    }

    @Override
    public int getItemCount() {
        return comments.size();
    }

    static class CommentViewHolder extends RecyclerView.ViewHolder {
        TextView contentTextView, autorTextView;
        ImageView profileImageView;
        Button deleteButton;

        CommentViewHolder(@NonNull View itemView) {
            super(itemView);
            contentTextView = itemView.findViewById(R.id.commentContentTextView);
            autorTextView = itemView.findViewById(R.id.commentAutorTextView);
            profileImageView = itemView.findViewById(R.id.commentProfileImageView);
            deleteButton = itemView.findViewById(R.id.deleteCommentButton);
        }
    }
}
