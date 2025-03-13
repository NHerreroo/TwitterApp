package com.example.redsocial;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

import io.appwrite.Client;
import io.appwrite.coroutines.CoroutineCallback;
import io.appwrite.exceptions.AppwriteException;
import io.appwrite.models.DocumentList;
import io.appwrite.services.Account;
import io.appwrite.services.Databases;

public class ProfileFragment extends Fragment {

    ImageView photoImageView;
    FloatingActionButton changeProfilePhotoButton;
    TextView displayNameTextView, emailTextView;
    RecyclerView profilePostsRecyclerView;

    Client client;
    Account account;
    Databases databases;
    String userId;

    HomeFragment.PostsAdapter postsAdapter;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Inicializar vistas
        photoImageView = view.findViewById(R.id.photoImageView);
        changeProfilePhotoButton = view.findViewById(R.id.changeProfilePhotoButton);
        displayNameTextView = view.findViewById(R.id.displayNameTextView);
        emailTextView = view.findViewById(R.id.emailTextView);
        profilePostsRecyclerView = view.findViewById(R.id.profilePostsRecyclerView);

        // Inicializar Appwrite
        client = new Client(requireContext()).setProject(getString(R.string.APPWRITE_PROJECT_ID));
        account = new Account(client);
        databases = new Databases(client);

        // Configurar RecyclerView
        postsAdapter = new HomeFragment().new PostsAdapter();
        profilePostsRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        profilePostsRecyclerView.setAdapter(postsAdapter);

        // Obtener perfil y posts
        obtenerPerfilUsuario();
    }

    private void obtenerPerfilUsuario() {
        try {
            account.get(new CoroutineCallback<>((result, error) -> {
                if (error != null) {
                    error.printStackTrace();
                    return;
                }

                userId = result.getId();

                requireActivity().runOnUiThread(() -> {
                    displayNameTextView.setText(result.getName());
                    emailTextView.setText(result.getEmail());
                });

                obtenerPostsDelUsuario();
                cargarFotoDePerfil();
            }));
        } catch (AppwriteException e) {
            e.printStackTrace();
        }
    }

    private void cargarFotoDePerfil() {
        try {
            databases.getDocument(
                    getString(R.string.APPWRITE_DATABASE_ID),
                    getString(R.string.APPWRITE_PROFILES_COLLECTION_ID),
                    userId,
                    new CoroutineCallback<>((profileResult, profileError) -> {
                        if (profileError == null && profileResult != null) {
                            String photoUrl = profileResult.getData().get("photoUrl").toString();
                            if (!photoUrl.isEmpty()) {
                                requireActivity().runOnUiThread(() ->
                                        Glide.with(requireView()).load(photoUrl).into(photoImageView));
                            }
                        }
                    })
            );
        } catch (AppwriteException e) {
            e.printStackTrace();
        }
    }

    private void obtenerPostsDelUsuario() {
        try {
            List<String> queries = List.of(
                    io.appwrite.Query.Companion.equal("uid", userId),
                    io.appwrite.Query.Companion.orderDesc("$createdAt")
            );

            databases.listDocuments(
                    getString(R.string.APPWRITE_DATABASE_ID),
                    getString(R.string.APPWRITE_POSTS_COLLECTION_ID),
                    queries,
                    new CoroutineCallback<>((result, error) -> {
                        requireActivity().runOnUiThread(() -> {
                            if (error == null && result != null) {
                                postsAdapter.establecerLista(result);
                            } else {
                                Toast.makeText(getContext(), "Error al obtener los posts: " + (error != null ? error.getMessage() : "Desconocido"), Toast.LENGTH_LONG).show();
                            }
                        });
                    })
            );
        } catch (AppwriteException e) {
            e.printStackTrace();
        }
    }
}
