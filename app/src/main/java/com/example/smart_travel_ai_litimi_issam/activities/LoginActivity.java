package com.example.smart_travel_ai_litimi_issam.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.smart_travel_ai_litimi_issam.R;
import com.example.smart_travel_ai_litimi_issam.network.RetrofitClient;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;

import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private TextInputEditText etEmail, etPassword;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mAuth = FirebaseAuth.getInstance();

        if (mAuth.getCurrentUser() != null) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        etEmail    = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);

        Button btnLogin      = findViewById(R.id.btnLogin);
        Button btnGoRegister = findViewById(R.id.btnGoRegister);

        btnLogin.setOnClickListener(v -> loginUser());
        btnGoRegister.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class))
        );
    }

    private void loginUser() {
        String email    = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Remplis tous les champs", Toast.LENGTH_SHORT).show();
            return;
        }

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    // Sync avec le backend après login réussi
                    String uid  = mAuth.getCurrentUser().getUid();
                    String name = mAuth.getCurrentUser().getDisplayName();
                    syncWithBackend(uid, email, name);

                    Toast.makeText(this, "Connexion réussie", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(this, MainActivity.class));
                    finish();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Erreur : " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
    }

    private void syncWithBackend(String uid, String email, String name) {
        Map<String, String> body = new HashMap<>();
        body.put("firebaseUid", uid);
        body.put("email", email);
        body.put("name", name != null ? name : "Utilisateur");

        RetrofitClient.getApiService().syncUser(body)
                .enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call,
                                           Response<Map<String, Object>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            String token = (String) response.body().get("token");
                            getSharedPreferences("prefs", MODE_PRIVATE)
                                    .edit()
                                    .putString("jwt_token", token)
                                    .apply();
                            Log.d("Backend", "Sync réussi, token sauvegardé");
                        }
                    }

                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                        // Backend injoignable, app continue quand même
                        Log.w("Backend", "Sync échoué: " + t.getMessage());
                    }
                });
    }
}