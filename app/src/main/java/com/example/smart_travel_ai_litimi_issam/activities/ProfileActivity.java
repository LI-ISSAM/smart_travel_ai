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
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import com.example.smart_travel_ai_litimi_issam.R;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class ProfileActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private TextView tvDisplayName, tvDisplayEmail, tvAvatarInitial;
    private ImageView ivProfilePhoto;
    private TextInputEditText etNewName;
    private ProgressBar profileProgressBar;

    private final ActivityResultLauncher<String> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    uploadProfilePhoto(uri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);
        // Dans onCreate, après setContentView

        mAuth = FirebaseAuth.getInstance();
        db    = FirebaseFirestore.getInstance();
        // Bouton Avatar
        findViewById(R.id.cardAvatar).setOnClickListener(v -> {
            startActivity(new Intent(this, AvatarActivity.class));
        });

        if (mAuth.getCurrentUser() == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        tvDisplayName      = findViewById(R.id.tvDisplayName);
        tvDisplayEmail     = findViewById(R.id.tvDisplayEmail);
        tvAvatarInitial    = findViewById(R.id.tvAvatarInitial);
        ivProfilePhoto     = findViewById(R.id.ivProfilePhoto);
        etNewName          = findViewById(R.id.etNewName);
        profileProgressBar = findViewById(R.id.profileProgressBar);

        Button btnUpdateName = findViewById(R.id.btnUpdateName);

        findViewById(R.id.avatarContainer).setOnClickListener(v -> imagePickerLauncher.launch("image/*"));

        loadUserProfile();
        btnUpdateName.setOnClickListener(v -> updateName());

        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigationView);
        bottomNav.setSelectedItemId(R.id.nav_journal);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_explore) {
                startActivity(new Intent(this, MainActivity.class));
                overridePendingTransition(0, 0);
                finish();
                return true;
            }
            else if(id == R.id.nav_scan){
                startActivity(new Intent(this,LandmarkScanActivity.class));
                overridePendingTransition(0, 0);
                finish();
                return true;
            }
            else if(id == R.id.nav_avatar){
                startActivity(new Intent(this,AvatarGuideActivity.class));
                overridePendingTransition(0, 0);
                finish();
                return true;
            }

            else if (id == R.id.nav_chat) {
                startActivity(new Intent(this, ChatActivity.class));
                overridePendingTransition(0, 0);
                finish();
                return true;
            }
            return false;
        });
    }


    private void loadUserProfile() {
        String uid   = mAuth.getCurrentUser().getUid();
        String email = mAuth.getCurrentUser().getEmail();
        tvDisplayEmail.setText(email != null ? email : "");

        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String name = doc.getString("name");
                        if (name != null && !name.isEmpty()) {
                            tvDisplayName.setText(name);
                            tvAvatarInitial.setText(String.valueOf(name.charAt(0)).toUpperCase());
                        }
                        String photoBase64 = doc.getString("photoBase64");
                        if (photoBase64 != null && !photoBase64.isEmpty()) {
                            showPhotoFromBase64(photoBase64);
                        }
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Erreur chargement : " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void uploadProfilePhoto(Uri uri) {
        profileProgressBar.setVisibility(View.VISIBLE);

        new Thread(() -> {
            try {
                InputStream inputStream = getContentResolver().openInputStream(uri);
                Bitmap original = BitmapFactory.decodeStream(inputStream);
                Bitmap resized = resizeBitmap(original, 300);

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                resized.compress(Bitmap.CompressFormat.JPEG, 75, baos);
                String base64 = Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);

                runOnUiThread(() -> savePhotoToFirestore(base64));

            } catch (Exception e) {
                runOnUiThread(() -> {
                    profileProgressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Erreur lecture image : " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void savePhotoToFirestore(String base64) {
        String uid = mAuth.getCurrentUser().getUid();
        Map<String, Object> updates = new HashMap<>();
        updates.put("photoBase64", base64);

        db.collection("users").document(uid).update(updates)
                .addOnSuccessListener(unused -> {
                    profileProgressBar.setVisibility(View.GONE);
                    showPhotoFromBase64(base64);
                    Toast.makeText(this, "Photo mise à jour !", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    profileProgressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Erreur sauvegarde : " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void showPhotoFromBase64(String base64) {
        try {
            byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            ivProfilePhoto.setImageBitmap(bitmap);
            ivProfilePhoto.setVisibility(View.VISIBLE);
            tvAvatarInitial.setVisibility(View.GONE);
        } catch (Exception e) {
            ivProfilePhoto.setVisibility(View.GONE);
            tvAvatarInitial.setVisibility(View.VISIBLE);
        }
    }

    private void updateName() {
        String newName = etNewName.getText().toString().trim();
        if (newName.isEmpty()) { etNewName.setError("Le nom ne peut pas être vide"); return; }
        if (newName.length() < 2) { etNewName.setError("Minimum 2 caractères"); return; }

        profileProgressBar.setVisibility(View.VISIBLE);
        String uid = mAuth.getCurrentUser().getUid();
        Map<String, Object> updates = new HashMap<>();
        updates.put("name", newName);

        db.collection("users").document(uid).update(updates)
                .addOnSuccessListener(unused -> {
                    profileProgressBar.setVisibility(View.GONE);
                    tvDisplayName.setText(newName);
                    if (ivProfilePhoto.getVisibility() != View.VISIBLE) {
                        tvAvatarInitial.setText(String.valueOf(newName.charAt(0)).toUpperCase());
                    }
                    etNewName.setText("");
                    Toast.makeText(this, "Nom mis à jour !", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    profileProgressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Erreur : " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private Bitmap resizeBitmap(Bitmap original, int maxSize) {
        int w = original.getWidth(), h = original.getHeight();
        float scale = Math.min((float) maxSize / w, (float) maxSize / h);
        return Bitmap.createScaledBitmap(original, Math.round(w * scale), Math.round(h * scale), true);
    }
}
