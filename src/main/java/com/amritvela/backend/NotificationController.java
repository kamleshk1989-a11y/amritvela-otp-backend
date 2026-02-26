package com.amritvela.backend;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import com.google.firebase.database.ValueEventListener;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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
        @RequestParam("admin_key") String adminKey,
        @RequestParam(value = "title", defaultValue = "Amritvela") String title,
        @RequestParam(value = "body", defaultValue = "Testing") String body,
        @RequestParam(value = "action_type", defaultValue = "OPEN_VIDEO") String actionType,

        @RequestParam(value = "video_url", required = false) String videoUrl,
        @RequestParam(value = "web_url", required = false) String webUrl,
        @RequestParam(value = "text", required = false) String text,

        @RequestParam(value = "use_notification", defaultValue = "true") boolean useNotification
) throws Exception {

    // ✅ SECURITY CHECK (must be first)
    String secret = System.getenv("ADMIN_SECRET_KEY");
    if (secret == null || secret.trim().isEmpty()) {
        return "Server missing ADMIN_SECRET_KEY (Railway Variables).";
    }
    if (adminKey == null || !secret.equals(adminKey)) {
        return "Unauthorized";
    }

    List<String> allTokens = fetchAllTokensFromUsersDatabase();

    if (allTokens.isEmpty()) {
        return "No tokens found in users_database.";
    }

    String finalType = (actionType == null || actionType.trim().isEmpty()) ? "OPEN_VIDEO" : actionType.trim();
    String finalVideoUrl = notEmpty(videoUrl) ? videoUrl.trim() : null;
    String finalWebUrl   = notEmpty(webUrl) ? webUrl.trim() : null;

    int total = allTokens.size();
    int success = 0;
    int failure = 0;

    final int BATCH_SIZE = 500; // FCM limit

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
    }

    return "Sent to ALL users (tokens from DB). TotalTokens=" + total
            + " Success=" + success
            + " Failure=" + failure
            + " | type=" + finalType;
}
    // ----------------- Firebase Admin DB read (NO ref.get()) -----------------

    private List<String> fetchAllTokensFromUsersDatabase() throws Exception {

        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("users_database");

        final CountDownLatch latch = new CountDownLatch(1);

        final List<String> tokens = new ArrayList<>();
        final Set<String> uniq = new HashSet<>();

        final String[] errorHolder = new String[1];

        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snap) {
                try {
                    for (DataSnapshot userSnap : snap.getChildren()) {
                        String t = readTokenFromUserSnapshot(userSnap);
                        if (notEmpty(t)) {
                            t = t.trim();
                            if (uniq.add(t)) tokens.add(t);
                        }
                    }
                } catch (Exception e) {
                    errorHolder[0] = "onDataChange error: " + e.getMessage();
                } finally {
                    latch.countDown();
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                errorHolder[0] = "DB cancelled: " + error.getMessage();
                latch.countDown();
            }
        });

        boolean ok = latch.await(15, TimeUnit.SECONDS);
        if (!ok) throw new RuntimeException("DB timeout while reading users_database (15s).");
        if (errorHolder[0] != null) throw new RuntimeException(errorHolder[0]);

        return tokens;
    }

    private String readTokenFromUserSnapshot(DataSnapshot userSnap) {
        String[] keys = new String[]{"fcmToken", "fcm_token", "token", "fcm", "fcmtoken"};

        for (String k : keys) {
            Object v = userSnap.child(k).getValue();
            if (v != null) {
                String s = String.valueOf(v);
                if (notEmpty(s)) return s;
            }
        }
        return null;
    }

    // ----------------- helpers -----------------

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