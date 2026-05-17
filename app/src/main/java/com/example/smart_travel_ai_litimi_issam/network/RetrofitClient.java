package com.example.smart_travel_ai_litimi_issam.network;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RetrofitClient {

    private static final String BASE_URL = "http://192.168.11.109:3000/";
    private static Retrofit retrofit;

    public static Retrofit getInstance() {

        if (retrofit == null) {

            OkHttpClient client = new OkHttpClient.Builder()

                    // Temps connexion
                    .connectTimeout(5, TimeUnit.MINUTES)

                    // Temps lecture réponse Ollama
                    .readTimeout(5, TimeUnit.MINUTES)

                    // Temps upload image Base64
                    .writeTimeout(5, TimeUnit.MINUTES)

                    .retryOnConnectionFailure(true)
                    .build();

            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }

        return retrofit;
    }

    public static ApiService getApiService() {
        return getInstance().create(ApiService.class);
    }
}