package com.example.smart_travel_ai_litimi_issam.activities;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import com.example.smart_travel_ai_litimi_issam.R;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private TextInputEditText etEmail, etPassword, etName;
    private ImageView ivRegisterPhoto;
    private View layoutAvatarDefault;
    private ProgressBar progressBar;

    // Photo choisie en Base64 (null si aucune photo sélectionnée)
    private String selectedPhotoBase64 = null;

    // Launcher galerie
    private final ActivityResultLauncher<String> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    processSelectedImage(uri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        mAuth = FirebaseAuth.getInstance();
        db   = FirebaseFirestore.getInstance();

        etName              = findViewById(R.id.etName);
        etEmail             = findViewById(R.id.etEmail);
        etPassword          = findViewById(R.id.etPassword);
        progressBar         = findViewById(R.id.registerProgressBar);
        ivRegisterPhoto     = findViewById(R.id.ivRegisterPhoto);
        layoutAvatarDefault = findViewById(R.id.layoutAvatarDefault);

        Button btnRegister = findViewById(R.id.btnRegister);
        Button btnGoLogin  = findViewById(R.id.btnGoLogin);

        // L'avatar entier est cliquable (CardView)
        findViewById(R.id.avatarContainer).setOnClickListener(v -> imagePickerLauncher.launch("image/*"));

        btnRegister.setOnClickListener(v -> registerUser());
        btnGoLogin.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }

    // ─────────────────────────────────────────────
    //  Traitement de l'image sélectionnée
    // ─────────────────────────────────────────────
    private void processSelectedImage(Uri uri) {
        progressBar.setVisibility(View.VISIBLE);

        new Thread(() -> {
            try {
                InputStream inputStream = getContentResolver().openInputStream(uri);
                Bitmap original = BitmapFactory.decodeStream(inputStream);
                Bitmap resized = resizeBitmap(original, 300);

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                resized.compress(Bitmap.CompressFormat.JPEG, 75, baos);
                byte[] bytes = baos.toByteArray();

                String base64 = Base64.encodeToString(bytes, Base64.DEFAULT);

                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    selectedPhotoBase64 = base64;

                    ivRegisterPhoto.setImageBitmap(resized);
                    ivRegisterPhoto.setVisibility(View.VISIBLE);
                    layoutAvatarDefault.setVisibility(View.GONE);

                    Toast.makeText(this, "Photo sélectionnée ✓", Toast.LENGTH_SHORT).show();
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Erreur lecture image : " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    // ─────────────────────────────────────────────
    //  Inscription
    // ─────────────────────────────────────────────
    private void registerUser() {
        String name     = etName.getText().toString().trim();
        String email    = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Remplis tous les champs", Toast.LENGTH_SHORT).show();
            return;
        }
        if (password.length() < 6) {
            Toast.makeText(this, "Mot de passe minimum 6 caractères", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    String uid = mAuth.getCurrentUser().getUid();

                    Map<String, Object> user = new HashMap<>();
                    user.put("name", name);
                    user.put("email", email);
                    user.put("uid", uid);
                    user.put("createdAt", System.currentTimeMillis());

                    if (selectedPhotoBase64 != null) {
                        user.put("photoBase64", selectedPhotoBase64);
                    }

                    db.collection("users").document(uid).set(user)
                            .addOnSuccessListener(unused -> {
                                progressBar.setVisibility(View.GONE);
                                Toast.makeText(this, "Compte créé ! Veuillez vous connecter.", Toast.LENGTH_SHORT).show();
                                mAuth.signOut();
                                Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                startActivity(intent);
                                finish();
                            })
                            .addOnFailureListener(e -> {
                                progressBar.setVisibility(View.GONE);
                                Toast.makeText(this, "Erreur base de données : " + e.getMessage(), Toast.LENGTH_LONG).show();
                            });
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Erreur : " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private Bitmap resizeBitmap(Bitmap original, int maxSize) {
        int width  = original.getWidth();
        int height = original.getHeight();
        float scale = Math.min((float) maxSize / width, (float) maxSize / height);
        return Bitmap.createScaledBitmap(original, Math.round(width * scale), Math.round(height * scale), true);
    }
}
