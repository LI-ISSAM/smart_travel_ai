package com.example.smart_travel_ai_litimi_issam.network;

import com.example.smart_travel_ai_litimi_issam.models.JournalEntryModel;

import retrofit2.Call;
import retrofit2.http.*;
import java.util.List;
import java.util.Map;

public interface ApiService {

    // ── Auth ──────────────────────────────────────────────────────
    @POST("api/auth/sync")
    Call<Map<String, Object>> syncUser(@Body Map<String, String> body);

    // ── Chat history ──────────────────────────────────────────────
    @POST("api/history/chat")
    Call<Map<String, Object>> saveChatMessage(
            @Header("Authorization") String token,
            @Body Map<String, String> body
    );

    @GET("api/history/chat")
    Call<List<Map<String, Object>>> getChatHistory(
            @Header("Authorization") String token
    );

    @DELETE("api/history/chat")
    Call<Map<String, Object>> deleteChatHistory(
            @Header("Authorization") String token
    );

    // ── Search history ────────────────────────────────────────────
    @POST("api/history/search")
    Call<Map<String, Object>> saveSearch(
            @Header("Authorization") String token,
            @Body Map<String, String> body
    );

    // ── Journal (MongoDB) ─────────────────────────────────────────

    @POST("api/journal")
    Call<Map<String, Object>> saveJournalEntry(
            @Header("Authorization") String token,
            @Body Map<String, Object> body
    );

    // ✅ Utilise JournalEntryModel pour désérialiser photoBase64 correctement
    @GET("api/journal")
    Call<List<JournalEntryModel>> getJournalEntries(
            @Header("Authorization") String token
    );

    @GET("api/journal/{id}")
    Call<Map<String, Object>> getJournalEntry(
            @Header("Authorization") String token,
            @Path("id") String id
    );

    @DELETE("api/journal/{id}")
    Call<Map<String, Object>> deleteJournalEntry(
            @Header("Authorization") String token,
            @Path("id") String id
    );
}