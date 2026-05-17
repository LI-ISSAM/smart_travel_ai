package com.example.smart_travel_ai_litimi_issam.activities;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RatingBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;

import com.example.smart_travel_ai_litimi_issam.R;
import com.example.smart_travel_ai_litimi_issam.models.JournalEntryModel;
import com.example.smart_travel_ai_litimi_issam.network.RetrofitClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class TravelJournalActivity extends AppCompatActivity {

    private static final String TAG             = "TravelJournal";
    private static final String OLLAMA_BASE_URL = "http://192.168.11.109:11434";
    private static final String MODEL_NAME      = "llava:latest";

    private static final String ENTRY_PROMPT =
            "Tu es un ecrivain de voyage poetique. L'utilisateur vient de visiter un lieu. " +
                    "A partir du nom du lieu, de sa note (/5) et d'une note personnelle, " +
                    "genere une description narrative courte (3-4 phrases) en francais, " +
                    "style carnet de voyage personnel et authentique. " +
                    "Reponds UNIQUEMENT avec le texte narratif, rien d'autre.";

    private static final String RECAP_PROMPT =
            "Tu es un ecrivain de voyage. Voici la liste des lieux visites avec leurs notes et descriptions. " +
                    "Genere un recit de voyage complet et coherent en francais (8-12 phrases), " +
                    "comme si tu racontais le voyage a quelqu'un. " +
                    "Style : chaleureux, personnel, memorable. Commence directement le recit. " +
                    "Reponds UNIQUEMENT avec le recit, rien d'autre.";

    private FirebaseAuth mAuth;
    private String       jwtToken;
    private String       firebaseUid;

    private LinearLayout     layoutEntries;
    private LinearLayout     layoutJournalEmpty;
    private ProgressBar      progressBar;
    private ScrollView       scrollView;
    private Button           btnAddEntry;
    private Button           btnGenerateRecap;
    private TextView         tvRecapText;
    private MaterialCardView cardRecap;

    private final List<Map<String, Object>> journalEntries = new ArrayList<>();
    private Bitmap  pendingPhoto    = null;
    private String  pendingPhotoB64 = null;

    private ActivityResultLauncher<String> photoPicker;
    private ImageView dialogPhotoPreview;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_travel_journal);

        mAuth = FirebaseAuth.getInstance();

        if (mAuth.getCurrentUser() == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        firebaseUid = mAuth.getCurrentUser().getUid();
        jwtToken = getSharedPreferences("prefs", MODE_PRIVATE).getString("jwt_token", null);
        Log.d(TAG, "jwtToken: " + (jwtToken != null ? "present" : "null"));

        if (jwtToken == null) {
            reSyncAndLoad();
        } else {
            bindViews();
            setupBottomNav();
            setupPhotoPickerLauncher();
            loadJournalEntries();
            btnAddEntry.setOnClickListener(v -> showAddEntryDialog());
            btnGenerateRecap.setOnClickListener(v -> generateTripRecap());
        }

        ImageButton btnHeaderProfile = findViewById(R.id.btnHeaderProfile);
        if (btnHeaderProfile != null) {
            btnHeaderProfile.setOnClickListener(v -> {
                startActivity(new Intent(this, ProfileActivity.class));
                overridePendingTransition(0, 0);
            });
        }
    }

    private void reSyncAndLoad() {
        String email = mAuth.getCurrentUser().getEmail();
        String name  = mAuth.getCurrentUser().getDisplayName();
        Map<String, String> body = new HashMap<>();
        body.put("firebaseUid", firebaseUid);
        body.put("email", email != null ? email : "");
        body.put("name",  name  != null ? name  : "Utilisateur");
        RetrofitClient.getApiService().syncUser(body).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Object tokenObj = response.body().get("token");
                    if (tokenObj instanceof String) {
                        jwtToken = (String) tokenObj;
                        getSharedPreferences("prefs", MODE_PRIVATE).edit().putString("jwt_token", jwtToken).apply();
                    }
                }
                initUI();
            }
            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                Toast.makeText(TravelJournalActivity.this, "Backend indisponible", Toast.LENGTH_SHORT).show();
                initUI();
            }
        });
    }

    private void initUI() {
        bindViews();
        setupBottomNav();
        setupPhotoPickerLauncher();
        loadJournalEntries();
        btnAddEntry.setOnClickListener(v -> showAddEntryDialog());
        btnGenerateRecap.setOnClickListener(v -> generateTripRecap());
    }

    private void bindViews() {
        layoutEntries      = findViewById(R.id.layoutJournalEntries);
        layoutJournalEmpty = findViewById(R.id.layoutJournalEmpty);
        progressBar        = findViewById(R.id.journalProgressBar);
        scrollView         = findViewById(R.id.journalScrollView);
        btnAddEntry        = findViewById(R.id.btnAddJournalEntry);
        btnGenerateRecap   = findViewById(R.id.btnGenerateRecap);
        tvRecapText        = findViewById(R.id.tvRecapText);
        cardRecap          = findViewById(R.id.cardRecap);
    }

    private void setupPhotoPickerLauncher() {
        photoPicker = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null && dialogPhotoPreview != null) {
                new Thread(() -> {
                    try {
                        Bitmap bmp = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
                        Bitmap resized = resizeBitmap(bmp, 400);
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        resized.compress(Bitmap.CompressFormat.JPEG, 75, baos);
                        // NO_WRAP = pas de \n dans le Base64
                        pendingPhotoB64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
                        pendingPhoto    = resized;
                        runOnUiThread(() -> {
                            dialogPhotoPreview.setImageBitmap(resized);
                            dialogPhotoPreview.setVisibility(View.VISIBLE);
                        });
                    } catch (Exception e) {
                        runOnUiThread(() -> Toast.makeText(this, "Erreur chargement photo", Toast.LENGTH_SHORT).show());
                    }
                }).start();
            }
        });
    }

    private void showAddEntryDialog() {
        pendingPhoto    = null;
        pendingPhotoB64 = null;
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_journal_entry, null);
        EditText  etPlaceName  = dialogView.findViewById(R.id.etJournalPlaceName);
        EditText  etNote       = dialogView.findViewById(R.id.etJournalNote);
        RatingBar ratingBar    = dialogView.findViewById(R.id.journalRatingBar);
        ImageView ivPhoto      = dialogView.findViewById(R.id.ivJournalEntryPhoto);
        Button    btnPickPhoto = dialogView.findViewById(R.id.btnPickJournalPhoto);
        dialogPhotoPreview = ivPhoto;
        btnPickPhoto.setOnClickListener(v -> photoPicker.launch("image/*"));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Nouvelle entree")
                .setView(dialogView)
                .setPositiveButton("Ajouter", (dialogInterface, which) -> {
                    String placeName = etPlaceName.getText().toString().trim();
                    String noteText  = etNote.getText().toString().trim();
                    float rating     = ratingBar.getRating();

                    if (placeName.isEmpty()) {
                        Toast.makeText(this, "Le nom du lieu est requis", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    createJournalEntry(placeName, noteText, rating, pendingPhotoB64);
                })
                .setNegativeButton("Annuler", null)
                .show();

// 🔥 changer couleur des boutons
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.WHITE);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.WHITE);
        int titleId = getResources().getIdentifier("alertTitle", "id", "android");
        TextView title = dialog.findViewById(titleId);
        if (title != null) {
            title.setTextColor(Color.WHITE);
        }

    }

    private void createJournalEntry(String placeName, String noteText, float rating, String photoB64) {
        progressBar.setVisibility(View.VISIBLE);
        new Thread(() -> {
            String narrative = generateNarrative(placeName, noteText, rating);
            String date      = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(new Date());
            long   timestamp = System.currentTimeMillis();
            Map<String, Object> body = new HashMap<>();
            body.put("placeName",  placeName);
            body.put("noteText",   noteText);
            body.put("rating",     (double) rating);
            body.put("narrative",  narrative);
            body.put("date",       date);
            body.put("timestamp",  timestamp);
            if (photoB64 != null) body.put("photoBase64", photoB64);

            if (jwtToken != null) {
                RetrofitClient.getApiService().saveJournalEntry("Bearer " + jwtToken, body)
                        .enqueue(new Callback<Map<String, Object>>() {
                            @Override
                            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                                if (response.isSuccessful() && response.body() != null) {
                                    String mongoId = (String) response.body().get("id");
                                    body.put("id", mongoId != null ? mongoId : "local");
                                } else {
                                    body.put("id", "local_" + timestamp);
                                }
                                addEntryToUI(body);
                            }
                            @Override
                            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                                body.put("id", "local_" + timestamp);
                                addEntryToUI(body);
                            }
                        });
            } else {
                body.put("id", "local_" + timestamp);
                addEntryToUI(body);
            }
        }).start();
    }

    private void addEntryToUI(Map<String, Object> entry) {
        runOnUiThread(() -> {
            progressBar.setVisibility(View.GONE);
            journalEntries.add(0, entry);
            addEntryCard(entry, true);
            updateEmptyState();
            Toast.makeText(this, "Entree ajoutee !", Toast.LENGTH_SHORT).show();
        });
    }

    // *** CORRECTION PRINCIPALE : JournalEntryModel preserve photoBase64 ***
    private void loadJournalEntries() {
        progressBar.setVisibility(View.VISIBLE);
        if (jwtToken == null) {
            progressBar.setVisibility(View.GONE);
            updateEmptyState();
            return;
        }
        RetrofitClient.getApiService().getJournalEntries("Bearer " + jwtToken)
                .enqueue(new Callback<List<JournalEntryModel>>() {
                    @Override
                    public void onResponse(Call<List<JournalEntryModel>> call, Response<List<JournalEntryModel>> response) {
                        progressBar.setVisibility(View.GONE);
                        journalEntries.clear();
                        layoutEntries.removeAllViews();
                        if (response.isSuccessful() && response.body() != null) {
                            Log.d(TAG, response.body().size() + " entrees chargees");
                            for (JournalEntryModel model : response.body()) {
                                Map<String, Object> entry = new HashMap<>();
                                entry.put("id",          model.id);
                                entry.put("placeName",   model.placeName);
                                entry.put("noteText",    model.noteText);
                                entry.put("rating",      model.rating);
                                entry.put("narrative",   model.narrative);
                                entry.put("date",        model.date);
                                entry.put("timestamp",   model.timestamp);
                                entry.put("photoBase64", model.photoBase64); // preserve correctement
                                journalEntries.add(entry);
                                addEntryCard(entry, false);
                            }
                        } else {
                            Log.e(TAG, "Erreur HTTP " + response.code());
                        }
                        updateEmptyState();
                    }
                    @Override
                    public void onFailure(Call<List<JournalEntryModel>> call, Throwable t) {
                        progressBar.setVisibility(View.GONE);
                        Log.e(TAG, "Chargement echoue : " + t.getMessage());
                        updateEmptyState();
                        Toast.makeText(TravelJournalActivity.this, "Backend injoignable", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void addEntryCard(Map<String, Object> entry, boolean prepend) {
        View card = LayoutInflater.from(this).inflate(R.layout.item_journal_entry, layoutEntries, false);
        TextView    tvDate      = card.findViewById(R.id.tvEntryDate);
        TextView    tvPlace     = card.findViewById(R.id.tvEntryPlace);
        TextView    tvRating    = card.findViewById(R.id.tvEntryRating);
        TextView    tvNarrative = card.findViewById(R.id.tvEntryNarrative);
        ImageView   ivPhoto     = card.findViewById(R.id.ivEntryPhoto);
        ImageButton btnDelete   = card.findViewById(R.id.btnDeleteEntry);

        tvDate.setText(getStr(entry, "date", ""));
        tvPlace.setText(getStr(entry, "placeName", ""));
        Object ratingObj = entry.get("rating");
        float rating = ratingObj instanceof Number ? ((Number) ratingObj).floatValue() : 0f;
        tvRating.setText(getStarsString(rating) + " " + String.format(Locale.getDefault(), "%.1f", rating) + "/5");
        tvNarrative.setText(getStr(entry, "narrative", ""));

        // Vérifier si une photo existe
        Object hasPhotoObj = entry.get("hasPhoto");
        boolean hasPhoto = hasPhotoObj instanceof Boolean && (Boolean) hasPhotoObj;

// Vérifier aussi si on a déjà la photo en mémoire (entrée qu'on vient d'ajouter)
        Object photoObj = entry.get("photoBase64");
        String photoB64 = (photoObj instanceof String) ? (String) photoObj : null;

        if (photoB64 != null && !photoB64.isEmpty()) {
            // ✅ Photo déjà en mémoire (entrée fraîchement ajoutée)
            final String finalPhoto = photoB64;
            new Thread(() -> {
                try {
                    byte[] bytes = Base64.decode(
                            finalPhoto.replaceAll("\\s", ""), Base64.DEFAULT);
                    Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    if (bmp != null) {
                        runOnUiThread(() -> {
                            ivPhoto.setImageBitmap(bmp);
                            ivPhoto.setVisibility(View.VISIBLE);
                        });
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Erreur photo mémoire: " + e.getMessage());
                }
            }).start();

        } else if (hasPhoto) {
            // ✅ Photo existe en DB → la charger depuis le backend
            String entryId = getStr(entry, "id", null);
            if (entryId != null && jwtToken != null) {
                ivPhoto.setVisibility(View.VISIBLE);
                loadPhotoFromBackend(entryId, ivPhoto);
            }
        } else {
            ivPhoto.setVisibility(View.GONE);
        }

        btnDelete.setOnClickListener(v -> {
            String entryId = getStr(entry, "id", null);
            if (entryId == null) return;
            new AlertDialog.Builder(this)
                    .setTitle("Supprimer cette entree ?")
                    .setPositiveButton("Oui", (d, w) -> {
                        if (jwtToken != null && !entryId.startsWith("local_")) {
                            RetrofitClient.getApiService().deleteJournalEntry("Bearer " + jwtToken, entryId)
                                    .enqueue(new Callback<Map<String, Object>>() {
                                        @Override public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {}
                                        @Override public void onFailure(Call<Map<String, Object>> call, Throwable t) {}
                                    });
                        }
                        journalEntries.remove(entry);
                        layoutEntries.removeView(card);
                        updateEmptyState();
                        Toast.makeText(this, "Entree supprimee", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Non", null).show();
        });

        if (prepend) layoutEntries.addView(card, 0);
        else         layoutEntries.addView(card);
    }
    private void loadPhotoFromBackend(String entryId, ImageView ivPhoto) {
        new Thread(() -> {
            try {
                RetrofitClient.getApiService()
                        .getJournalEntry("Bearer " + jwtToken, entryId)
                        .enqueue(new Callback<Map<String, Object>>() {
                            @Override
                            public void onResponse(Call<Map<String, Object>> call,
                                                   Response<Map<String, Object>> response) {
                                if (response.isSuccessful() && response.body() != null) {
                                    Object photoObj = response.body().get("photoBase64");
                                    if (photoObj instanceof String) {
                                        String b64 = ((String) photoObj).replaceAll("\\s", "");
                                        new Thread(() -> {
                                            try {
                                                byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
                                                Bitmap bmp = BitmapFactory.decodeByteArray(
                                                        bytes, 0, bytes.length);
                                                if (bmp != null) {
                                                    runOnUiThread(() -> {
                                                        ivPhoto.setImageBitmap(bmp);
                                                        ivPhoto.setVisibility(View.VISIBLE);
                                                    });
                                                }
                                            } catch (Exception e) {
                                                Log.e(TAG, "Erreur décodage: " + e.getMessage());
                                            }
                                        }).start();
                                    }
                                }
                            }
                            @Override
                            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                                Log.e(TAG, "Echec chargement photo: " + t.getMessage());
                            }
                        });
            } catch (Exception e) {
                Log.e(TAG, "Erreur loadPhoto: " + e.getMessage());
            }
        }).start();
    }
    private String generateNarrative(String placeName, String noteText, float rating) {
        try {
            String userContent = "Lieu visite : " + placeName + "\nNote : " + rating + "/5\nMon ressenti : " +
                    (noteText.isEmpty() ? "Magnifique experience." : noteText);
            JSONObject systemMsg = new JSONObject();
            systemMsg.put("role", "system");
            systemMsg.put("content", ENTRY_PROMPT);
            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            userMsg.put("content", userContent);
            JSONArray messages = new JSONArray();
            messages.put(systemMsg);
            messages.put(userMsg);
            JSONObject body = new JSONObject();
            body.put("model", MODEL_NAME);
            body.put("stream", false);
            body.put("messages", messages);
            URL url = new URL(OLLAMA_BASE_URL + "/api/chat");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(60000);
            OutputStream os = conn.getOutputStream();
            os.write(body.toString().getBytes("UTF-8"));
            os.close();
            if (conn.getResponseCode() == 200) {
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                return new JSONObject(sb.toString()).getJSONObject("message").getString("content").trim();
            }
        } catch (Exception e) {
            Log.w(TAG, "Ollama indisponible : " + e.getMessage());
        }
        return "Une visite inoubliable a " + placeName + ", notee " + rating + "/5. " +
                (noteText.isEmpty() ? "Un moment qui restera grave dans la memoire." : noteText);
    }

    private void generateTripRecap() {
        if (journalEntries.isEmpty()) {
            Toast.makeText(this, "Ajoutez d'abord des lieux", Toast.LENGTH_SHORT).show();
            return;
        }
        progressBar.setVisibility(View.VISIBLE);
        btnGenerateRecap.setEnabled(false);
        cardRecap.setVisibility(View.GONE);
        new Thread(() -> {
            try {
                StringBuilder summary = new StringBuilder("Voici mon voyage :\n\n");
                for (Map<String, Object> entry : journalEntries) {
                    summary.append("- ").append(entry.get("date")).append(" : ")
                            .append(entry.get("placeName"))
                            .append(" (").append(entry.get("rating")).append("/5) - ")
                            .append(entry.get("narrative")).append("\n\n");
                }
                JSONObject systemMsg = new JSONObject();
                systemMsg.put("role", "system");
                systemMsg.put("content", RECAP_PROMPT);
                JSONObject userMsg = new JSONObject();
                userMsg.put("role", "user");
                userMsg.put("content", summary.toString());
                JSONArray messages = new JSONArray();
                messages.put(systemMsg);
                messages.put(userMsg);
                JSONObject body = new JSONObject();
                body.put("model", MODEL_NAME);
                body.put("stream", false);
                body.put("messages", messages);
                URL url = new URL(OLLAMA_BASE_URL + "/api/chat");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(120000);
                OutputStream os = conn.getOutputStream();
                os.write(body.toString().getBytes("UTF-8"));
                os.close();
                if (conn.getResponseCode() == 200) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    br.close();
                    String recap = new JSONObject(sb.toString()).getJSONObject("message").getString("content").trim();
                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        btnGenerateRecap.setEnabled(true);
                        tvRecapText.setText(recap);
                        cardRecap.setVisibility(View.VISIBLE);
                        scrollView.post(() -> scrollView.smoothScrollTo(0, cardRecap.getTop()));
                    });
                } else {
                    runOnUiThread(() -> { progressBar.setVisibility(View.GONE); btnGenerateRecap.setEnabled(true); });
                }
            } catch (Exception e) {
                runOnUiThread(() -> { progressBar.setVisibility(View.GONE); btnGenerateRecap.setEnabled(true);
                    Toast.makeText(this, "Ollama inaccessible", Toast.LENGTH_LONG).show(); });
            }
        }).start();
    }

    private void updateEmptyState() {
        boolean empty = journalEntries.isEmpty();
        layoutJournalEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        btnGenerateRecap.setEnabled(!empty);
        btnGenerateRecap.setAlpha(empty ? 0.4f : 1.0f);
    }

    private String getStr(Map<String, Object> map, String key, String def) {
        Object val = map.get(key);
        return val instanceof String ? (String) val : def;
    }

    private String getStarsString(float rating) {
        int stars = Math.round(rating);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; i++) sb.append(i < stars ? "⭐" : "☆");
        return sb.toString();
    }

    private Bitmap resizeBitmap(Bitmap original, int maxSize) {
        int w = original.getWidth(), h = original.getHeight();
        float scale = Math.min((float) maxSize / w, (float) maxSize / h);
        if (scale >= 1) return original;
        return Bitmap.createScaledBitmap(original, Math.round(w * scale), Math.round(h * scale), true);
    }

    private void setupBottomNav() {
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigationView);
        bottomNav.setSelectedItemId(R.id.nav_journal);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_explore) {
                startActivity(new Intent(this, MainActivity.class));
                overridePendingTransition(0, 0); finish(); return true;
            } else if (id == R.id.nav_chat) {
                startActivity(new Intent(this, ChatActivity.class));
                overridePendingTransition(0, 0); finish(); return true;
            } else if (id == R.id.nav_scan) {
                startActivity(new Intent(this, LandmarkScanActivity.class));
                overridePendingTransition(0, 0); finish(); return true;
            } else if (id == R.id.nav_avatar) {
                startActivity(new Intent(this, AvatarGuideActivity.class));
                overridePendingTransition(0, 0); finish(); return true;
            } else if (id == R.id.nav_journal) {
                return true;
            }
            return false;
        });
    }
}