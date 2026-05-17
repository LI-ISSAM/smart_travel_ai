package com.example.smart_travel_ai_litimi_issam.models;

public class Place {
    private String name;
    private String address;
    private double rating;
    private double lat;
    private double lng;

    public Place(String name, String address, double rating, double lat, double lng) {
        this.name = name;
        this.address = address;
        this.rating = rating;
        this.lat = lat;
        this.lng = lng;
    }

    public String getName() { return name; }
    public String getAddress() { return address; }
    public double getRating() { return rating; }
    public double getLat() { return lat; }
    public double getLng() { return lng; }
}
