package com.example.redsocial;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import io.appwrite.Query;

import io.appwrite.Client;
import io.appwrite.coroutines.CoroutineCallback;
import io.appwrite.exceptions.AppwriteException;
import io.appwrite.models.DocumentList;
import io.appwrite.services.Account;
import io.appwrite.services.Databases;

public class CommentsFragment extends Fragment {

    private String postId;
    private CommentsAdapter adapter;
    private Databases databases;
    private Client client;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_comments, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        client = new Client(requireContext()).setProject(getString(R.string.APPWRITE_PROJECT_ID));
        databases = new Databases(client);

        postId = getArguments().getString("postId");

        // Obtener el UID del usuario actual
        Account account = new Account(client);
        try {
            account.get(new CoroutineCallback<>((user, error) -> {
                requireActivity().runOnUiThread(() -> {
                    if (error == null && user != null) {
                        String currentUserId = user.getId();
                        adapter = new CommentsAdapter(currentUserId, client);

                        RecyclerView recyclerView = view.findViewById(R.id.commentsRecyclerView);
                        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
                        recyclerView.setAdapter(adapter);

                        obtenerComentarios();
                    }
                });
            }));
        } catch (AppwriteException e) {
            throw new RuntimeException(e);
        }
    }

    private void obtenerComentarios() {
        try {
            List<String> queries = List.of(Query.Companion.equal("postId", postId));

            databases.listDocuments(
                    getString(R.string.APPWRITE_DATABASE_ID),
                    getString(R.string.APPWRITE_COMMENTS_COLLECTION_ID),
                    queries,
                    new CoroutineCallback<>((result, error) -> {
                        requireActivity().runOnUiThread(() -> {
                            if (error == null && result != null) {
                                adapter.setComments(result);  // Actualiza el adaptador con los nuevos datos
                            } else {
                                Toast.makeText(getContext(), "Error al obtener comentarios: " + (error != null ? error.getMessage() : "Desconocido"), Toast.LENGTH_LONG).show();
                                if (error != null) error.printStackTrace();
                            }
                        });
                    })
            );
        } catch (AppwriteException e) {
            e.printStackTrace();
            requireActivity().runOnUiThread(() ->
                    Toast.makeText(getContext(), "Excepción al obtener comentarios: " + e.getMessage(), Toast.LENGTH_LONG).show()
            );
        }
    }


    private void publicarComentario(String content) {
        Account account = new Account(client);

        try {
            // Obtener información del usuario autenticado
            account.get(new CoroutineCallback<>((user, error) -> {
                requireActivity().runOnUiThread(() -> {
                    if (error != null) {
                        Toast.makeText(getContext(), "Error al obtener el usuario: " + error.getMessage(), Toast.LENGTH_LONG).show();
                        error.printStackTrace();
                        return;
                    }

                    String uid = user.getId();
                    String autor = user.getName();

                    // Ahora creamos el comentario con los datos del usuario
                    Map<String, Object> data = new HashMap<>();
                    data.put("postId", postId);
                    data.put("content", content);
                    data.put("autor", autor);
                    data.put("uid", uid);

                    try {
                        databases.createDocument(
                                getString(R.string.APPWRITE_DATABASE_ID),
                                getString(R.string.APPWRITE_COMMENTS_COLLECTION_ID),
                                "unique()",
                                data,
                                new ArrayList<>(),
                                new CoroutineCallback<>((result, commentError) -> {
                                    requireActivity().runOnUiThread(() -> {
                                        if (commentError == null) {
                                            obtenerComentarios(); // Refrescar comentarios
                                            Toast.makeText(getContext(), "Comentario publicado", Toast.LENGTH_SHORT).show();
                                        } else {
                                            Toast.makeText(getContext(), "Error al comentar: " + commentError.getMessage(), Toast.LENGTH_LONG).show();
                                            commentError.printStackTrace();
                                        }
                                    });
                                })
                        );
                    } catch (AppwriteException e) {
                        e.printStackTrace();
                        Toast.makeText(getContext(), "Error al crear comentario: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
            }));
        } catch (AppwriteException e) {
            e.printStackTrace();
            requireActivity().runOnUiThread(() ->
                    Toast.makeText(getContext(), "Error al obtener usuario: " + e.getMessage(), Toast.LENGTH_LONG).show()
            );
        }
    }

}
