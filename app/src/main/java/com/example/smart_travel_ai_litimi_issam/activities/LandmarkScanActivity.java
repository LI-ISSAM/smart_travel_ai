package com.example.smart_travel_ai_litimi_issam.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;

import com.example.smart_travel_ai_litimi_issam.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * ═══════════════════════════════════════════════════════════════════
 *  LandmarkScanActivity — "Scan & Découvrir"
 *
 *  Fonctionnalité : L'utilisateur prend une photo d'un monument,
 *  paysage, plat ou objet au Maroc. L'IA (via Ollama llava ou
 *  llama3.2-vision) analyse l'image et retourne :
 *    - Le nom du lieu / plat identifié
 *    - Une description touristique
 *    - Des conseils pratiques (horaires, prix, best time to visit)
 *    - Un bouton pour voir le lieu sur la carte (si applicable)
 *
 *  Pourquoi c'est utile pour une app de voyage :
 *    → Identifier un monument vu dans la rue sans chercher sur Google
 *    → Découvrir le nom d'un plat marocain dans un restaurant
 *    → Obtenir des infos instantanées sur ce qu'on voit devant soi
 * ═══════════════════════════════════════════════════════════════════
 */
public class LandmarkScanActivity extends AppCompatActivity {

    // ─── Config Ollama (même serveur que ChatActivity) ───────────────
    private static final String OLLAMA_BASE_URL = "http://192.168.11.109:11434";
    // Modèle avec vision. Si tu n'as pas llava, utilise "llama3.2:1b"
    // et on enverra juste le nom du fichier comme fallback.
    private static final String VISION_MODEL    = "moondream:latest";
    private static final String FALLBACK_MODEL  = "moondream:latest";

    private static final int CAMERA_PERMISSION_REQUEST = 3001;

    // ─── System prompt spécialisé voyage Maroc ──────────────────────
    private static final String SCAN_SYSTEM_PROMPT =
            "Tu es un guide touristique expert du plat maroc. " +
                    "Analyse l'image et reponds UNIQUEMENT en JSON valide, sans texte avant ou apres : "
            ;

    // ─── Views ────────────────────────────────────────────────────────
    private ImageView        ivPreview;
    private ImageButton      btnCamera;
    private ImageButton      btnGallery;
    private Button           btnAnalyze;
    private Button           btnSeeOnMap;
    private ProgressBar      progressBar;
    private LinearLayout     layoutPlaceholder;
    private LinearLayout     layoutResult;
    private MaterialCardView cardResult;
    private TextView         tvResultName;
    private TextView         tvResultCategory;
    private TextView         tvResultDescription;
    private TextView         tvResultTips;
    private TextView         tvConfidence;
    private ScrollView       scrollResult;

    // ─── State ────────────────────────────────────────────────────────
    private Bitmap   currentBitmap   = null;
    private Uri      photoUri        = null;   // URI pour la photo caméra
    private double   resultLat       = 0;
    private double   resultLng       = 0;
    private String   resultPlaceName = null;

