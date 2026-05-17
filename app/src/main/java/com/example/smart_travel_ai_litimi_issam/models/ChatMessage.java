package com.example.smart_travel_ai_litimi_issam.models;

public class ChatMessage {

    public enum Type { USER, AI, TYPING }

    private String text;
    private Type type;

    // Lieu suggéré par l'IA (peut être null)
    private String placeName;
    private double placeLat;
    private double placeLng;
    private boolean hasPlace;

    // Message simple (user ou IA sans lieu)
    public ChatMessage(String text, Type type) {
        this.text     = text;
        this.type     = type;
        this.hasPlace = false;
    }

    // Message IA avec un lieu suggéré
    public ChatMessage(String text, Type type, String placeName, double lat, double lng) {
        this.text      = text;
        this.type      = type;
        this.placeName = placeName;
        this.placeLat  = lat;
        this.placeLng  = lng;
        this.hasPlace  = true;
    }

    public String getText()     { return text; }
    public Type   getType()     { return type; }
    public String getPlaceName(){ return placeName; }
    public double getPlaceLat() { return placeLat; }
    public double getPlaceLng() { return placeLng; }
    public boolean hasPlace()   { return hasPlace; }
}