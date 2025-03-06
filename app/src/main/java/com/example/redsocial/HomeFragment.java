package com.example.redsocial;

import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.appwrite.Client;
import io.appwrite.coroutines.CoroutineCallback;
import io.appwrite.exceptions.AppwriteException;
import io.appwrite.models.DocumentList;
import io.appwrite.services.Account;
import io.appwrite.services.Databases;

public class HomeFragment extends Fragment {

    ImageView photoImageView;
    TextView displayNameTextView, emailTextView;
    Client client;
    Account account;
    String userId;
    PostsAdapter adapter;
    AppViewModel appViewModel;
    NavController navController;

    Databases databases;

    public HomeFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        appViewModel = new ViewModelProvider(requireActivity()).get(AppViewModel.class);

        appViewModel.repostUpdated.observe(getViewLifecycleOwner(), repostUpdated -> {
            if (repostUpdated != null && repostUpdated) {
                obtenerPosts();
                appViewModel.repostUpdated.setValue(false);
            }
        });

        client = new Client(requireContext()).setProject(getString(R.string.APPWRITE_PROJECT_ID));
        account = new Account(client);
        databases = new Databases(client);

        navController = Navigation.findNavController(view);
        NavigationView navigationView = view.getRootView().findViewById(R.id.nav_view);
        View header = navigationView.getHeaderView(0);
        photoImageView = header.findViewById(R.id.imageView);
        displayNameTextView = header.findViewById(R.id.displayNameTextView);
        emailTextView = header.findViewById(R.id.emailTextView);

        ImageView navProfilePhoto = header.findViewById(R.id.imageView);
        TextView navDisplayName = header.findViewById(R.id.displayNameTextView);
        TextView navEmail = header.findViewById(R.id.emailTextView);

        try {
            account.get(new CoroutineCallback<>((result, error) -> {
                if (error != null) {
                    error.printStackTrace();
                    return;
                }

                requireActivity().runOnUiThread(() -> {
                    navDisplayName.setText(result.getName());
                    navEmail.setText(result.getEmail());
                });

                String userId = result.getId();
                try {
                    databases.getDocument(getString(R.string.APPWRITE_DATABASE_ID), getString(R.string.APPWRITE_PROFILES_COLLECTION_ID), userId, new CoroutineCallback<>((profileResult, profileError) -> {
                        requireActivity().runOnUiThread(() -> {
                            if (profileResult != null && profileResult.getData() != null) {
                                Object photoUrlObj = profileResult.getData().get("photoUrl");
                                String photoUrl = (photoUrlObj != null) ? photoUrlObj.toString() : "";

                                if (!photoUrl.isEmpty()) {
                                    Glide.with(requireView()).load(photoUrl).error(R.drawable.user).circleCrop().into(navProfilePhoto);
                                } else {
                                    navProfilePhoto.setImageResource(R.drawable.user);
                                }
                            } else {
                                navProfilePhoto.setImageResource(R.drawable.user);
                            }
                        });
                    }));
                } catch (AppwriteException e) {
                    e.printStackTrace();
                }
            }));
        } catch (AppwriteException e) {
            e.printStackTrace();
        }

        Handler mainHandler = new Handler(Looper.getMainLooper());
        try {
            account.get(new CoroutineCallback<>((result, error) -> {
                if (error != null) {
                    error.printStackTrace();
                    return;
                }
                mainHandler.post(() -> {
                    userId = result.getId();
                    displayNameTextView.setText(result.getName());
                    emailTextView.setText(result.getEmail());
                    Glide.with(requireView()).load(R.drawable.user).into(photoImageView);
                    obtenerPosts();
                });
            }));
        } catch (AppwriteException e) {
            throw new RuntimeException(e);
        }

        view.findViewById(R.id.gotoNewPostFragmentButton).setOnClickListener(v -> {
            navController.navigate(R.id.newPostFragment);
        });

        RecyclerView postsRecyclerView = view.findViewById(R.id.postsRecyclerView);
        adapter = new PostsAdapter();
        postsRecyclerView.setAdapter(adapter);

