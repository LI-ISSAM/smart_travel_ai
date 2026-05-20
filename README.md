#  Smart Travel AI

> **Votre guide de voyage intelligent propulsé par l'IA locale**  
> Application mobile Android pour explorer le Maroc avec l'intelligence artificielle

---

##  Aperçu

Smart Travel AI est une application mobile Android complète qui combine **cartographie interactive**, **IA conversationnelle locale**, **scanner visuel**, **journal de voyage** et **avatar guide** pour offrir une expérience de voyage unique au Maroc.

---

##  Fonctionnalités

| Fonctionnalité | Description |
|---|---|
|  **Explorateur GPS** | Carte Google Maps avec 27 lieux marocains + recherche temps réel |
|  **Chat IA** | Chatbot voyage propulsé par LLaMA 3.2 via Ollama |
|  **Micro + TTS** | Saisie vocale (STT) + lecture des réponses (TTS) en français |
|  **Scanner** | Identification de monuments et plats par photo (moondream vision IA) |
|  **Journal** | Journal de voyage avec narratives poétiques générées par IA |
|  **Avatar Guide** | Guide interactif animé avec synthèse vocale |
|  **Avatar Personnel** | Clone vidéo personnel animé (D-ID / Tavus API) |
|  **Profil** | Gestion du compte, photo de profil, préférences |

---

##  Architecture

```
┌─────────────────────────────────────────────────────┐
│              Application Android (Java)              │
│         Google Maps SDK · Firebase Auth · Retrofit   │
└──────────────────────┬──────────────────────────────┘
                       │ HTTP REST
┌──────────────────────▼──────────────────────────────┐
│           Backend Node.js / Express                  │
│              JWT Auth · Mongoose · CORS              │
└──────────┬───────────────────────────────────────────┘
           │
┌──────────▼──────────┐    ┌───────────────────────────┐
│   MongoDB local     │    │   Ollama (LLaMA 3.2)       │
│   port 27017        │    │            
└─────────────────────┘    └───────────────────────────┘
```

---

##  Stack Technique

### Mobile Android
- **Langage** : Java (Android SDK)
- **IDE** : Android Studio Hedgehog
- **Auth** : Firebase Authentication + Firestore
- **Carte** : Google Maps SDK 18.2.0 + Location Services 21.1.0
- **Réseau** : Retrofit 2 + OkHttp 4.12 + Gson
- **UI** : Material Design 3
- **Voix** : SpeechRecognizer + TextToSpeech

### Backend
- **Runtime** : Node.js 18 LTS
- **Framework** : Express.js 4.18
- **ODM** : Mongoose 7
- **DB** : MongoDB 6 (local port 27017)
- **Auth** : JWT (jsonwebtoken)

### IA
- **Modèle chat** : LLaMA 3.2 1B (via Ollama)
- **Modèle vision** : moondream:latest (via Ollama)
- **Avatar vidéo** : D-ID API / Tavus API

---

##  Prérequis

