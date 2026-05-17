package com.example.smart_travel_ai_litimi_issam.network;

import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class DIdApiService {

    private static final String BASE_URL = "https://api.d-id.com";
    private static final String API_KEY  = "cGVyZW1vbjM2NEBnbWFpbC5jb20:_0NIt5SGhEbgjZFJsRaCl";

    public static String createTalkVideo(String imageUrl, String textToSpeak, String voiceName) throws Exception {

        // ✅ Body correct selon doc D-ID v1
        JSONObject voiceConfig = new JSONObject();
        voiceConfig.put("type", "microsoft");
        voiceConfig.put("voice_id", voiceName);

        JSONObject script = new JSONObject();
        script.put("type", "text");
        script.put("input", textToSpeak);
        script.put("provider", voiceConfig);

        JSONObject config = new JSONObject();
        config.put("fluent", false);
        config.put("pad_audio", 0);

        JSONObject body = new JSONObject();
        body.put("source_url", imageUrl);
        body.put("script", script);
        body.put("config", config);

        Log.d("DID", "Request body: " + body.toString());

        URL url = new URL(BASE_URL + "/talks");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Basic " + API_KEY);
        conn.setRequestProperty("Accept", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);

        OutputStream os = conn.getOutputStream();
        os.write(body.toString().getBytes("UTF-8"));
        os.close();

        int responseCode = conn.getResponseCode();
        Log.d("DID", "Response code: " + responseCode);

        // ✅ Lire le bon stream selon le code
        BufferedReader br;
        if (responseCode >= 200 && responseCode < 300) {
            br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
        } else {
            // Lire l'erreur pour savoir ce qui ne va pas
            br = new BufferedReader(new InputStreamReader(conn.getErrorStream(), "UTF-8"));
            StringBuilder errSb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) errSb.append(line);
            br.close();
            Log.e("DID", "Erreur response: " + errSb.toString());
            throw new Exception("D-ID HTTP " + responseCode + " : " + errSb.toString());
        }

        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();

        Log.d("DID", "Response: " + sb.toString());

        JSONObject response = new JSONObject(sb.toString());
        return response.getString("id");
    }

    public static String getTalkVideoUrl(String talkId) throws Exception {
        URL url = new URL(BASE_URL + "/talks/" + talkId);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Basic " + API_KEY);
        conn.setRequestProperty("Accept", "application/json");

        int responseCode = conn.getResponseCode();

        BufferedReader br;
        if (responseCode >= 200 && responseCode < 300) {
            br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
        } else {
            br = new BufferedReader(new InputStreamReader(conn.getErrorStream(), "UTF-8"));
            StringBuilder errSb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) errSb.append(line);
            br.close();
            throw new Exception("D-ID GET HTTP " + responseCode + " : " + errSb.toString());
        }

        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();

        JSONObject response = new JSONObject(sb.toString());
        String status = response.getString("status");

        Log.d("DID", "Talk status: " + status);

        if (status.equals("done")) {
            return response.getString("result_url");
        } else if (status.equals("error")) {
            throw new Exception("D-ID erreur vidéo: " + response.optString("description", "unknown"));
        }

        return null; // Encore en cours → on reessaie
    }
}