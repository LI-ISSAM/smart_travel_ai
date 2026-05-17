package com.example.smart_travel_ai_litimi_issam.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import android.view.View;
import android.view.inputmethod.EditorInfo;
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

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Locale;

public class AvatarGuideActivity extends AppCompatActivity {

    private static final String AVATAR_IMAGE_URL = "https://res.cloudinary.com/dpfcduqgl/image/upload/v1778179598/issam_lwmuck.png";
    private static final String VOICE_NAME = "fr-FR-HenriNeural";
    private static final String OLLAMA_URL        = "http://192.168.11.109:11434/api/chat";
    private static final String MODEL_NAME        = "llama3.2:1b";
    private static final int    MIC_PERM          = 4001;

    // Contexte de l'app pour que l'IA guide répond correctement
    // APRÈS
    private static final String GUIDE_SYSTEM_PROMPT =
            "Tu es Issam, un guide touristique virtuel expert de l'application Smart Travel AI et du tourisme au Maroc. " +
                    "Tu aides les utilisateurs à naviguer dans l'application et à découvrir le Maroc. " +
                    "L'application contient : Explorer (carte des lieux), Chat IA (assistant voyage), " +
                    "Scanner (identifier monuments et plats en photo), Guide IA (toi-même), Journal (carnet de voyage). " +
                    "Réponds toujours en français, de façon courte et claire (2-3 phrases max). " +
                    "Sois chaleureux, professionnel et enthousiaste pour le voyage au Maroc.";