### PC (Backend + IA)
- Node.js 18+
- MongoDB 6+ (local)
- [Ollama](https://ollama.com) installé

### Android
- Android Studio Hedgehog+
- Android 8.0+ (API 26+)
- Compte Firebase
- Clé API Google Maps
- PC et téléphone sur le **même réseau Wi-Fi**

---

##  Installation

### 1. Cloner le projet

```bash
git clone https://github.com/votre-username/smart-travel-ai.git
cd smart-travel-ai
```

### 2. Configurer le Backend

```bash
cd backend
npm install
```

Créer le fichier `.env` :

```env
PORT=3000
MONGO_URI=mongodb://localhost:27017/smarttravel
JWT_SECRET=smart_travel_secret_key_2024
```

Démarrer le serveur :

```bash
node server.js
```

### 3. Configurer Ollama (IA locale)

```bash
# Installer le modèle chat
ollama pull llama3.2:1b

# Installer le modèle vision
ollama pull moondream

# Windows — lancer Ollama accessible sur le réseau
set OLLAMA_HOST=0.0.0.0
ollama serve

# Linux/Mac
export OLLAMA_HOST=0.0.0.0
ollama serve
```

Vérifier que Ollama répond :
```bash
curl http://localhost:11434/api/tags
```

### 4. Configurer l'Application Android

#### Firebase
1. Créer un projet sur [console.firebase.google.com](https://console.firebase.google.com)
2. Ajouter une app Android avec le package :  
   `com.example.smart_travel_ai_litimi_issam`
3. Télécharger `google-services.json` → placer dans `/app/`
4. Activer **Authentication > Email/Password**
5. Activer **Firestore Database** (mode test)

#### Google Maps
1. Aller sur [console.cloud.google.com](https://console.cloud.google.com)
2. Activer **Maps SDK for Android**
3. Créer une clé API
4. Dans `AndroidManifest.xml` :

```xml
<meta-data
    android:name="com.google.android.geo.API_KEY"
    android:value="VOTRE_CLE_API_GOOGLE_MAPS"/>
```

#### Configuration réseau
Trouver votre IP locale :
```bash
# Windows
ipconfig | findstr "IPv4"
```

Puis remplacer dans chaque fichier concerné :

```java
// ChatActivity.java + AvatarGuideActivity.java
private static final String OLLAMA_BASE_URL = "http://VOTRE_IP:11434";

// LandmarkScanActivity.java
private static final String OLLAMA_BASE_URL = "http://VOTRE_IP:11434";
private static final String VISION_MODEL    = "moondream:latest";

// RetrofitClient.java
private static final String BASE_URL = "http://VOTRE_IP:3000/";
```

### 5. Build et Run

Dans Android Studio :
```
Build > Make Project
Run > Run 'app'
```

---

##  Structure du Projet

```
smart-travel-ai/
│
├── app/src/main/
│   ├── java/com/example/smart_travel_ai_litimi_issam/
│   │   │
│   │   ├── activities/
│   │   │   ├── AvatarActivity.java           # Avatar personnel (D-ID/Tavus)
│   │   │   ├── AvatarGuideActivity.java      # Guide avatar interactif
│   │   │   ├── ChatActivity.java             # Chat IA + micro + TTS
│   │   │   ├── LandmarkScanActivity.java     # Scanner vision IA
│   │   │   ├── LoginActivity.java            # Connexion Firebase
│   │   │   ├── MainActivity.java             # Carte GPS + exploration
│   │   │   ├── ProfileActivity.java          # Profil utilisateur
│   │   │   ├── RegisterActivity.java         # Inscription Firebase
│   │   │   └── TravelJournalActivity.java    # Journal de voyage
│   │   │
│   │   ├── adapters/
│   │   │   ├── ChatAdapter.java              # RecyclerView messages chat
│   │   │   └── PlacesAdapter.java            # RecyclerView lieux
│   │   │
│   │   ├── models/
│   │   │   ├── ChatMessage.java              # Modèle message (USER/AI/TYPING)
│   │   │   ├── JournalEntryModel.java        # Modèle entrée journal + photo Base64
│   │   │   └── Place.java                    # Modèle lieu touristique
│   │   │
│   │   ├── network/
│   │   │   ├── ApiService.java               # Interface Retrofit (tous endpoints)
│   │   │   ├── DIdApiService.java            # Service API D-ID (avatar vidéo)
│   │   │   ├── DIdResponse.java              # Modèle réponse D-ID
│   │   │   ├── PlacesApiService.java         # Interface Google Places API
│   │   │   ├── PlacesResponse.java           # Modèle réponse Places
│   │   │   └── RetrofitClient.java           # Client HTTP Retrofit singleton
│   │   │
│   │   └── utils/
│   │       └── FirebaseHelper.java           # Utilitaires Firebase
│   │
│   └── res/
│       └── layout/
│           ├── activity_avatar.xml
│           ├── activity_avatar_guide.xml
│           ├── activity_chat.xml
│           ├── activity_landmark_scan.xml
│           ├── activity_login.xml
│           ├── activity_main.xml
│           ├── activity_profile.xml
│           ├── activity_register.xml
│           ├── activity_travel_journal.xml
│           ├── dialog_add_journal_entry.xml  # Dialogue ajout entrée journal
│           ├── item_journal_entry.xml         # Item liste journal
│           ├── item_message.xml               # Item bulle de message chat
│           └── item_place.xml                 # Item liste lieux
│
└── backend/
    ├── middleware/
    │   └── auth.js                            # Vérification JWT
    ├── models/
    │   ├── User.js                            # Schéma utilisateur MongoDB
    │   ├── ChatHistory.js                     # Schéma historique chat
    │   └── JournalEntry.js                    # Schéma entrée journal
    ├── routes/
    │   ├── auth.js                            # POST /api/auth/sync
    │   ├── history.js                         # CRUD /api/history
    │   ├── journal.js                         # CRUD /api/journal
    │   └── preferences.js                     # GET/PUT /api/preferences
    ├── .env
    ├── package.json
    └── server.js
```

---

##  API Endpoints

| Méthode | Endpoint | Auth | Description |
|---------|----------|------|-------------|
| `POST` | `/api/auth/sync` | ❌ | Sync Firebase → MongoDB, retourne JWT |
| `GET` | `/api/auth/me` | ✅ | Profil utilisateur courant |
| `POST` | `/api/history/chat` | ✅ | Sauvegarder un message |
| `GET` | `/api/history/chat` | ✅ | Historique complet du chat |
| `DELETE` | `/api/history/chat` | ✅ | Effacer tout l'historique |
| `POST` | `/api/history/search` | ✅ | Sauvegarder une recherche |
| `POST` | `/api/journal` | ✅ | Créer une entrée journal |
| `GET` | `/api/journal` | ✅ | Lister toutes les entrées |
| `GET` | `/api/journal/:id` | ✅ | Détail entrée (avec photo Base64) |
| `DELETE` | `/api/journal/:id` | ✅ | Supprimer une entrée |
| `DELETE` | `/api/journal` | ✅ | Supprimer toutes les entrées |
| `GET` | `/api/preferences` | ✅ | Lire les préférences |
| `PUT` | `/api/preferences` | ✅ | Modifier les préférences |

>   Header requis : `Authorization: Bearer <jwt_token>`

---

##  Lieux Couverts

| Ville | Nb lieux | Exemple phare |
|-------|----------|---------------|
| Casablanca | 10 | Mosquée Hassan II |
| Marrakech | 5 | Place Jemaa el-Fna |
| Rabat | 4 | Tour Hassan |
| Fès | 3 | Médina de Fès |
| Essaouira | 2 | Remparts d'Essaouira |
| Agadir | 2 | Plage d'Agadir |

---

## ⚙️ Variables de Configuration

### Android

| Fichier | Variable | Description |
|---------|----------|-------------|
| `ChatActivity.java` | `OLLAMA_BASE_URL` | IP + port Ollama |
| `ChatActivity.java` | `MODEL_NAME` | `llama3.2:1b` |
| `LandmarkScanActivity.java` | `VISION_MODEL` | `moondream:latest` |
| `AvatarGuideActivity.java` | `OLLAMA_URL` | IP + port Ollama + `/api/chat` |
| `AvatarActivity.java` | `AVATAR_IMAGE_URL` | URL publique de votre photo |
| `RetrofitClient.java` | `BASE_URL` | IP + port Backend Node.js |
| `DIdApiService.java` | `API_KEY` | Clé D-ID encodée Base64 |

### Backend `.env`

```env
PORT=3000
MONGO_URI=mongodb://localhost:27017/smarttravel
JWT_SECRET=votre_secret_jwt_ici
```

---

##  Problèmes Connus et Solutions

### Ollama inaccessible depuis le téléphone
```bash
# Vérifier que Ollama tourne
curl http://localhost:11434/api/tags

# S'assurer que OLLAMA_HOST est défini avant ollama serve
set OLLAMA_HOST=0.0.0.0
ollama serve
```

### Vision IA retourne des embeddings (`ids = [...]`)
Le modèle `moondream` nécessite l'endpoint `/api/generate` (pas `/api/chat`) et retourne le résultat dans le champ `response` (pas `message.content`).

### Erreur SpeechRecognizer code 5 (`ERROR_CLIENT`)
Le `SpeechRecognizer` doit être **détruit et recréé** à chaque écoute — ne jamais le réutiliser entre deux appels.

### Photos disparaissent après navigation
Utiliser `JournalEntryModel` (et non `Map<String, Object>`) pour la désérialisation Gson, et encoder les photos avec `Base64.NO_WRAP` pour éviter la troncature.

### Crash caméra — FileProvider
Vérifier que `res/xml/file_paths.xml` existe et que le `FileProvider` est déclaré dans `AndroidManifest.xml` avec `android:exported="false"`.

### Titre de l'Activity s'affiche verticalement
Ajouter `android:paddingTop="40dp"` sur le LinearLayout header pour respecter la barre de statut système (`fitsSystemWindows`).

---

##  Dépendances Android (`build.gradle.kts`)

```kotlin
dependencies {
    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:32.7.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")

    // Google Maps + Location
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.android.gms:play-services-location:21.1.0")

    // Réseau
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // UI
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.appcompat:appcompat:1.6.1")
}
```

##  Dépendances Backend (`package.json`)

```json
{
  "dependencies": {
    "express":      "^4.18.0",
    "mongoose":     "^7.0.0",
    "cors":         "^2.8.5",
    "dotenv":       "^16.0.0",
    "jsonwebtoken": "^9.0.0"
  }
}
```

---

##  Auteur

**LITIMI Issam**  
Développeur Full-Stack Android  
Encadrant : BOUSMAH Mohammed  
EMSI — Mai 2026

---

##  Licence

Ce projet est développé dans le cadre d'un projet académique à l'EMSI.  
Tous droits réservés © 2026 LITIMI Issam.
