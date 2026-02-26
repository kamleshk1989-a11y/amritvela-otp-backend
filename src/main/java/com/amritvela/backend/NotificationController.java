package com.yourpackage;  // ⚠️ use same package as your other controllers

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class NotificationController {

    @PostMapping("/send-notification")
    public ResponseEntity<String> sendNotification(
            @RequestParam String token,
            @RequestParam String url
    ) {

        try {

            Message message = Message.builder()
                    .setToken(token)
                    .putData("action_type", "OPEN_VIDEO")
                    .putData("url", url)
                    .putData("title", "Live Kirtan")
                    .putData("text", "Tap to watch")
                    .build();

            String response = FirebaseMessaging.getInstance().send(message);

            return ResponseEntity.ok("Sent: " + response);

        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("Error: " + e.getMessage());
        }
    }
}