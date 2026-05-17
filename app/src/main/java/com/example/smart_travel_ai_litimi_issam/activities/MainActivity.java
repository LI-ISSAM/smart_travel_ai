package com.example.smart_travel_ai_litimi_issam.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.example.smart_travel_ai_litimi_issam.R;
import com.example.smart_travel_ai_litimi_issam.adapters.PlacesAdapter;
import com.example.smart_travel_ai_litimi_issam.models.Place;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback, PlacesAdapter.OnPlaceClickListener {

    private static final int LOCATION_PERMISSION_REQUEST = 1001;
    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private PlacesAdapter adapter;
    private FirebaseAuth mAuth;
    private List<Marker> placeMarkers = new ArrayList<>();

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // NOUVELLES VARIABLES POUR LA RECHERCHE (Problème 3)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    private List<Place> allPlaces = new ArrayList<>(); // Tous les lieux
    private SearchView searchView;
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mAuth = FirebaseAuth.getInstance();
        if (mAuth.getCurrentUser() == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        // Init RecyclerView
        RecyclerView rvPlaces = findViewById(R.id.rvPlaces);
        rvPlaces.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PlacesAdapter(new ArrayList<>(), this);
        rvPlaces.setAdapter(adapter);

        // Init Map
        SupportMapFragment mapFragment = (SupportMapFragment)
                getSupportFragmentManager().findFragmentById(R.id.mapFragment);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Bouton Logout
        Button btnLogout = findViewById(R.id.btnLogout);
        btnLogout.setOnClickListener(v -> {
            mAuth.signOut();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // SETUP SEARCHVIEW (Problème 3 - NOUVEAU)
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        setupSearchView();
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

        // Bottom Navigation
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigationView);
        bottomNav.setSelectedItemId(R.id.nav_explore); // Explorer est sélectionné par défaut
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_explore) {
                // On est déjà ici, ne rien faire
                return true;
            }
            else if(id == R.id.nav_chat){
                startActivity(new Intent(this, ChatActivity.class));
                overridePendingTransition(0, 0); // Transition sans animation
                return true;

            }
            else if(id == R.id.nav_avatar){
                startActivity(new Intent(this, AvatarGuideActivity.class));
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

        requestLocationPermission();
    }


    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // SETUP SEARCHVIEW (Problème 3)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    private void setupSearchView() {
        searchView = findViewById(R.id.searchView);
        TextView textView = searchView.findViewById(androidx.appcompat.R.id.search_src_text);
        textView.setTextColor(Color.BLACK);      // texte tapé
        textView.setHintTextColor(Color.BLACK);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                // Lancer la recherche au clic sur le bouton "Chercher"
                searchPlaces(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                // Filtrer en temps réel pendant la saisie
                filterPlaces(newText);
                return true;
            }
        });
    }

    /**
     * Filtre les lieux par nom ou adresse
     */
    private void filterPlaces(String query) {
        List<Place> filtered = new ArrayList<>();

        if (query.isEmpty()) {
            // Si la barre est vide, afficher tous les lieux
            filtered.addAll(allPlaces);
        } else {
            // Sinon, filtrer par nom ou adresse
            String queryLower = query.toLowerCase();
            for (Place place : allPlaces) {
                if (place.getName().toLowerCase().contains(queryLower) ||
                        place.getAddress().toLowerCase().contains(queryLower)) {
                    filtered.add(place);
                }
            }
        }

        // Mettre à jour le RecyclerView
        adapter.updatePlaces(filtered);

        // Mettre à jour la carte
        updateMapMarkers(filtered);
    }

    /**
     * Recherche avancée (peut intégrer Google Places API)
     */
    private void searchPlaces(String query) {
        filterPlaces(query);
        Toast.makeText(this, "Recherche : " + query, Toast.LENGTH_SHORT).show();
    }

    /**
     * Met à jour les marqueurs sur la carte selon les résultats filtrés
     */
    private void updateMapMarkers(List<Place> places) {
        if (mMap == null) return;

        // Supprimer les anciens marqueurs
        for (Marker marker : placeMarkers) {
            marker.remove();
        }
        placeMarkers.clear();

        // Ajouter les nouveaux marqueurs
        for (Place place : places) {
            LatLng pos = new LatLng(place.getLat(), place.getLng());
            Marker marker = mMap.addMarker(new MarkerOptions()
                    .position(pos)
                    .title(place.getName())
                    .snippet(place.getAddress()));
            placeMarkers.add(marker);
        }

        // Centrer sur le premier lieu si résultats
        if (!places.isEmpty()) {
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(
                    new LatLng(places.get(0).getLat(), places.get(0).getLng()), 11));
        }
    }
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private void requestLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST);
        } else {
            loadMockPlaces();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadMockPlaces();
        }
    }

    private void loadMockPlaces() {
        allPlaces.clear(); // Réinitialiser la liste

        // === Casablanca ===
        allPlaces.add(new Place("Mosquée Hassan II", "Boulevard de la Corniche, Casablanca", 4.8, 33.6085, -7.6327));
        allPlaces.add(new Place("Morocco Mall", "Angle Boulevard de la Corniche, Casablanca", 4.5, 33.5883, -7.7058));
        allPlaces.add(new Place("Habous Quarter", "Quartier Habous, Casablanca", 4.6, 33.5815, -7.6041));
        allPlaces.add(new Place("Rick's Café", "Boulevard Sour Jdid, Casablanca", 4.4, 33.6052, -7.6206));
        allPlaces.add(new Place("Anfa Place", "Boulevard de la Corniche, Casablanca", 4.3, 33.5995, -7.6685));
        allPlaces.add(new Place("Cathédrale du Sacré-Cœur", "Angle Rue d'Alger, Casablanca", 4.2, 33.5925, -7.6225));
        allPlaces.add(new Place("Parc de la Ligue Arabe", "Casablanca Centre", 4.5, 33.5890, -7.6235));
        allPlaces.add(new Place("Twin Center", "Boulevard Zerktouni, Casablanca", 4.1, 33.5870, -7.6290));
        allPlaces.add(new Place("Corniche d'Aïn Diab", "Aïn Diab, Casablanca", 4.6, 33.5978, -7.6720));
        allPlaces.add(new Place("Marché Central", "Rue Chaouia, Casablanca Centre", 4.0, 33.5953, -7.6192));

        // === Marrakech ===
        allPlaces.add(new Place("Place Jemaa el-Fna", "Medina, Marrakech", 4.7, 31.6259, -7.9890));
        allPlaces.add(new Place("Jardins Majorelle", "Rue Yves Saint Laurent, Marrakech", 4.8, 31.6413, -8.0033));
        allPlaces.add(new Place("Bahia Palace", "Rue Riad Zitoun el Jdid, Marrakech", 4.5, 31.6209, -7.9831));
        allPlaces.add(new Place("Koutoubia Mosque", "Avenue Mohammed V, Marrakech", 4.7, 31.6237, -7.9943));
        allPlaces.add(new Place("Souks de Marrakech", "Medina, Marrakech", 4.6, 31.6317, -7.9866));

        // === Rabat ===
        allPlaces.add(new Place("Tour Hassan", "Avenue Imam Malik, Rabat", 4.7, 34.0240, -6.8217));
        allPlaces.add(new Place("Kasbah des Oudaias", "Rabat Medina", 4.6, 34.0292, -6.8411));
        allPlaces.add(new Place("Mausolée Mohammed V", "Avenue Imam Malik, Rabat", 4.8, 34.0233, -6.8221));
        allPlaces.add(new Place("Chellah", "Avenue Yacoub El Mansour, Rabat", 4.4, 33.9993, -6.8358));

        // === Fès ===
        allPlaces.add(new Place("Médina de Fès (Fès el Bali)", "Fès Medina", 4.8, 34.0609, -4.9782));
        allPlaces.add(new Place("Tanneries Chouara", "Hay Blida, Fès", 4.5, 34.0650, -4.9742));
        allPlaces.add(new Place("Medersa Bou Inania", "Talaa Kebira, Fès", 4.6, 34.0640, -4.9787));

        // === Essaouira ===
        allPlaces.add(new Place("Remparts d'Essaouira", "Medina d'Essaouira", 4.7, 31.5125, -9.7700));
        allPlaces.add(new Place("Port d'Essaouira", "Essaouira", 4.5, 31.5072, -9.7736));

        // === Agadir ===
        allPlaces.add(new Place("Plage d'Agadir", "Agadir", 4.6, 30.4202, -9.6000));
        allPlaces.add(new Place("Souk El Had d'Agadir", "Avenue du 29 Février, Agadir", 4.3, 30.4243, -9.5989));

        // Mettre à jour les affichages
        adapter.updatePlaces(allPlaces);
        addMarkersToMap(allPlaces);
    }

    private void addMarkersToMap(List<Place> places) {
        if (mMap == null) return;

        for (Marker marker : placeMarkers) marker.remove();
        placeMarkers.clear();

        for (Place place : places) {
            LatLng pos = new LatLng(place.getLat(), place.getLng());
            Marker marker = mMap.addMarker(new MarkerOptions()
                    .position(pos)
                    .title(place.getName())
                    .snippet(place.getAddress()));
            placeMarkers.add(marker);
        }

        // Centrer sur Casablanca par défaut
        if (!places.isEmpty()) {
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                    new LatLng(places.get(0).getLat(), places.get(0).getLng()), 11));
        }
    }

    @Override
    public void onPlaceClick(Place place) {
        if (mMap != null) {
            LatLng pos = new LatLng(place.getLat(), place.getLng());
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(pos, 15));

            for (Marker marker : placeMarkers) {
                if (marker.getPosition().latitude == pos.latitude &&
                        marker.getPosition().longitude == pos.longitude) {
                    marker.showInfoWindow();
                    break;
                }
            }

            Toast.makeText(this, "Exploration de : " + place.getName(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
        }
        loadMockPlaces();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // S'assurer que "Explorer" reste sélectionné au retour
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigationView);
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.nav_explore);
        }
    }
}