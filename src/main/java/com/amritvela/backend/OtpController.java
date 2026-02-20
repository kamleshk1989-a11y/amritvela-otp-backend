package com.amritvela.backend;

import com.google.firebase.auth.FirebaseAuth;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class OtpController {

    private final WebClient webClient = WebClient.create("https://2factor.in");

    @Value("${twofactor.apiKey}")
    private String apiKey;

    // ---- 1) SEND OTP ----
    @PostMapping("/send-otp")
    public ResponseEntity<?> sendOtp(@RequestBody Map<String, String> body) {

        String phone = body.get("phone"); // digits only: 91XXXXXXXXXX
        if (phone == null || phone.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "phone required"));
        }

        // https://2factor.in/API/V1/{apiKey}/SMS/{phone}/AUTOGEN
        Map res = webClient.get()
                .uri("/API/V1/{key}/SMS/{phone}/AUTOGEN", apiKey, phone.trim())
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (res == null) return ResponseEntity.status(500).body(Map.of("error", "No response from 2Factor"));

        String status = String.valueOf(res.get("Status"));
        if (!"Success".equalsIgnoreCase(status)) {
            return ResponseEntity.status(400).body(Map.of("error", "2Factor send failed", "details", res));
        }

        // 2Factor returns sessionId in "Details"
        String sessionId = String.valueOf(res.get("Details"));
        return ResponseEntity.ok(Map.of("sessionId", sessionId));
    }

    // ---- 2) VERIFY OTP + RETURN FIREBASE CUSTOM TOKEN ----
    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(@RequestBody Map<String, String> body) {

        String phone = body.get("phone"); // digits only: 91XXXXXXXXXX
        String sessionId = body.get("sessionId");
        String otp = body.get("otp");

        if (phone == null || sessionId == null || otp == null ||
                phone.trim().isEmpty() || sessionId.trim().isEmpty() || otp.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "phone, sessionId, otp required"));
        }

        // https://2factor.in/API/V1/{apiKey}/SMS/VERIFY/{sessionId}/{otp}
        Map res = webClient.get()
                .uri("/API/V1/{key}/SMS/VERIFY/{sid}/{otp}", apiKey, sessionId.trim(), otp.trim())
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (res == null) return ResponseEntity.status(500).body(Map.of("error", "No response from 2Factor"));

        String status = String.valueOf(res.get("Status"));
        String details = String.valueOf(res.get("Details"));

        boolean matched = "Success".equalsIgnoreCase(status) && details.toLowerCase().contains("matched");
        if (!matched) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid OTP", "details", res));
        }

        // Create Firebase custom token
        try {
            String uid = "phone_" + phone.trim(); // stable uid
            String token = FirebaseAuth.getInstance().createCustomToken(uid);
            return ResponseEntity.ok(Map.of("firebaseCustomToken", token));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Firebase custom token failed", "msg", e.getMessage()));
        }
    }
}