package com.amritvela.backend;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class NotificationController {

    // ✅ move this away from "/"
    @GetMapping("/api")
    public String home() {
        return "OK - backend running";
    }

    @GetMapping("/api/ping")
    public String ping() {
        return "pong";
    }

    @GetMapping("/api/send-notification")
    public String sendNotification(
            @RequestParam("token") String token,
            @RequestParam(value = "title", defaultValue = "Amritvela") String title,
            @RequestParam(value = "body", defaultValue = "Testing") String body,
            @RequestParam(value = "action_type", defaultValue = "OPEN_VIDEO") String actionType,
            @RequestParam(value = "url", required = false) String url,
            @RequestParam(value = "text", required = false) String text
    ) throws Exception {

        Message.Builder mb = Message.builder()
                .setToken(token)
                .putData("action_type", actionType);

        if (url != null) mb.putData("url", url);
        if (text != null) mb.putData("text", text);

        // Optional notification display
        mb.setNotification(Notification.builder().setTitle(title).setBody(body).build());

        String response = FirebaseMessaging.getInstance().send(mb.build());
        return "Sent: " + response;
    }
}