        appViewModel = new ViewModelProvider(requireActivity()).get(AppViewModel.class);
    }

    void obtenerPosts() {
        Databases databases = new Databases(client);
        Handler mainHandler = new Handler(Looper.getMainLooper());
        try {
            databases.listDocuments(getString(R.string.APPWRITE_DATABASE_ID), getString(R.string.APPWRITE_POSTS_COLLECTION_ID), new ArrayList<>(), new CoroutineCallback<>((result, error) -> {
                if (error != null) {
                    Snackbar.make(requireView(), "Error al obtener los posts: " + error.toString(), Snackbar.LENGTH_LONG).show();
                    return;
                }
                mainHandler.post(() -> adapter.establecerLista(result));
            }));
        } catch (AppwriteException e) {
            throw new RuntimeException(e);
        }
    }

    class PostViewHolder extends RecyclerView.ViewHolder {
        ImageView authorPhotoImageView, likeImageView, mediaImageView;
        TextView authorTextView, contentTextView, numLikesTextView;
        ImageButton repostButton;
        Button deleteButton;

        PostViewHolder(@NonNull View itemView) {
            super(itemView);
            authorPhotoImageView = itemView.findViewById(R.id.authorPhotoImageView);
            likeImageView = itemView.findViewById(R.id.likeImageView);
            mediaImageView = itemView.findViewById(R.id.mediaImage);
            authorTextView = itemView.findViewById(R.id.authorTextView);
            contentTextView = itemView.findViewById(R.id.contentTextView);
            numLikesTextView = itemView.findViewById(R.id.numLikesTextView);
            deleteButton = itemView.findViewById(R.id.deleteButton);
            repostButton = itemView.findViewById(R.id.repostButton);
        }
    }

    class PostsAdapter extends RecyclerView.Adapter<PostViewHolder> {
        DocumentList<Map<String, Object>> lista = null;

        @NonNull
        @Override
        public PostViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new PostViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.viewholder_post, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull PostViewHolder holder, int position) {
            Map<String, Object> post = lista.getDocuments().get(position).getData();

            // Manejo de la foto de perfil del autor
            Object photoUrlObj = post.get("authorPhotoUrl");
            String authorPhotoUrl = (photoUrlObj != null) ? photoUrlObj.toString() : "";

            if (!authorPhotoUrl.isEmpty()) {
                Glide.with(getContext()).load(authorPhotoUrl).circleCrop().error(R.drawable.user).placeholder(R.drawable.user).into(holder.authorPhotoImageView);
            } else {
                holder.authorPhotoImageView.setImageResource(R.drawable.user);
            }

            String postAuthorId = post.get("uid").toString();
            String author = post.get("author").toString();
            String content = post.get("content").toString();
            String mediaUrl = post.get("mediaUrl") != null ? post.get("mediaUrl").toString() : null;
            String mediaType = post.get("mediaType") != null ? post.get("mediaType").toString() : null;

            holder.repostButton.setOnClickListener(view -> {
                repostearPublicacion(author, content, mediaUrl, mediaType);
            });

            if (postAuthorId.equals(userId)) {
                holder.deleteButton.setVisibility(View.VISIBLE);
                holder.deleteButton.setOnClickListener(v -> eliminarPost(post.get("$id").toString()));
            } else {
                holder.deleteButton.setVisibility(View.GONE);
            }

            holder.authorTextView.setText(author);
            holder.contentTextView.setText(content);

            // Gestión de likes
            List<String> likes = (List<String>) post.get("likes");
            if (likes.contains(userId)) {
                holder.likeImageView.setImageResource(R.drawable.like_on);
            } else {
                holder.likeImageView.setImageResource(R.drawable.like_off);
            }
            holder.numLikesTextView.setText(String.valueOf(likes.size()));
            holder.likeImageView.setOnClickListener(view -> {
                Databases databases = new Databases(client);
                Handler mainHandler = new Handler(Looper.getMainLooper());
                List<String> nuevosLikes = new ArrayList<>(likes);
                if (nuevosLikes.contains(userId)) {
                    nuevosLikes.remove(userId);
                } else {
                    nuevosLikes.add(userId);
                }
                Map<String, Object> data = new HashMap<>();
                data.put("likes", nuevosLikes);
                try {
                    databases.updateDocument(getString(R.string.APPWRITE_DATABASE_ID), getString(R.string.APPWRITE_POSTS_COLLECTION_ID), post.get("$id").toString(), data, new ArrayList<>(), new CoroutineCallback<>((result, error) -> {
                        if (error != null) {
                            error.printStackTrace();
                            return;
                        }
                        mainHandler.post(() -> obtenerPosts());
                    }));
                } catch (AppwriteException e) {
                    throw new RuntimeException(e);
                }
            });

            // Miniatura de media
            if (mediaUrl != null) {
                holder.mediaImageView.setVisibility(View.VISIBLE);
                if ("audio".equals(mediaType)) {
                    Glide.with(requireView()).load(R.drawable.audio).centerCrop().into(holder.mediaImageView);
                } else {
                    Glide.with(requireView()).load(mediaUrl).centerCrop().into(holder.mediaImageView);
                }
                holder.mediaImageView.setOnClickListener(view -> {
                    appViewModel.postSeleccionado.setValue(post);
                    navController.navigate(R.id.mediaFragment);
                });
            } else {
                holder.mediaImageView.setVisibility(View.GONE);
            }
        }

        void eliminarPost(String postId) {
            new AlertDialog.Builder(requireContext()).setTitle("Eliminar post").setMessage("¿Estás seguro de que quieres eliminar este post?").setPositiveButton("Sí", (dialog, which) -> {
                Databases databases = new Databases(client);
                databases.deleteDocument(getString(R.string.APPWRITE_DATABASE_ID), getString(R.string.APPWRITE_POSTS_COLLECTION_ID), postId, new CoroutineCallback<>((result, error) -> {
                    if (error != null) {
                        error.printStackTrace();
                        Snackbar.make(requireView(), "Error al eliminar", Snackbar.LENGTH_LONG).show();
                        return;
                    }
                    Snackbar.make(requireView(), "Post eliminado", Snackbar.LENGTH_SHORT).show();
                    obtenerPosts();
                }));
            }).setNegativeButton("Cancelar", null).show();
        }

        private void repostearPublicacion(String author, String originalContent, String mediaUrl, String mediaType) {
            try {
                account.get(new CoroutineCallback<>((result, error) -> {
                    if (error != null) return;

                    String currentUserId = result.getId();
                    String currentUserName = result.getName();

                    try {
                        databases.getDocument(getString(R.string.APPWRITE_DATABASE_ID), getString(R.string.APPWRITE_PROFILES_COLLECTION_ID), currentUserId, new CoroutineCallback<>((profileResult, profileError) -> {
                            String currentUserPhotoUrl = "";
                            if (profileResult != null && profileResult.getData() != null) {
                                Object photoUrlObj = profileResult.getData().get("photoUrl");
                                currentUserPhotoUrl = (photoUrlObj != null) ? photoUrlObj.toString() : "";
                            }

                            Map<String, Object> data = new HashMap<>();
                            data.put("uid", currentUserId);
                            data.put("author", currentUserName);
                            data.put("authorPhotoUrl", currentUserPhotoUrl != null ? currentUserPhotoUrl : "");
                            data.put("content", "Reposteado de @" + author + ": " + originalContent);
                            data.put("mediaUrl", mediaUrl);
                            data.put("mediaType", mediaType);
                            data.put("likes", new ArrayList<>());

                            try {
                                databases.createDocument(getString(R.string.APPWRITE_DATABASE_ID), getString(R.string.APPWRITE_POSTS_COLLECTION_ID), "unique()", data, new ArrayList<>(), new CoroutineCallback<>((result2, error2) -> {
                                    requireActivity().runOnUiThread(() -> {
                                        if (error2 == null) {
                                            appViewModel.repostUpdated.postValue(true);
                                            Toast.makeText(requireContext(), "Reposteado con éxito", Toast.LENGTH_SHORT).show();
                                            obtenerPosts();
                                        } else {
                                            Toast.makeText(requireContext(), "Error: " + error2.getMessage(), Toast.LENGTH_SHORT).show();
                                        }
                                    });
                                }));
                            } catch (AppwriteException e) {
                                e.printStackTrace();
                            }
                        }));
                    } catch (AppwriteException e) {
                        e.printStackTrace();
                    }
                }));
            } catch (AppwriteException e) {
                e.printStackTrace();
            }
        }

        @Override
        public int getItemCount() {
            return lista == null ? 0 : lista.getDocuments().size();
        }

        public void establecerLista(DocumentList<Map<String, Object>> lista) {
            this.lista = lista;
            notifyDataSetChanged();
        }
    }
}