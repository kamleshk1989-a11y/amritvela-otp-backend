package com.amritvela.backend;

import com.google.api.core.ApiFuture;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RestController
public class NotificationController {

    @GetMapping("/api")
    public String home() {
        return "OK - backend running";
    }

    @GetMapping("/api/ping")
    public String ping() {
        return "pong";
    }

    /**
     * ✅ Single user (token) send
     * Supports BOTH old & new param names:
     * - action_type OR type
     * - url OR video_url OR web_url
     */
    @GetMapping("/api/send-notification")
    public String sendNotification(
            @RequestParam("token") String token,
            @RequestParam(value = "title", defaultValue = "Amritvela") String title,
            @RequestParam(value = "body", defaultValue = "Testing") String body,

            @RequestParam(value = "action_type", required = false) String actionType,
            @RequestParam(value = "type", required = false) String type,

            @RequestParam(value = "url", required = false) String url,
            @RequestParam(value = "video_url", required = false) String videoUrl,
            @RequestParam(value = "web_url", required = false) String webUrl,

            @RequestParam(value = "text", required = false) String text,
            @RequestParam(value = "use_notification", defaultValue = "true") boolean useNotification
    ) throws Exception {

        String finalType = firstNonEmpty(actionType, type, "OPEN_VIDEO");

        String finalVideoUrl = firstNonEmpty(videoUrl, url);
        String finalWebUrl   = firstNonEmpty(webUrl, url);

        Message.Builder mb = Message.builder()
                .setToken(token)
                .putData("action_type", finalType)
                .putData("title", title)
                .putData("body", body);

        if ("OPEN_VIDEO".equalsIgnoreCase(finalType) && notEmpty(finalVideoUrl)) {
            mb.putData("video_url", finalVideoUrl.trim());
            mb.putData("url", finalVideoUrl.trim()); // fallback
        } else if ("OPEN_WEB".equalsIgnoreCase(finalType) && notEmpty(finalWebUrl)) {
            mb.putData("web_url", finalWebUrl.trim());
            mb.putData("url", finalWebUrl.trim());   // fallback
        } else if (notEmpty(url)) {
            mb.putData("url", url.trim());
        }

        if (notEmpty(text)) mb.putData("text", text.trim());

        if (useNotification) {
            mb.setNotification(Notification.builder().setTitle(title).setBody(body).build());
        }

        String response = FirebaseMessaging.getInstance().send(mb.build());

        return "Sent: " + response
                + " | type=" + finalType
                + " | video_url=" + (finalVideoUrl == null ? "" : finalVideoUrl)
                + " | web_url=" + (finalWebUrl == null ? "" : finalWebUrl);
    }

    /**
     * ✅ SEND TO ALL USERS (NOT TOPIC)
     * Reads tokens from Realtime DB: users_database/*
     *
     * Token key supported (auto-detect):
     * fcmToken, fcm_token, token, fcm, fcmtoken
     */
    @GetMapping("/api/send-notification-all-users")
    public String sendNotificationToAllUsers(
            @RequestParam(value = "title", defaultValue = "Amritvela") String title,
            @RequestParam(value = "body", defaultValue = "Testing") String body,
            @RequestParam(value = "action_type", defaultValue = "OPEN_VIDEO") String actionType,

            @RequestParam(value = "video_url", required = false) String videoUrl,
            @RequestParam(value = "web_url", required = false) String webUrl,
            @RequestParam(value = "text", required = false) String text,

            @RequestParam(value = "use_notification", defaultValue = "true") boolean useNotification
    ) throws Exception {

        // 1) Fetch all tokens from DB
        List<String> allTokens = fetchAllTokensFromUsersDatabase();

        if (allTokens.isEmpty()) {
            return "No tokens found in users_database.";
        }

        // 2) Prepare data payload
        String finalType = (actionType == null || actionType.trim().isEmpty()) ? "OPEN_VIDEO" : actionType.trim();

        String finalVideoUrl = notEmpty(videoUrl) ? videoUrl.trim() : null;
        String finalWebUrl   = notEmpty(webUrl) ? webUrl.trim() : null;

        int total = allTokens.size();
        int success = 0;
        int failure = 0;

        // 3) Send in batches of 500 (FCM limit)
        final int BATCH_SIZE = 500;

        for (int i = 0; i < allTokens.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, allTokens.size());
            List<String> batch = allTokens.subList(i, end);

            MulticastMessage.Builder mb = MulticastMessage.builder()
                    .addAllTokens(batch)
                    .putData("action_type", finalType)
                    .putData("title", title)
                    .putData("body", body);

            if ("OPEN_VIDEO".equalsIgnoreCase(finalType) && notEmpty(finalVideoUrl)) {
                mb.putData("video_url", finalVideoUrl);
                mb.putData("url", finalVideoUrl); // fallback
            } else if ("OPEN_WEB".equalsIgnoreCase(finalType) && notEmpty(finalWebUrl)) {
                mb.putData("web_url", finalWebUrl);
                mb.putData("url", finalWebUrl);   // fallback
            }

            if (notEmpty(text)) mb.putData("text", text.trim());

            if (useNotification) {
                mb.setNotification(Notification.builder().setTitle(title).setBody(body).build());
            }

            BatchResponse br = FirebaseMessaging.getInstance().sendEachForMulticast(mb.build());

            success += br.getSuccessCount();
            failure += br.getFailureCount();

            // OPTIONAL: you can later remove invalid tokens here by checking SendResponse exceptions.
            // List<SendResponse> responses = br.getResponses();
        }

        return "Sent to ALL users (tokens from DB). TotalTokens=" + total
                + " Success=" + success
                + " Failure=" + failure
                + " | type=" + finalType;
    }

    // ----------------- helpers -----------------

    private List<String> fetchAllTokensFromUsersDatabase() throws Exception {
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("users_database");

        ApiFuture<DataSnapshot> future = ref.get();
        DataSnapshot snap = future.get(); // waits

        Set<String> uniq = new HashSet<>();
        List<String> tokens = new ArrayList<>();

        for (DataSnapshot userSnap : snap.getChildren()) {
            String t = readTokenFromUserSnapshot(userSnap);
            if (notEmpty(t)) {
                t = t.trim();
                if (uniq.add(t)) tokens.add(t);
            }
        }
        return tokens;
    }

    private String readTokenFromUserSnapshot(DataSnapshot userSnap) {
        // Try multiple common keys so it works without knowing exact field name
        String[] keys = new String[]{"fcmToken", "fcm_token", "token", "fcm", "fcmtoken"};

        for (String k : keys) {
            Object v = userSnap.child(k).getValue();
            if (v != null) {
                String s = String.valueOf(v);
                if (notEmpty(s)) return s;
            }
        }

        // Sometimes token is nested, e.g. userSnap.child("device").child("fcmToken")
        // Add more checks here if you want later.

        return null;
    }

    private boolean notEmpty(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private String firstNonEmpty(String a, String b) {
        if (a != null && !a.trim().isEmpty()) return a.trim();
        if (b != null && !b.trim().isEmpty()) return b.trim();
        return null;
    }

    private String firstNonEmpty(String a, String b, String fallback) {
        String v = firstNonEmpty(a, b);
        return (v != null) ? v : fallback;
    }
}