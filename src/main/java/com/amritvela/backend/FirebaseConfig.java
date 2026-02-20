package com.amritvela.backend;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

@Configuration
public class FirebaseConfig {

    @Value("${firebase.serviceAccountJson:}")
    private String serviceAccountJson;

    @PostConstruct
    public void init() {
        try {
            if (!FirebaseApp.getApps().isEmpty()) return;

            if (serviceAccountJson == null || serviceAccountJson.trim().isEmpty()) {
                throw new RuntimeException("FIREBASE_SERVICE_ACCOUNT_JSON is empty. Add it in Railway Variables.");
            }

            ByteArrayInputStream serviceAccountStream =
                    new ByteArrayInputStream(serviceAccountJson.getBytes(StandardCharsets.UTF_8));

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccountStream))
                    .build();

            FirebaseApp.initializeApp(options);

            System.out.println("✅ Firebase Admin initialized (from env var JSON)");
        } catch (Exception e) {
            System.out.println("❌ Firebase init failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}