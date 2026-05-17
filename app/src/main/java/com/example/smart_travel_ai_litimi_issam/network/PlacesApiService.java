package com.example.smart_travel_ai_litimi_issam.network;


import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface PlacesApiService {

    @GET("maps/api/place/nearbysearch/json")
    Call<PlacesResponse> getNearbyPlaces(
            @Query("location") String location,   // "lat,lng"
            @Query("radius") int radius,           // en mètres
            @Query("type") String type,            // "restaurant", "movie_theater"
            @Query("key") String apiKey
    );
}