    private VideoView         videoGuideAvatar;
    private ImageView         ivGuideStatic;
    private ProgressBar       progressGuide;
    private TextView          tvGuideStatus, tvAvatarSpeech;
    private TextInputEditText etGuideQuestion;
    private ImageButton       btnGuideMicInput;
    private SpeechRecognizer  speechRecognizer;
    // TTS supprimé
    private boolean           isListening = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_avatar_guide);
        // Bottom Navigation
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigationView);
        bottomNav.setSelectedItemId(R.id.nav_avatar); // Explorer est sélectionné par défaut
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_explore) {
                startActivity(new Intent(this, MainActivity.class));
                overridePendingTransition(0, 0); // Transition sans animation                return true;
            }
            else if(id == R.id.nav_chat){
                startActivity(new Intent(this, ChatActivity.class));
                overridePendingTransition(0, 0); // Transition sans animation
                return true;

            }


            else if (id==R.id.nav_scan){
                startActivity(new Intent(this,LandmarkScanActivity.class));
                overridePendingTransition(0, 0); // Transition sans animation
                return true;
            }

            else if (id == R.id.nav_journal) {
                startActivity(new Intent(this, TravelJournalActivity.class));
                overridePendingTransition(0, 0); // Transition sans animation
                return true;
            }
            return false;
        });



        initViews();
        setupGuideButtons();
        setupInputZone();

        // Message d'accueil automatique au démarrage
        speakAndAnimate("Bonjour ! Je suis Issam, votre guide Smart Travel AI. " +
                "Je peux vous expliquer l'application ou répondre à vos questions !");

    }

    private void initViews() {
        videoGuideAvatar = findViewById(R.id.videoGuideAvatar);
        ivGuideStatic    = findViewById(R.id.ivGuideStatic);
        progressGuide    = findViewById(R.id.progressGuide);
        tvGuideStatus    = findViewById(R.id.tvGuideStatus);
        tvAvatarSpeech   = findViewById(R.id.tvAvatarSpeech);
        etGuideQuestion  = findViewById(R.id.etGuideQuestion);
        btnGuideMicInput = findViewById(R.id.btnGuideMicInput);

        // Bouton retour
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }

    // ─────────────────────────────────────────
    //  BOUTONS GUIDE RAPIDE
    // ─────────────────────────────────────────

    private void setupGuideButtons() {
        findViewById(R.id.btnGuideExplore).setOnClickListener(v ->
                askGuide("Comment explorer les lieux sur la carte ?"));

        findViewById(R.id.btnGuideChat).setOnClickListener(v ->
                askGuide("Comment utiliser le chat IA ?"));

        findViewById(R.id.btnGuideMic).setOnClickListener(v ->
                askGuide("Comment utiliser le microphone dans l'application ?"));

        findViewById(R.id.btnGuideProfile).setOnClickListener(v ->
                askGuide("Comment gérer et modifier mon profil utilisateur ?"));
    }

    // ─────────────────────────────────────────
    //  ZONE QUESTION LIBRE
    // ─────────────────────────────────────────

    private void setupInputZone() {
        // Envoyer avec bouton
        findViewById(R.id.btnGuideSend).setOnClickListener(v -> sendQuestion());

        // Envoyer avec clavier
        etGuideQuestion.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendQuestion();
                return true;
            }
            return false;
        });

        // Micro
        btnGuideMicInput.setOnClickListener(v -> {
            if (isListening) stopListening();
            else checkMicAndStart();
        });
    }

    private void sendQuestion() {
        String q = etGuideQuestion.getText().toString().trim();
        if (q.isEmpty()) return;
        etGuideQuestion.setText("");
        askGuide(q);
    }

    // ─────────────────────────────────────────
    //  APPEL IA (OLLAMA) + AVATAR
    // ─────────────────────────────────────────

    private void askGuide(String question) {
        setStatus("Réflexion...");
        tvAvatarSpeech.setText("...");

        new Thread(() -> {
            try {
                // Appel Ollama
                JSONObject userMsg = new JSONObject();
                userMsg.put("role", "user");
                userMsg.put("content", question);

                JSONObject systemMsg = new JSONObject();
                systemMsg.put("role", "system");
                systemMsg.put("content", GUIDE_SYSTEM_PROMPT);

                JSONArray messages = new JSONArray();
                messages.put(systemMsg);
                messages.put(userMsg);

                JSONObject body = new JSONObject();
                body.put("model", MODEL_NAME);
                body.put("stream", false);
                body.put("messages", messages);

                URL url = new URL(OLLAMA_URL);
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
                    BufferedReader br = new BufferedReader(
                            new InputStreamReader(conn.getInputStream(), "UTF-8"));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    br.close();

                    JSONObject resp = new JSONObject(sb.toString());
                    String answer = resp.getJSONObject("message")
                            .getString("content").trim();

                    runOnUiThread(() -> speakAndAnimate(answer));
                } else {
                    runOnUiThread(() -> {
                        setStatus(" Erreur IA");
                        tvAvatarSpeech.setText("Je ne peux pas répondre pour l'instant.");
                    });
                }

            } catch (Exception e) {
                runOnUiThread(() -> {
                    // Si Ollama indisponible → répondre avec D-ID seulement
                    String fallback = getFallbackAnswer(question);
                    speakAndAnimate(fallback);
                });
            }
        }).start();
    }

    // Réponses de secours si Ollama est down
    private String getFallbackAnswer(String question) {
        String q = question.toLowerCase();
        if (q.contains("explore") || q.contains("carte") || q.contains("lieu"))
            return "Pour explorer, allez sur l'onglet Explorer. Vous verrez une carte avec des lieux proches et une liste en dessous !";
        if (q.contains("chat") || q.contains("ia") || q.contains("intelligence"))
            return "Le Chat IA se trouve dans l'onglet Chat IA. Posez vos questions sur les voyages et l'IA vous répond !";
        if (q.contains("micro") || q.contains("voix") || q.contains("parler"))
            return "Appuyez sur le bouton microphone dans le Chat IA pour parler directement à l'assistant !";
        if (q.contains("profil") || q.contains("compte") || q.contains("photo"))
            return "Dans l'onglet Profil, vous pouvez modifier votre nom et changer votre photo de profil !";
        return "Je suis votre guide Smart Travel AI ! Explorez des lieux, chattez avec l'IA et utilisez votre voix pour interagir.";
    }

    // ─────────────────────────────────────────
    //  PARLER + ANIMER L'AVATAR (D-ID uniquement)
    // ─────────────────────────────────────────

    private void speakAndAnimate(String text) {
        tvAvatarSpeech.setText(text);
        setStatus("Génération de la vidéo...");

        // Générer la vidéo D-ID avec voix uniquement
        generateDIdVideo(text);
    }

    private void generateDIdVideo(String text) {
        progressGuide.setVisibility(View.VISIBLE);

        new Thread(() -> {
            try {
                String talkId = DIdApiService.createTalkVideo(
                        AVATAR_IMAGE_URL, text, VOICE_NAME);

                String videoUrl = null;
                int attempts = 0;
                while (videoUrl == null && attempts < 20) {
                    Thread.sleep(2000);
                    videoUrl = DIdApiService.getTalkVideoUrl(talkId);
                    attempts++;
                }

                if (videoUrl != null) {
                    final String finalUrl = videoUrl;
                    runOnUiThread(() -> playVideo(finalUrl));
                } else {
                    runOnUiThread(() -> {
                        progressGuide.setVisibility(View.GONE);
                        setStatus("Votre assistant personnel");
                        // Pas de TTS de secours - seulement le texte affiché
                        tvAvatarSpeech.setText(text + " (vidéo non disponible)");
                    });
                }
            } catch (Exception e) {
                // D-ID indisponible - pas de TTS de secours
                runOnUiThread(() -> {
                    progressGuide.setVisibility(View.GONE);
                    setStatus("Votre assistant personnel");
                    tvAvatarSpeech.setText(text + " (vidéo non disponible)");
                });
            }
        }).start();
    }

    private void playVideo(String videoUrl) {
        progressGuide.setVisibility(View.GONE);
        ivGuideStatic.setVisibility(View.GONE);
        videoGuideAvatar.setVisibility(View.VISIBLE);

        videoGuideAvatar.setVideoURI(Uri.parse(videoUrl));
        videoGuideAvatar.setOnPreparedListener(mp -> videoGuideAvatar.start());
        videoGuideAvatar.setOnCompletionListener(mp -> {
            videoGuideAvatar.setVisibility(View.GONE);
            ivGuideStatic.setVisibility(View.VISIBLE);
            setStatus("Votre assistant personnel");
        });

        // Gestion des erreurs de lecture vidéo
        videoGuideAvatar.setOnErrorListener((mp, what, extra) -> {
            videoGuideAvatar.setVisibility(View.GONE);
            ivGuideStatic.setVisibility(View.VISIBLE);
            setStatus("Erreur de lecture vidéo");
            return true;
        });
    }

    // ─────────────────────────────────────────
    //  MICRO (inchangé)
    // ─────────────────────────────────────────

    private void checkMicAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, MIC_PERM);
        } else {
            startListening();
        }
    }

    private void startListening() {
        if (speechRecognizer != null) speechRecognizer.destroy();

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle p) {
                isListening = true;
                btnGuideMicInput.setBackgroundResource(R.drawable.circle_mic_active_bg);
                setStatus("🎤 Je vous écoute...");
            }
            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    stopListening();
                    askGuide(matches.get(0));
                }
            }
            @Override
            public void onError(int error) {
                stopListening();
                Toast.makeText(AvatarGuideActivity.this,
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
        btnGuideMicInput.setBackgroundResource(R.drawable.circle_mic_bg);
        setStatus("Votre assistant personnel");
    }

    private void setStatus(String s) {
        runOnUiThread(() -> tvGuideStatus.setText(s));
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] p, int[] g) {
        super.onRequestPermissionsResult(req, p, g);
        if (req == MIC_PERM && g.length > 0 && g[0] == PackageManager.PERMISSION_GRANTED)
            startListening();
    }

    @Override
    protected void onDestroy() {
        if (speechRecognizer != null) speechRecognizer.destroy();
        super.onDestroy();
    }
}