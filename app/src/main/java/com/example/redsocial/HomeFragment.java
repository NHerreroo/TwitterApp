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

    Databases databases;// <-----------------

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
                // Volver a cargar los posts
                obtenerPosts();
                // Reiniciar el valor del LiveData
                appViewModel.repostUpdated.setValue(false);
            }
        });


        client = new Client(requireContext()).setProject(getString(R.string.APPWRITE_PROJECT_ID));
        account = new Account(client);
        databases = new Databases(client);

        super.onViewCreated(view, savedInstanceState);
        navController = Navigation.findNavController(view);
        NavigationView navigationView = view.getRootView().findViewById(R.id.nav_view);
        View header = navigationView.getHeaderView(0);
        photoImageView = header.findViewById(R.id.imageView);
        displayNameTextView = header.findViewById(R.id.displayNameTextView);
        emailTextView = header.findViewById(R.id.emailTextView);
        client = new Client(requireContext()).setProject(getString(R.string.APPWRITE_PROJECT_ID));
        account = new Account(client);

        ImageView navProfilePhoto = header.findViewById(R.id.imageView); // ImageView del encabezado
        TextView navDisplayName = header.findViewById(R.id.displayNameTextView);
        TextView navEmail = header.findViewById(R.id.emailTextView);


        try {
            account.get(new CoroutineCallback<>((result, error) -> {
                if (error != null) {
                    error.printStackTrace();
                    return;
                }

                // Mostrar el nombre y el correo del usuario
                requireActivity().runOnUiThread(() -> {
                    navDisplayName.setText(result.getName());
                    navEmail.setText(result.getEmail());
                });

                // Obtener la foto de perfil del usuario desde la colección "profiles"
                String userId = result.getId();
                try {
                    databases.getDocument(
                            getString(R.string.APPWRITE_DATABASE_ID),
                            getString(R.string.APPWRITE_PROFILES_COLLECTION_ID),
                            userId, // Usamos el UID como ID del documento
                            new CoroutineCallback<>((profileResult, profileError) -> {
                                if (profileError != null) {
                                    profileError.printStackTrace();
                                    return;
                                }

                                // Mostrar la foto de perfil
                                String photoUrl = profileResult.getData().get("photoUrl").toString();
                                if (photoUrl != null && !photoUrl.isEmpty()) {
                                    requireActivity().runOnUiThread(() ->
                                            Glide.with(requireView()).load(photoUrl).into(navProfilePhoto)
                                    );
                                }
                            })
                    );
                } catch (AppwriteException e) {
                    throw new RuntimeException(e);
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
                mainHandler.post(() ->
                {
                    userId = result.getId();
                    displayNameTextView.setText(result.getName().toString());
                    emailTextView.setText(result.getEmail().toString());
                    Glide.with(requireView()).load(R.drawable.user).into(photoImageView);
                    obtenerPosts();
                });
            }));
        } catch (AppwriteException e) {
            throw new RuntimeException(e);
        }

        view.findViewById(R.id.gotoNewPostFragmentButton).setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                navController.navigate(R.id.newPostFragment);
            }
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
            databases.listDocuments(
                    getString(R.string.APPWRITE_DATABASE_ID), // databaseId
                    getString(R.string.APPWRITE_POSTS_COLLECTION_ID), // collectionId
                    new ArrayList<>(), // queries (optional)
                    new CoroutineCallback<>((result, error) -> {
                        if (error != null) {
                            Snackbar.make(requireView(), "Error al obtener los posts: "
                                    + error.toString(), Snackbar.LENGTH_LONG).show();
                            return;
                        }
                        System.out.println(result.toString());
                        mainHandler.post(() -> adapter.establecerLista(result));
                    })
            );
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
        public PostViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int
                viewType) {
            return new
                    PostViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.viewholder_post, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull PostViewHolder holder, int position) {
            Map<String, Object> post = lista.getDocuments().get(position).getData();
            String authorPhotoUrl = post.get("authorPhotoUrl") != null ? post.get("authorPhotoUrl").toString() : null;


            if (authorPhotoUrl != null && !authorPhotoUrl.isEmpty()) {
                Glide.with(getContext()).load(authorPhotoUrl).circleCrop().into(holder.authorPhotoImageView);
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

            if (postAuthorId.equals(userId)){
                holder.deleteButton.setVisibility(View.VISIBLE);
                holder.deleteButton.setOnClickListener(v -> eliminarPost(post.get("$id").toString()));
            } else {
                holder.deleteButton.setVisibility(View.GONE);
            }

            if (post.get("authorPhotoUrl") == null) {
                holder.authorPhotoImageView.setImageResource(R.drawable.user);
            } else {

                Glide.with(getContext()).load(post.get("authorPhotoUrl").toString()).circleCrop()
                        .into(holder.authorPhotoImageView);
            }
            holder.authorTextView.setText(post.get("author").toString());
            holder.contentTextView.setText(post.get("content").toString());

            // Gestion de likes
            List<String> likes = (List<String>) post.get("likes");
            if(likes.contains(userId))
                holder.likeImageView.setImageResource(R.drawable.like_on);
            else
                holder.likeImageView.setImageResource(R.drawable.like_off);
            holder.numLikesTextView.setText(String.valueOf(likes.size()));
            holder.likeImageView.setOnClickListener(view -> {
                Databases databases = new Databases(client);
                Handler mainHandler = new Handler(Looper.getMainLooper());
                List<String> nuevosLikes = likes;
                if(nuevosLikes.contains(userId))
                    nuevosLikes.remove(userId);
                else
                    nuevosLikes.add(userId);
                Map<String, Object> data = new HashMap<>();
                data.put("likes", nuevosLikes);
                try {
                    databases.updateDocument(
                            getString(R.string.APPWRITE_DATABASE_ID),
                            getString(R.string.APPWRITE_POSTS_COLLECTION_ID),
                            post.get("$id").toString(), // documentId
                            data, // data (optional)
                            new ArrayList<>(), // permissions (optional)
                            new CoroutineCallback<>((result, error) -> {
                                if (error != null) {
                                    error.printStackTrace();
                                    return;
                                }
                                System.out.println("Likes actualizados:" +
                                        result.toString());
                                mainHandler.post(() -> obtenerPosts());
                            })
                    );
                } catch (AppwriteException e) {
                    throw new RuntimeException(e);
                }
            });

            // Miniatura de media
            if (post.get("mediaUrl") != null) {
                holder.mediaImageView.setVisibility(View.VISIBLE);
                if ("audio".equals(post.get("mediaType").toString())) {

                    Glide.with(requireView()).load(R.drawable.audio).centerCrop().into(holder.mediaImageView);
                } else {

                    Glide.with(requireView()).load(post.get("mediaUrl").toString()).centerCrop().into
                            (holder.mediaImageView);
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
            new AlertDialog.Builder(requireContext())
                    .setTitle("Eliminar post")
                    .setMessage("¿Estás seguro de que quieres eliminar este post?")
                    .setPositiveButton("Sí", (dialog, which) -> {
                        Databases databases = new Databases(client);
                        databases.deleteDocument(
                                getString(R.string.APPWRITE_DATABASE_ID),  // ID de la base de datos
                                getString(R.string.APPWRITE_POSTS_COLLECTION_ID),  // ID de la colección de posts
                                postId,  // ID del post a eliminar
                                new CoroutineCallback<>((result, error) -> {
                                    if (error != null) {
                                        error.printStackTrace();
                                        Snackbar.make(requireView(), "Error al eliminar", Snackbar.LENGTH_LONG).show();
                                        return;
                                    }
                                    Snackbar.make(requireView(), "Post eliminado", Snackbar.LENGTH_SHORT).show();
                                    obtenerPosts(); // Refrescar lista de posts después de eliminar
                                })
                        );
                    })
                    .setNegativeButton("Cancelar", null)
                    .show();
        }

        private void repostearPublicacion(String author, String originalContent, String mediaUrl, String mediaType) {
            try {
                account.get(new CoroutineCallback<>((result, error) -> {
                    if (error != null) {
                        error.printStackTrace();
                        return;
                    }

                    String currentUserId = result.getId();
                    String currentUserName = result.getName();

                    // Crear el contenido de la publicación reposteada
                    String repostContent = "Reposteado de @" + author + ": " + originalContent;

                    // Crear el mapa de datos para la nueva publicación
                    Map<String, Object> data = new HashMap<>();
                    data.put("uid", currentUserId);
                    data.put("author", currentUserName);
                    data.put("content", repostContent);
                    data.put("authorPhotoUrl", null); // Puedes agregar la URL de la foto del usuario si la tienes
                    data.put("likes", new ArrayList<>()); // Inicializar la lista de likes vacía
                    data.put("mediaUrl", mediaUrl); // Incluir el mediaUrl original
                    data.put("mediaType", mediaType); // Incluir el mediaType original

                    // Subir la nueva publicación a Appwrite
                    Databases databases = new Databases(client);
                    try {
                        databases.createDocument(
                                getString(R.string.APPWRITE_DATABASE_ID),
                                getString(R.string.APPWRITE_POSTS_COLLECTION_ID),
                                "unique()", // Generar un ID único automáticamente
                                data,
                                new ArrayList<>(), // Permisos opcionales
                                new CoroutineCallback<>((result2, error2) -> {
                                    if (error2 != null) {
                                        error2.printStackTrace();
                                        return;
                                    }

                                    // Notificar al ViewModel que se ha realizado un reposteo
                                    appViewModel.repostUpdated.postValue(true);

                                    // Notificar al usuario que la publicación se ha reposteado
                                    requireActivity().runOnUiThread(() ->
                                            Toast.makeText(requireContext(), "Reposteado con éxito", Toast.LENGTH_SHORT).show()
                                    );
                                })
                        );
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





