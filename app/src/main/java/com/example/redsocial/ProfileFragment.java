package com.example.redsocial;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import io.appwrite.Client;
import io.appwrite.coroutines.CoroutineCallback;
import io.appwrite.exceptions.AppwriteException;
import io.appwrite.models.Document;
import io.appwrite.models.InputFile;
import io.appwrite.services.Account;
import io.appwrite.services.Databases;
import io.appwrite.services.Storage;

public class ProfileFragment extends Fragment {

    ImageView photoImageView;
    Button changeProfilePhotoButton;
    Client client;
    Account account;
    Storage storage;
    Databases databases;
    String userId;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        photoImageView = view.findViewById(R.id.photoImageView);
        changeProfilePhotoButton = view.findViewById(R.id.changeProfilePhotoButton);

        // Inicializar Appwrite
        client = new Client(requireContext()).setProject(getString(R.string.APPWRITE_PROJECT_ID));
        account = new Account(client);
        storage = new Storage(client);
        databases = new Databases(client);

        // Obtener el perfil del usuario
        obtenerPerfilUsuario();

        // Configurar el botón para cambiar la foto de perfil
        changeProfilePhotoButton.setOnClickListener(v -> seleccionarImagen());
    }

    private void obtenerPerfilUsuario() {
        try {
            account.get(new CoroutineCallback<>((result, error) -> {
                if (error != null) {
                    error.printStackTrace();
                    return;
                }

                userId = result.getId();

                // Obtener el perfil del usuario desde la colección "profiles"
                try {
                    databases.getDocument(
                            getString(R.string.APPWRITE_DATABASE_ID),
                            getString(R.string.APPWRITE_PROFILES_COLLECTION_ID),
                            userId, // Usamos el UID como ID del documento
                            new CoroutineCallback<>((profileResult, profileError) -> {
                                if (profileError != null) {
                                    // Si no existe un perfil, creamos uno nuevo
                                    crearPerfilUsuario();
                                    return;
                                }

                                // Mostrar la foto de perfil
                                String photoUrl = profileResult.getData().get("photoUrl").toString();
                                if (photoUrl != null && !photoUrl.isEmpty()) {
                                    requireActivity().runOnUiThread(() ->
                                            Glide.with(requireView()).load(photoUrl).into(photoImageView)
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
    }

    private void crearPerfilUsuario() {
        Map<String, Object> data = new HashMap<>();
        data.put("uid", userId);
        data.put("photoUrl", ""); // Inicialmente no hay foto de perfil

        try {
            databases.createDocument(
                    getString(R.string.APPWRITE_DATABASE_ID),
                    getString(R.string.APPWRITE_PROFILES_COLLECTION_ID),
                    userId, // Usamos el UID como ID del documento
                    data,
                    new ArrayList<>(),
                    new CoroutineCallback<>((result, error) -> {
                        if (error != null) {
                            error.printStackTrace();
                            return;
                        }
                        Toast.makeText(requireContext(), "Perfil creado", Toast.LENGTH_SHORT).show();
                    })
            );
        } catch (AppwriteException e) {
            e.printStackTrace();
        }
    }

    private final ActivityResultLauncher<String> seleccionarImagenLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    subirImagenAAppwrite(uri);
                }
            });

    private void seleccionarImagen() {
        seleccionarImagenLauncher.launch("image/*");
    }

    private void subirImagenAAppwrite(Uri imageUri) {
        File tempFile;
        try {
            tempFile = getFileFromUri(requireContext(), imageUri);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(requireContext(), "Error al procesar la imagen", Toast.LENGTH_SHORT).show();
            return;
        }

        storage.createFile(
                getString(R.string.APPWRITE_STORAGE_BUCKET_ID),
                "unique()",
                InputFile.Companion.fromFile(tempFile),
                new ArrayList<>(),
                new CoroutineCallback<>((result, error) -> {
                    if (error != null) {
                        error.printStackTrace();
                        requireActivity().runOnUiThread(() ->
                                Toast.makeText(requireContext(), "Error al subir la imagen", Toast.LENGTH_SHORT).show()
                        );
                        return;
                    }

                    // Obtener la URL de la imagen subida
                    String downloadUrl = "https://cloud.appwrite.io/v1/storage/buckets/"
                            + getString(R.string.APPWRITE_STORAGE_BUCKET_ID)
                            + "/files/" + result.getId()
                            + "/view?project=" + getString(R.string.APPWRITE_PROJECT_ID);

                    // Actualizar la foto de perfil en la colección "profiles"
                    actualizarFotoPerfil(downloadUrl);
                })
        );
    }

    private void actualizarFotoPerfil(String photoUrl) {
        Map<String, Object> data = new HashMap<>();
        data.put("photoUrl", photoUrl);

        try {
            databases.updateDocument(
                    getString(R.string.APPWRITE_DATABASE_ID),
                    getString(R.string.APPWRITE_PROFILES_COLLECTION_ID),
                    userId, // Usamos el UID como ID del documento
                    data,
                    new ArrayList<>(),
                    new CoroutineCallback<>((result, error) -> {
                        if (error != null) {
                            error.printStackTrace();
                            requireActivity().runOnUiThread(() ->
                                    Toast.makeText(requireContext(), "Error al actualizar la foto de perfil", Toast.LENGTH_SHORT).show()
                            );
                            return;
                        }

                        // Mostrar la nueva foto de perfil
                        requireActivity().runOnUiThread(() -> {
                            Glide.with(requireView()).load(photoUrl).into(photoImageView);
                            Toast.makeText(requireContext(), "Foto de perfil actualizada", Toast.LENGTH_SHORT).show();
                        });
                    })
            );
        } catch (AppwriteException e) {
            e.printStackTrace();
        }
    }

    public File getFileFromUri(Context context, Uri uri) throws Exception {
        InputStream inputStream = context.getContentResolver().openInputStream(uri);
        if (inputStream == null) {
            throw new FileNotFoundException("No se pudo abrir el URI: " + uri);
        }

        String fileName = "temp_file";
        File tempFile = new File(context.getCacheDir(), fileName);
        FileOutputStream outputStream = new FileOutputStream(tempFile);
        byte[] buffer = new byte[1024];
        int length;
        while ((length = inputStream.read(buffer)) > 0) {
            outputStream.write(buffer, 0, length);
        }

        outputStream.close();
        inputStream.close();
        return tempFile;
    }
}