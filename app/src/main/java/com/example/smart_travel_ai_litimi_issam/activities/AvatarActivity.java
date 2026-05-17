package com.example.smart_travel_ai_litimi_issam.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.smart_travel_ai_litimi_issam.R;
import com.example.smart_travel_ai_litimi_issam.network.DIdApiService;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;

public class AvatarActivity extends AppCompatActivity {

    private static final String AVATAR_IMAGE_URL = "https://res.cloudinary.com/dpfcduqgl/image/upload/v1778179598/issam_lwmuck.png";
    private static final String VOICE_NAME = "fr-FR-DeniseNeural";
    private static final int MIC_PERMISSION_CODE = 3001;

    private VideoView    videoAvatar;
    private ImageView    ivAvatarStatic;
    private ProgressBar  progressAvatar;
    private TextView     tvStatus, tvAvatarSpeech;
    private TextInputEditText etAvatarText;
    private ImageButton  btnAvatarMic;

    private SpeechRecognizer speechRecognizer;
    private boolean isListening = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_avatar);

        // Init vues
        videoAvatar     = findViewById(R.id.videoAvatar);
        ivAvatarStatic  = findViewById(R.id.ivAvatarStatic);
        progressAvatar  = findViewById(R.id.progressAvatar);
        tvStatus        = findViewById(R.id.tvStatus);
        tvAvatarSpeech  = findViewById(R.id.tvAvatarSpeech);
        etAvatarText    = findViewById(R.id.etAvatarText);
        btnAvatarMic    = findViewById(R.id.btnAvatarMic);

        // Bouton parler
        findViewById(R.id.btnSpeak).setOnClickListener(v -> {
            String text = etAvatarText.getText().toString().trim();
            if (text.isEmpty()) {
                Toast.makeText(this, "Écris ce que l'avatar doit dire", Toast.LENGTH_SHORT).show();
                return;
            }
            generateAvatarVideo(text);
        });

        // Bouton micro
        btnAvatarMic.setOnClickListener(v -> {
            if (isListening) stopListening();
            else checkMicAndStart();
        });

        // Bottom Navigation
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigationView);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_explore) {
                startActivity(new Intent(this, MainActivity.class));
                overridePendingTransition(0, 0);
                finish();
            } else if (id == R.id.nav_chat) {
                startActivity(new Intent(this, ChatActivity.class));
                overridePendingTransition(0, 0);
                finish();
            } else if (id == R.id.nav_journal) {
                startActivity(new Intent(this, ProfileActivity.class));
                overridePendingTransition(0, 0);
                finish();
            }
            return true;
        });
    }

    // ─────────────────────────────────────────
    //  GÉNÉRER LA VIDÉO AVATAR
    // ─────────────────────────────────────────

    private void generateAvatarVideo(String text) {
        setStatus("⏳ Génération en cours...", true);
        tvAvatarSpeech.setText(text);
        ivAvatarStatic.setVisibility(View.VISIBLE);
        videoAvatar.setVisibility(View.GONE);

        new Thread(() -> {
            try {
                // Étape 1 : Créer la vidéo
                String talkId = DIdApiService.createTalkVideo(
                        AVATAR_IMAGE_URL, text, VOICE_NAME);

                // Étape 2 : Polling jusqu'à ce que la vidéo soit prête
                String videoUrl = null;
                int attempts = 0;
                while (videoUrl == null && attempts < 30) {
                    Thread.sleep(2000); // Attendre 2 secondes entre chaque vérif
                    videoUrl = DIdApiService.getTalkVideoUrl(talkId);
                    attempts++;
                }

                if (videoUrl != null) {
                    final String finalUrl = videoUrl;
                    runOnUiThread(() -> playAvatarVideo(finalUrl));
                } else {
                    runOnUiThread(() -> setStatus("❌ Timeout - Réessaie", false));
                }

            } catch (Exception e) {
                runOnUiThread(() -> {
                    setStatus("❌ Erreur: " + e.getMessage(), false);
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void playAvatarVideo(String videoUrl) {
        setStatus("▶ Lecture...", false);

        ivAvatarStatic.setVisibility(View.GONE);
        videoAvatar.setVisibility(View.VISIBLE);

        videoAvatar.setVideoURI(Uri.parse(videoUrl));
        videoAvatar.setOnPreparedListener(mp -> {
            mp.setLooping(false);
            videoAvatar.start();
        });
        videoAvatar.setOnCompletionListener(mp -> {
            // Revenir à la photo statique après la vidéo
            videoAvatar.setVisibility(View.GONE);
            ivAvatarStatic.setVisibility(View.VISIBLE);
            setStatus("Prêt", false);
        });
        videoAvatar.setOnErrorListener((mp, what, extra) -> {
            setStatus("❌ Erreur lecture vidéo", false);
            return true;
        });
    }

    private void setStatus(String status, boolean loading) {
        tvStatus.setText(status);
        progressAvatar.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    // ─────────────────────────────────────────
    //  MICRO → TEXTE
    // ─────────────────────────────────────────

    private void checkMicAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, MIC_PERMISSION_CODE);
        } else {
            startListening();
        }
    }

    private void startListening() {
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    etAvatarText.setText(matches.get(0));
                    stopListening();
                    // Lancer automatiquement la génération
                    generateAvatarVideo(matches.get(0));
                }
            }

            @Override
            public void onReadyForSpeech(Bundle b) {
                isListening = true;
                btnAvatarMic.setBackgroundResource(R.drawable.circle_mic_active_bg);
                setStatus("🎤 Parlez...", false);
            }

            @Override
            public void onError(int error) {
                stopListening();
                Toast.makeText(AvatarActivity.this,
                        "Erreur micro (" + error + ")", Toast.LENGTH_SHORT).show();
            }

            @Override public void onBeginningOfSpeech() {}
            @Override public void onEndOfSpeech() {}
            @Override public void onRmsChanged(float v) {}
            @Override public void onBufferReceived(byte[] b) {}
            @Override public void onPartialResults(Bundle b) {}
            @Override public void onEvent(int i, Bundle b) {}
        });

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR");
        speechRecognizer.startListening(intent);
    }

    private void stopListening() {
        isListening = false;
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
        btnAvatarMic.setBackgroundResource(R.drawable.circle_mic_bg);
        setStatus("Prêt", false);
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] grants) {
        super.onRequestPermissionsResult(req, perms, grants);
        if (req == MIC_PERMISSION_CODE && grants.length > 0
                && grants[0] == PackageManager.PERMISSION_GRANTED) {
            startListening();
        }
    }

    @Override
    protected void onDestroy() {
        if (speechRecognizer != null) speechRecognizer.destroy();
        super.onDestroy();
    }
}