    // ─── Launchers ────────────────────────────────────────────────────
    private final ActivityResultLauncher<Uri> cameraLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
                if (success && photoUri != null) {
                    loadBitmapFromUri(photoUri);
                }
            });

    private final ActivityResultLauncher<String> galleryLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) loadBitmapFromUri(uri);
            });

    // ─────────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_landmark_scan);

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        bindViews();
        setupClickListeners();
        setupBottomNav();

        // Bottom Navigation
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigationView);
        bottomNav.setSelectedItemId(R.id.nav_scan); // Explorer est sélectionné par défaut
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_explore) {
                startActivity(new Intent(this, MainActivity.class));
                overridePendingTransition(0, 0); // Transition sans animation

                return true;
            }
            else if(id == R.id.nav_chat){
                startActivity(new Intent(this, ChatActivity.class));
                overridePendingTransition(0, 0); // Transition sans animation
                return true;

            }
            else if(id == R.id.nav_avatar){
                startActivity(new Intent(this, AvatarGuideActivity.class));
                overridePendingTransition(0, 0); // Transition sans animation
                return true;
            }



            else if (id == R.id.nav_journal) {
                startActivity(new Intent(this, ProfileActivity.class));
                overridePendingTransition(0, 0); // Transition sans animation
                return true;
            }
            return false;
        });
    }

    // ─── Binding ─────────────────────────────────────────────────────
    private void bindViews() {
        ivPreview           = findViewById(R.id.ivScanPreview);
        btnCamera           = findViewById(R.id.btnCamera);
        btnGallery          = findViewById(R.id.btnGallery);
        btnAnalyze          = findViewById(R.id.btnAnalyze);
        btnSeeOnMap         = findViewById(R.id.btnSeeOnMap);
        progressBar         = findViewById(R.id.scanProgressBar);
        layoutPlaceholder   = findViewById(R.id.layoutPlaceholder);
        layoutResult        = findViewById(R.id.layoutResult);
        cardResult          = findViewById(R.id.cardResult);
        tvResultName        = findViewById(R.id.tvResultName);
        tvResultCategory    = findViewById(R.id.tvResultCategory);
        tvResultDescription = findViewById(R.id.tvResultDescription);
        tvResultTips        = findViewById(R.id.tvResultTips);
        tvConfidence        = findViewById(R.id.tvConfidence);
        scrollResult        = findViewById(R.id.scrollResult);
    }

    // ─── Click listeners ─────────────────────────────────────────────
    private void setupClickListeners() {

        // Bouton Appareil Photo
        btnCamera.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);
            } else {
                launchCamera();
            }
        });

        // Bouton Galerie
        btnGallery.setOnClickListener(v -> galleryLauncher.launch("image/*"));

        // Bouton Analyser
        btnAnalyze.setOnClickListener(v -> {
            if (currentBitmap == null) {
                Toast.makeText(this, "Prenez ou choisissez d'abord une photo", Toast.LENGTH_SHORT).show();
                return;
            }
            analyzeImage();
        });

        // Bouton Voir sur la carte
        btnSeeOnMap.setOnClickListener(v -> {
            if (resultLat != 0 && resultLng != 0) {
                Intent intent = new Intent(this, MainActivity.class);
                intent.putExtra("focusPlaceName", resultPlaceName);
                intent.putExtra("focusLat", resultLat);
                intent.putExtra("focusLng", resultLng);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                overridePendingTransition(0, 0);
            }
        });
    }

    // ─── Lancer la caméra ────────────────────────────────────────────
    private void launchCamera() {
        try {
            File photoFile = createImageFile();
            photoUri = FileProvider.getUriForFile(this,
                    getPackageName() + ".provider", photoFile);
            cameraLauncher.launch(photoUri);
        } catch (Exception e) {
            Toast.makeText(this, "Erreur caméra : " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private File createImageFile() throws Exception {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String fileName  = "SCAN_" + timeStamp + "_";
        File storageDir  = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        return File.createTempFile(fileName, ".jpg", storageDir);
    }

    // ─── Charger bitmap depuis URI ───────────────────────────────────
    private void loadBitmapFromUri(Uri uri) {
        new Thread(() -> {
            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
                Bitmap resized = resizeBitmap(bitmap, 512); // Limiter taille pour l'API

                runOnUiThread(() -> {
                    currentBitmap = resized;
                    ivPreview.setImageBitmap(resized);
                    ivPreview.setVisibility(View.VISIBLE);
                    layoutPlaceholder.setVisibility(View.GONE);
                    layoutResult.setVisibility(View.GONE);
                    btnAnalyze.setEnabled(true);
                    btnAnalyze.setAlpha(1.0f);
                    btnAnalyze.setText("Analyser cette photo");
                });
            } catch (Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(this, "Erreur lecture image", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    // ─── Analyse IA de l'image ───────────────────────────────────────
// Dans analyzeImage() — remplace la compression
    private void analyzeImage() {
        progressBar.setVisibility(View.VISIBLE);
        btnAnalyze.setEnabled(false);
        btnAnalyze.setText("Analyse en cours...");
        layoutResult.setVisibility(View.GONE);

        new Thread(() -> {
            try {
                // ✅ Réduire à 224px et qualité 60%
                Bitmap small = resizeBitmap(currentBitmap, 224);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                small.compress(Bitmap.CompressFormat.JPEG, 60, baos);
                String base64Image = Base64.encodeToString(
                        baos.toByteArray(), Base64.NO_WRAP);

                callOllamaVision(base64Image);
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    btnAnalyze.setEnabled(true);
                    btnAnalyze.setText("Analyser cette photo");
                    Toast.makeText(this, "Erreur : " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    // ─── Appel Ollama (llava vision model) ──────────────────────────
    private void callOllamaVision(String base64Image) {
        try {
            // Construction du body JSON pour Ollama /api/chat avec images
            JSONObject body = new JSONObject();
            body.put("model", VISION_MODEL);
            body.put("stream", false);

            // Message avec l'image en base64
            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            userMsg.put("content", "Identifie ce que tu vois sur cette photo. Réponds en JSON comme demandé.");

            JSONArray images = new JSONArray();
            images.put(base64Image);
            userMsg.put("images", images);

            // Système prompt
            JSONObject systemMsg = new JSONObject();
            systemMsg.put("role", "system");
            systemMsg.put("content", SCAN_SYSTEM_PROMPT);

            JSONArray messages = new JSONArray();
            messages.put(systemMsg);
            messages.put(userMsg);
            body.put("messages", messages);

            // Connexion HTTP
            URL url = new URL(OLLAMA_BASE_URL + "/api/chat");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(180000); // 3 minutes pour le modèle vision

            OutputStream os = conn.getOutputStream();
            os.write(body.toString().getBytes("UTF-8"));
            os.close();

            int responseCode = conn.getResponseCode();

            if (responseCode == 200) {
                BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();

                JSONObject response = new JSONObject(sb.toString());
                String rawText = response.getJSONObject("message").getString("content").trim();

                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    btnAnalyze.setEnabled(true);
                    btnAnalyze.setText("Analyser cette photo");
                    parseAndDisplayResult(rawText);
                });

            } else if (responseCode == 404) {
                // Modèle llava non disponible → fallback texte
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    btnAnalyze.setEnabled(true);
                    btnAnalyze.setText(" Analyser cette photo");
                    showModelNotAvailableError();
                });

            } else {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    btnAnalyze.setEnabled(true);
                    btnAnalyze.setText("Analyser cette photo");
                    Toast.makeText(this, "Erreur serveur : " + responseCode, Toast.LENGTH_SHORT).show();
                });
            }

        } catch (java.net.ConnectException e) {
            runOnUiThread(() -> {
                progressBar.setVisibility(View.GONE);
                btnAnalyze.setEnabled(true);
                btnAnalyze.setText("Analyser cette photo");
                showOllamaNotReachableError();
            });
        } catch (Exception e) {
            runOnUiThread(() -> {
                progressBar.setVisibility(View.GONE);
                btnAnalyze.setEnabled(true);
                btnAnalyze.setText("Analyser cette photo");
                Toast.makeText(this, "Erreur : " + e.getMessage(), Toast.LENGTH_LONG).show();
            });
        }
    }

    // ─── Parser et afficher le résultat IA ──────────────────────────
    private void parseAndDisplayResult(String rawText) {
        try {
            // Nettoyer le JSON si entouré de backticks
            String cleaned = rawText.replace("```json", "").replace("```", "").trim();
            int start = cleaned.indexOf("{");
            int end   = cleaned.lastIndexOf("}");
            if (start != -1 && end != -1) cleaned = cleaned.substring(start, end + 1);

            JSONObject json = new JSONObject(cleaned);

            String name        = json.optString("name",        "Lieu inconnu");
            String category    = json.optString("category",    "autre");
            String description = json.optString("description", "Aucune description disponible.");
            String tips        = json.optString("tips",        "");
            boolean hasLoc     = json.optBoolean("hasLocation", false);
            double  lat        = json.optDouble("lat", 0);
            double  lng        = json.optDouble("lng", 0);
            String confidence  = json.optString("confidence",  "low");

            // Mémoriser pour le bouton carte
            resultLat       = lat;
            resultLng       = lng;
            resultPlaceName = name;

            // Remplir les views
            tvResultName.setText(name);
            String cleanCategory = category.split("\\|")[0].trim();
            tvResultCategory.setText(getCategoryEmoji(cleanCategory) + " " + formatCategory(cleanCategory));            tvResultDescription.setText(description);

            if (!tips.isEmpty()) {
                tvResultTips.setText("💡 " + tips);
                tvResultTips.setVisibility(View.VISIBLE);
            } else {
                tvResultTips.setVisibility(View.GONE);
            }

            // Badge de confiance
            tvConfidence.setText(getConfidenceLabel(confidence));
            tvConfidence.setBackgroundColor(getConfidenceColor(confidence));

            // Bouton carte si localisation connue
            if (hasLoc && lat != 0 && lng != 0) {
                btnSeeOnMap.setVisibility(View.VISIBLE);
                btnSeeOnMap.setText(" Voir " + name + " sur la carte");
            } else {
                btnSeeOnMap.setVisibility(View.GONE);
            }

            layoutResult.setVisibility(View.VISIBLE);
            scrollResult.post(() -> scrollResult.smoothScrollTo(0, layoutResult.getTop()));

        } catch (Exception e) {
            // Fallback : afficher le texte brut
            tvResultName.setText("Résultat de l'analyse");
            tvResultCategory.setText("🤖 IA");
            tvResultDescription.setText(rawText);
            tvResultTips.setVisibility(View.GONE);
            tvConfidence.setText("Résultat brut");
            btnSeeOnMap.setVisibility(View.GONE);
            layoutResult.setVisibility(View.VISIBLE);
        }
    }

    // ─── Helpers affichage ───────────────────────────────────────────
    private String getCategoryEmoji(String cat) {
        switch (cat.toLowerCase()) {
            case "monument":  return "🏛️";
            case "plat":      return "🍽️";
            case "paysage":   return "🌄";
            case "marché":    return "🛍️";
            case "mosquée":   return "🕌";
            default:          return "📍";
        }
    }

    private String formatCategory(String cat) {
        if (cat == null || cat.isEmpty()) return "Autre";
        return cat.substring(0, 1).toUpperCase() + cat.substring(1);
    }

    private String getConfidenceLabel(String conf) {
        switch (conf.toLowerCase()) {
            case "high":   return "✅ Identification fiable";
            case "medium": return "⚠️ Identification probable";
            default:       return "❓ Identification incertaine";
        }
    }

    private int getConfidenceColor(String conf) {
        switch (conf.toLowerCase()) {
            case "high":   return 0xFF4CAF50; // vert
            case "medium": return 0xFFFF9800; // orange
            default:       return 0xFF9E9E9E; // gris
        }
    }

    private void showModelNotAvailableError() {
        tvResultName.setText("Modèle Vision non installé");
        tvResultCategory.setText("⚠️ Configuration requise");
        tvResultDescription.setText(
                "Le modèle 'llava:7b' n'est pas installé sur ton serveur Ollama.\n\n" +
                        "Pour l'installer, exécute dans le terminal :\n" +
                        "  ollama pull llava:7b\n\n" +
                        "Si tu as peu de VRAM, essaie :\n" +
                        "  ollama pull llava:7b-q4_0\n\n" +
                        "Une fois installé, relance l'analyse.");
        tvResultTips.setVisibility(View.GONE);
        tvConfidence.setText("Modèle manquant");
        tvConfidence.setBackgroundColor(0xFFE53935);
        btnSeeOnMap.setVisibility(View.GONE);
        layoutResult.setVisibility(View.VISIBLE);
    }

    private void showOllamaNotReachableError() {
        tvResultName.setText("Serveur Ollama inaccessible");
        tvResultCategory.setText("❌ Connexion échouée");
        tvResultDescription.setText(
                "Impossible de joindre Ollama.\n\n" +
                        "Vérifie :\n" +
                        "1. ollama serve est lancé sur ton PC\n" +
                        "2. OLLAMA_HOST=0.0.0.0 est défini\n" +
                        "3. L'IP dans le code est correcte (" + OLLAMA_BASE_URL + ")\n" +
                        "4. PC et téléphone sur le même WiFi");
        tvResultTips.setVisibility(View.GONE);
        tvConfidence.setText("Serveur hors ligne");
        tvConfidence.setBackgroundColor(0xFFE53935);
        btnSeeOnMap.setVisibility(View.GONE);
        layoutResult.setVisibility(View.VISIBLE);
    }

    // ─── Bottom Navigation ───────────────────────────────────────────
    private void setupBottomNav() {
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigationView);
        bottomNav.setSelectedItemId(R.id.nav_scan);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_explore) {
                startActivity(new Intent(this, MainActivity.class));
                overridePendingTransition(0, 0);
                finish();
                return true;
            } else if (id == R.id.nav_chat) {
                startActivity(new Intent(this, ChatActivity.class));
                overridePendingTransition(0, 0);
                finish();
                return true;
            } else if (id == R.id.nav_scan) {
                return true;
            } else if (id == R.id.nav_journal) {
                startActivity(new Intent(this, TravelJournalActivity.class));
                overridePendingTransition(0, 0);
                finish();
                return true;
            }
            return false;
        });
    }

    // ─── Permissions ─────────────────────────────────────────────────
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST &&
                grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            launchCamera();
        } else {
            Toast.makeText(this, "Permission caméra refusée", Toast.LENGTH_SHORT).show();
        }
    }

    // ─── Utilitaire resize ───────────────────────────────────────────
    private Bitmap resizeBitmap(Bitmap original, int maxSize) {
        int w = original.getWidth(), h = original.getHeight();
        float scale = Math.min((float) maxSize / w, (float) maxSize / h);
        if (scale >= 1) return original;
        return Bitmap.createScaledBitmap(original,
                Math.round(w * scale), Math.round(h * scale), true);
    }
}