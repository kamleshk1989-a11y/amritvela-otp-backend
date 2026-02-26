package com.amritvela.backend;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
     * ✅ Supports BOTH old & new param names:
     * - action_type OR type
     * - url OR video_url OR web_url
     *
     * ✅ Sends required keys in DATA payload so app can route properly.
     */
    @GetMapping("/api/send-notification")
    public String sendNotification(
            @RequestParam("token") String token,
            @RequestParam(value = "title", defaultValue = "Amritvela") String title,
            @RequestParam(value = "body", defaultValue = "Testing") String body,

            // old param
            @RequestParam(value = "action_type", required = false) String actionType,

            // new param (your URL uses this)
            @RequestParam(value = "type", required = false) String type,

            // old param
            @RequestParam(value = "url", required = false) String url,

            // new params (your URL uses video_url)
            @RequestParam(value = "video_url", required = false) String videoUrl,
            @RequestParam(value = "web_url", required = false) String webUrl,

            @RequestParam(value = "text", required = false) String text,

            // optional: send notification payload or only data
            @RequestParam(value = "use_notification", defaultValue = "true") boolean useNotification
    ) throws Exception {

        // ✅ Determine final action type (support both keys)
        String finalType = firstNonEmpty(actionType, type, "OPEN_VIDEO");

        // ✅ Determine final URLs
        // For OPEN_VIDEO prefer video_url, fallback to url
        // For OPEN_WEB   prefer web_url,   fallback to url
        String finalVideoUrl = firstNonEmpty(videoUrl, url);
        String finalWebUrl   = firstNonEmpty(webUrl, url);

        Message.Builder mb = Message.builder()
                .setToken(token)

                // ✅ MUST be in DATA payload
                .putData("action_type", finalType)
                .putData("title", title)
                .putData("body", body);

        // ✅ Put url keys properly so Android can open video/web
        if ("OPEN_VIDEO".equalsIgnoreCase(finalType)) {
            if (finalVideoUrl != null) {
                mb.putData("video_url", finalVideoUrl);
                mb.putData("url", finalVideoUrl); // fallback for older app logic
            }
        } else if ("OPEN_WEB".equalsIgnoreCase(finalType)) {
            if (finalWebUrl != null) {
                mb.putData("web_url", finalWebUrl);
                mb.putData("url", finalWebUrl); // fallback
            }
        } else {
            // generic fallback
            if (url != null) mb.putData("url", url);
        }

        if (text != null) mb.putData("text", text);

        // Optional: show notification UI (still keep data for routing)
        if (useNotification) {
            mb.setNotification(Notification.builder()
                    .setTitle(title)
                    .setBody(body)
                    .build());
        }

        String response = FirebaseMessaging.getInstance().send(mb.build());

        return "Sent: " + response
                + " | type=" + finalType
                + " | video_url=" + (finalVideoUrl == null ? "" : finalVideoUrl)
                + " | web_url=" + (finalWebUrl == null ? "" : finalWebUrl);
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