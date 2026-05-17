package com.example.smart_travel_ai_litimi_issam.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;

import com.example.smart_travel_ai_litimi_issam.R;
import com.example.smart_travel_ai_litimi_issam.adapters.ChatAdapter;
import com.example.smart_travel_ai_litimi_issam.models.ChatMessage;
import com.example.smart_travel_ai_litimi_issam.network.RetrofitClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ChatActivity extends AppCompatActivity implements ChatAdapter.OnShowMapListener {

    private static final String OLLAMA_BASE_URL       = "http://192.168.11.109:11434";
    private static final String API_URL               = OLLAMA_BASE_URL + "/api/chat";
    private static final String MODEL_NAME            = "llama3.2:1b";
    private static final int    MIC_PERMISSION_REQUEST = 2001;

    private static final String SYSTEM_PROMPT =
            "Tu es un assistant voyage spécialisé au Maroc. " +
                    "Réponds rapidement, clairement et de manière professionnelle. " +
                    "Quand l'utilisateur demande un lieu, donne : " +
                    "1. une courte description, " +
                    "2. la ville ou région, " +
                    "3. les activités principales, " +
                    "4. pourquoi cet endroit est connu. " +
                    "Fais des réponses courtes, naturelles et utiles pour une application de voyage.";

    private RecyclerView      rvMessages;
    private TextInputEditText etMessage;
    private ChatAdapter       adapter;
    private List<ChatMessage> messages            = new ArrayList<>();
    private final JSONArray   conversationHistory = new JSONArray();

    private String jwtToken = null;

    private SpeechRecognizer speechRecognizer;
    private TextToSpeech     textToSpeech;
    private ImageButton      btnMic;
    private boolean          isListening = false;
    private boolean          ttsReady    = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        // ✅ Vérification auth EN PREMIER, avant tout autre appel
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        // ✅ Récupérer le JWT token sauvegardé au login
        jwtToken = getSharedPreferences("prefs", MODE_PRIVATE)
                .getString("jwt_token", null);

        // Init RecyclerView
        rvMessages = findViewById(R.id.rvMessages);
        LinearLayoutManager llm = new LinearLayoutManager(this);
        llm.setStackFromEnd(true);
        rvMessages.setLayoutManager(llm);
        adapter = new ChatAdapter(this, messages, this);
        rvMessages.setAdapter(adapter);

        etMessage = findViewById(R.id.etMessage);
        btnMic    = findViewById(R.id.btnMic);

        findViewById(R.id.btnSend).setOnClickListener(v -> sendMessage());
        etMessage.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                sendMessage();
                return true;
            }
            return false;
        });

        initTTS();
        initSpeechRecognizer();

        btnMic.setOnClickListener(v -> {
            if (isListening) stopListening();
            else checkMicPermissionAndStart();
        });

        // Charger l'historique depuis MongoDB
        loadChatHistoryFromBackend();

        // Bottom Navigation — ✅ nav_avatar ajouté
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigationView);
        bottomNav.setSelectedItemId(R.id.nav_chat);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_explore) {
                startActivity(new Intent(this, MainActivity.class));
                overridePendingTransition(0, 0);
                finish();
                return true;
            } else if (id == R.id.nav_scan) {
                startActivity(new Intent(this, LandmarkScanActivity.class));
                overridePendingTransition(0, 0);
                finish();
                return true;
            } else if (id == R.id.nav_chat) {
                return true; // déjà ici
            } else if (id == R.id.nav_avatar) {
                startActivity(new Intent(this, AvatarGuideActivity.class));
                overridePendingTransition(0, 0);
                finish();
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

    // ─────────────────────────────────────────
    //  CHARGER L'HISTORIQUE DEPUIS MONGODB
    // ─────────────────────────────────────────
    private void loadChatHistoryFromBackend() {
        if (jwtToken == null) {
            addAiMessage("Bonjour ! Je suis votre assistant voyage 🌍\n\nParlez ou écrivez pour me poser une question !", null, 0, 0);
            return;
        }

        RetrofitClient.getApiService()
                .getChatHistory("Bearer " + jwtToken)
                .enqueue(new Callback<List<Map<String, Object>>>() {
                    @Override
                    public void onResponse(Call<List<Map<String, Object>>> call,
                                           Response<List<Map<String, Object>>> response) {
                        if (response.isSuccessful() && response.body() != null
                                && !response.body().isEmpty()) {

                            for (Map<String, Object> entry : response.body()) {
                                String role    = (String) entry.get("role");
                                String content = (String) entry.get("content");
                                if ("user".equals(role)) {
                                    messages.add(new ChatMessage(content, ChatMessage.Type.USER));
                                } else if ("assistant".equals(role)) {
                                    parseAndAddHistoryMessage(content);
                                }
                                try {
                                    JSONObject msg = new JSONObject();
                                    msg.put("role", role);
                                    msg.put("content", content);
                                    conversationHistory.put(msg);
                                } catch (Exception ignored) {}
                            }

                            adapter.notifyDataSetChanged();
                            if (!messages.isEmpty())
                                rvMessages.scrollToPosition(messages.size() - 1);

                        } else {
                            addAiMessage("Bonjour ! Je suis votre assistant voyage 🌍\n\nParlez ou écrivez pour me poser une question !", null, 0, 0);
                        }
                    }

                    @Override
                    public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {
                        addAiMessage("Bonjour ! Je suis votre assistant voyage 🌍\n\nParlez ou écrivez pour me poser une question !", null, 0, 0);
                    }
                });
    }

    private void parseAndAddHistoryMessage(String content) {
        try {
            String cleaned = content.replace("```json", "").replace("```", "").trim();
            int start = cleaned.indexOf("{");
            int end   = cleaned.lastIndexOf("}");
            if (start != -1 && end != -1) cleaned = cleaned.substring(start, end + 1);

            JSONObject json     = new JSONObject(cleaned);
            String     message  = json.getString("message");
            boolean    hasPlace = json.optBoolean("hasPlace", false);
            String     name     = json.optString("placeName", null);
            double     lat      = json.optDouble("lat", 0);
            double     lng      = json.optDouble("lng", 0);

            if (hasPlace && lat != 0 && lng != 0) {
                messages.add(new ChatMessage(message, ChatMessage.Type.AI, name, lat, lng));
            } else {
                messages.add(new ChatMessage(message, ChatMessage.Type.AI));
            }
        } catch (Exception e) {
            messages.add(new ChatMessage(content, ChatMessage.Type.AI));
        }
    }

    // ─────────────────────────────────────────
    //  SAUVEGARDER UN MESSAGE DANS MONGODB
    // ─────────────────────────────────────────
    private void saveMessageToBackend(String role, String content) {
        if (jwtToken == null) return;

        Map<String, String> body = new HashMap<>();
        body.put("role", role);
        body.put("content", content);

        RetrofitClient.getApiService()
                .saveChatMessage("Bearer " + jwtToken, body)
                .enqueue(new Callback<Map<String, Object>>() {
                    @Override public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {}
                    @Override public void onFailure(Call<Map<String, Object>> call, Throwable t) {}
                });
    }

    // ─────────────────────────────────────────
    //  TEXT TO SPEECH
    // ─────────────────────────────────────────
    private void initTTS() {
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = textToSpeech.setLanguage(Locale.FRENCH);
                if (result == TextToSpeech.LANG_MISSING_DATA ||
                        result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    textToSpeech.setLanguage(Locale.ENGLISH);
                }
                textToSpeech.setSpeechRate(0.95f);
                ttsReady = true;
            }
        });
    }

    private void speak(String text) {
        if (ttsReady && text != null && !text.isEmpty()) {
            String cleanText = text.replaceAll("[^\\p{L}\\p{N}\\s.,!?']", "").trim();
            textToSpeech.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, "tts_response");
        }
    }

    // ─────────────────────────────────────────
    //  SPEECH TO TEXT
    // ─────────────────────────────────────────
    private void initSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Reconnaissance vocale non disponible", Toast.LENGTH_SHORT).show();
            if (btnMic != null) btnMic.setEnabled(false);
        }
    }

    private void checkMicPermissionAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, MIC_PERMISSION_REQUEST);
        } else {
            startListening();
        }
    }

    private void startListening() {
        if (textToSpeech != null) textToSpeech.stop();
        if (speechRecognizer != null) { speechRecognizer.destroy(); speechRecognizer = null; }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) {
                runOnUiThread(() -> {
                    isListening = true;
                    btnMic.setImageResource(android.R.drawable.ic_media_pause);
                });
            }
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() { runOnUiThread(() -> isListening = false); }
            @Override public void onError(int error) {
                runOnUiThread(() -> {
                    isListening = false;
                    btnMic.setImageResource(android.R.drawable.ic_btn_speak_now);
                });
            }
            @Override public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String spoken = matches.get(0);
                    runOnUiThread(() -> {
                        etMessage.setText(spoken);
                        isListening = false;
                        btnMic.setImageResource(android.R.drawable.ic_btn_speak_now);
                        sendMessage();
                    });
                }
            }
            @Override public void onPartialResults(Bundle partialResults) {}
            @Override public void onEvent(int eventType, Bundle params) {}
        });

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.FRENCH);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        speechRecognizer.startListening(intent);
    }

    private void stopListening() {
        if (speechRecognizer != null) speechRecognizer.stopListening();
        isListening = false;
        if (btnMic != null) btnMic.setImageResource(android.R.drawable.ic_btn_speak_now);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MIC_PERMISSION_REQUEST && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startListening();
        } else {
            Toast.makeText(this, "Permission micro refusée", Toast.LENGTH_SHORT).show();
        }
    }

    // ─────────────────────────────────────────
    //  ENVOI MESSAGE & APPEL OLLAMA
    // ─────────────────────────────────────────
    private void sendMessage() {
        String text = etMessage.getText().toString().trim();
        if (text.isEmpty()) return;

        messages.add(new ChatMessage(text, ChatMessage.Type.USER));
        adapter.notifyItemInserted(messages.size() - 1);
        rvMessages.scrollToPosition(messages.size() - 1);
        etMessage.setText("");

        saveMessageToBackend("user", text);

        messages.add(new ChatMessage("...", ChatMessage.Type.TYPING));
        int typingIndex = messages.size() - 1;
        adapter.notifyItemInserted(typingIndex);
        rvMessages.scrollToPosition(typingIndex);

        new Thread(() -> callOllamaAPI(text, typingIndex)).start();
    }

    private void callOllamaAPI(String userMessage, int typingIndex) {
        try {
            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            userMsg.put("content", userMessage);
            conversationHistory.put(userMsg);

            JSONObject body = new JSONObject();
            body.put("model", MODEL_NAME);
            body.put("stream", false);

            JSONArray allMessages = new JSONArray();
            JSONObject systemMsg = new JSONObject();
            systemMsg.put("role", "system");
            systemMsg.put("content", SYSTEM_PROMPT);
            allMessages.put(systemMsg);
            for (int i = 0; i < conversationHistory.length(); i++) {
                allMessages.put(conversationHistory.get(i));
            }
            body.put("messages", allMessages);

            URL url = new URL(API_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(120000);

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

                JSONObject assistantMsg = new JSONObject();
                assistantMsg.put("role", "assistant");
                assistantMsg.put("content", rawText);
                conversationHistory.put(assistantMsg);

                saveMessageToBackend("assistant", rawText);

                runOnUiThread(() -> {
                    removeTypingIndicator(typingIndex);
                    parseAndDisplayResponse(rawText);
                });

            } else {
                BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getErrorStream(), "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                final String errBody = sb.toString();
                runOnUiThread(() -> {
                    removeTypingIndicator(typingIndex);
                    addAiMessage("❌ Erreur Ollama " + responseCode + "\n" + errBody, null, 0, 0);
                });
            }

        } catch (java.net.ConnectException e) {
            runOnUiThread(() -> {
                removeTypingIndicator(typingIndex);
                addAiMessage("❌ Ollama inaccessible !\n\nVérifie :\n1. ollama serve tourne\n2. OLLAMA_HOST=0.0.0.0\n3. IP correcte : " + OLLAMA_BASE_URL + "\n4. PC et téléphone sur le même WiFi", null, 0, 0);
            });
        } catch (java.net.SocketTimeoutException e) {
            runOnUiThread(() -> {
                removeTypingIndicator(typingIndex);
                addAiMessage("⏱️ Timeout — Ollama met trop de temps à répondre.", null, 0, 0);
            });
        } catch (Exception e) {
            runOnUiThread(() -> {
                removeTypingIndicator(typingIndex);
                addAiMessage("❌ Erreur : " + e.getClass().getSimpleName() + " — " + e.getMessage(), null, 0, 0);
            });
        }
    }

    private void parseAndDisplayResponse(String rawText) {
        try {
            String cleaned = rawText.replace("```json", "").replace("```", "").trim();
            int start = cleaned.indexOf("{");
            int end   = cleaned.lastIndexOf("}");
            if (start != -1 && end != -1) cleaned = cleaned.substring(start, end + 1);

            JSONObject json     = new JSONObject(cleaned);
            String     message  = json.getString("message");
            boolean    hasPlace = json.optBoolean("hasPlace", false);
            String     name     = json.optString("placeName", null);
            double     lat      = json.optDouble("lat", 0);
            double     lng      = json.optDouble("lng", 0);

            if (hasPlace && lat != 0 && lng != 0) {
                addAiMessage(message, name, lat, lng);
            } else {
                addAiMessage(message, null, 0, 0);
            }
            speak(message);

        } catch (Exception e) {
            addAiMessage(rawText, null, 0, 0);
            speak(rawText);
        }
    }

    private void addAiMessage(String text, String placeName, double lat, double lng) {
        ChatMessage msg = (placeName != null && lat != 0)
                ? new ChatMessage(text, ChatMessage.Type.AI, placeName, lat, lng)
                : new ChatMessage(text, ChatMessage.Type.AI);
        messages.add(msg);
        adapter.notifyItemInserted(messages.size() - 1);
        rvMessages.scrollToPosition(messages.size() - 1);
    }

    private void removeTypingIndicator(int index) {
        if (index >= 0 && index < messages.size() &&
                messages.get(index).getType() == ChatMessage.Type.TYPING) {
            messages.remove(index);
            adapter.notifyItemRemoved(index);
        }
    }

    @Override
    public void onShowMap(String placeName, double lat, double lng) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("focusPlaceName", placeName);
        intent.putExtra("focusLat", lat);
        intent.putExtra("focusLng", lng);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        overridePendingTransition(0, 0);
    }

    @Override
    protected void onDestroy() {
        if (speechRecognizer != null) speechRecognizer.destroy();
        if (textToSpeech != null) { textToSpeech.stop(); textToSpeech.shutdown(); }
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (textToSpeech != null) textToSpeech.stop();
        stopListening();
    }
}