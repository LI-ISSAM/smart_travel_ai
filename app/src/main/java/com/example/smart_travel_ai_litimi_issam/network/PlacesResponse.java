package com.example.smart_travel_ai_litimi_issam.network;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class PlacesResponse {

    @SerializedName("results")
    public List<Result> results;

    @SerializedName("status")
    public String status;

    @SerializedName("error_message")
    public String errorMessage;

    public static class Result {
        @SerializedName("name")
        public String name;

        @SerializedName("vicinity")
        public String vicinity;

        @SerializedName("rating")
        public double rating;

        @SerializedName("geometry")
        public Geometry geometry;
    }

    public static class Geometry {
        @SerializedName("location")
        public Location location;
    }

    public static class Location {
        @SerializedName("lat")
        public double lat;

        @SerializedName("lng")
        public double lng;
    }
}
