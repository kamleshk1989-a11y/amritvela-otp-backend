package com.amritvela.backend;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

@Configuration
public class FirebaseConfig {

    @PostConstruct
    public void init() {
        try {
            // prevent double init
            if (!FirebaseApp.getApps().isEmpty()) return;

            String saJson = System.getenv("FIREBASE_SERVICE_ACCOUNT_JSON");
            if (saJson == null || saJson.trim().isEmpty()) {
                throw new RuntimeException("Missing FIREBASE_SERVICE_ACCOUNT_JSON");
            }

            String dbUrl = System.getenv("FIREBASE_DB_URL");
            if (dbUrl == null || dbUrl.trim().isEmpty()) {
                throw new RuntimeException("Missing FIREBASE_DB_URL");
            }

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(
                            new ByteArrayInputStream(saJson.getBytes(StandardCharsets.UTF_8))
                    ))
                    .setDatabaseUrl(dbUrl) // ✅ THIS FIXES YOUR ERROR
                    .build();

            FirebaseApp.initializeApp(options);

            System.out.println("✅ Firebase initialized (DB URL set): " + dbUrl);

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Firebase init failed: " + e.getMessage(), e);
        }
    }
}