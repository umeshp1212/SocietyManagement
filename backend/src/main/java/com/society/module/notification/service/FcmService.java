package com.society.module.notification.service;

import com.google.auth.oauth2.GoogleCredentials;
import com.society.module.notification.entity.DeviceToken;
import com.society.module.visitor.dto.VisitorRecipient;
import com.society.module.visitor.entity.VisitorRequest;
import com.society.module.visitor.service.VisitorNotifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Sends visitor/delivery push notifications to members via the free Firebase Cloud
 * Messaging (FCM) HTTP v1 API. Implements {@link VisitorNotifier} and is {@code @Primary},
 * so the visitor service pushes through here when available.
 *
 * <p><b>Degrades gracefully.</b> The whole integration is feature-flagged by
 * {@code app.fcm.enabled}. When disabled, or when credentials are missing, or when a
 * send fails, the service logs and returns without throwing — the in-app approval list
 * (polled by the member portal) remains the source of truth, so the gate flow still
 * works end-to-end without any Firebase setup.</p>
 *
 * <p><b>Deploy-time setup.</b> Create a free Firebase project, download a service-account
 * JSON, and set {@code app.fcm.enabled=true}, {@code app.fcm.project-id}, and
 * {@code app.fcm.credentials-path}. The web/VAPID key is configured on the frontend.</p>
 */
@Service
@Primary
@Slf4j
public class FcmService implements VisitorNotifier {

    private static final String FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging";

    private final DeviceTokenService deviceTokenService;

    @Value("${app.fcm.enabled:false}")
    private boolean enabled;

    @Value("${app.fcm.project-id:}")
    private String projectId;

    @Value("${app.fcm.credentials-path:}")
    private String credentialsPath;

    public FcmService(DeviceTokenService deviceTokenService) {
        this.deviceTokenService = deviceTokenService;
    }

    /**
     * Logs a clear, actionable status at startup so FCM misconfiguration is obvious in
     * the boot log rather than only on the first visitor request. Never throws — a bad
     * FCM config must not stop the app.
     */
    @PostConstruct
    void logStartupStatus() {
        if (!enabled) {
            log.info("FCM push disabled (app.fcm.enabled=false). Visitor approvals use in-app polling.");
            return;
        }
        if (projectId == null || projectId.isBlank()) {
            log.warn("FCM enabled but app.fcm.project-id is blank — push will fail. Set FCM_PROJECT_ID.");
        }
        File file = (credentialsPath == null) ? null : new File(credentialsPath);
        if (file == null || !file.exists()) {
            log.warn("FCM enabled but credentials file is missing at '{}'. "
                    + "Mount the service-account JSON there (host ./secrets/fcm-service-account.json).",
                    credentialsPath);
            return;
        }
        // A complete service-account key is ~2.3 KB (the PEM private_key alone is ~1.7 KB).
        // A much smaller file almost always means the private_key was truncated in transfer
        // (e.g. pasted into an editor) — the usual cause of a 400 from Google's token endpoint.
        long size = file.length();
        if (size < 1500) {
            log.warn("FCM credentials file at '{}' is only {} bytes — it looks truncated "
                    + "(a valid service-account key is ~2.3 KB). Re-download the key from Firebase "
                    + "Console > Project settings > Service accounts and copy it with scp (not paste).",
                    credentialsPath, size);
        }
        try {
            acquireAccessToken();
            log.info("FCM push enabled and credentials verified (project '{}').", projectId);
        } catch (Exception e) {
            log.warn("FCM enabled but credentials at '{}' could not mint a token: {}. "
                    + "Common causes: truncated private_key, wrong project, FCM API not enabled, "
                    + "or server clock skew (check 'date -u' / NTP).",
                    credentialsPath, e.getMessage());
        }
    }

    @Override
    public void notifyNewRequest(VisitorRequest request, List<VisitorRecipient> recipients) {
        if (!enabled) {
            log.debug("FCM disabled — skipping push for visitor request {}.", request.getVisitorRequestId());
            return;
        }

        List<Long> ownerIds = recipients.stream()
                .filter(r -> r.getType() == VisitorRecipient.Type.OWNER && r.getOwnerId() != null)
                .map(VisitorRecipient::getOwnerId)
                .collect(Collectors.toList());

        List<DeviceToken> tokens = deviceTokenService.tokensForOwners(ownerIds);
        if (tokens.isEmpty()) {
            log.debug("No registered device tokens for visitor request {} recipients.",
                    request.getVisitorRequestId());
            return;
        }

        String title = "Delivery/Visitor at the gate";
        String flat = request.getUnit() != null ? request.getUnit().getUnitNumber() : "your flat";
        String company = request.getCompany() != null ? (" from " + request.getCompany()) : "";
        String body = request.getVisitorName() + company + " is waiting for entry to " + flat
                + ". Tap to approve or reject.";

        String accessToken;
        try {
            accessToken = acquireAccessToken();
        } catch (Exception e) {
            log.warn("Could not acquire FCM access token; skipping push for request {}: {}",
                    request.getVisitorRequestId(), e.toString());
            return;
        }

        log.info("FCM: sending push for request {} to {} device token(s).",
                request.getVisitorRequestId(), tokens.size());
        for (DeviceToken token : tokens) {
            Map<String, Object> message = buildMessage(token.getToken(), title, body,
                    String.valueOf(request.getVisitorRequestId()));
            try {
                sendMessage(accessToken, message);
                log.info("FCM: push accepted for request {} (token …{}).",
                        request.getVisitorRequestId(), tail(token.getToken()));
            } catch (org.springframework.web.client.HttpStatusCodeException e) {
                // FCM returns the real reason in the body (UNREGISTERED, SenderId
                // mismatch, InvalidArgument, etc.) — log it so the cause is visible.
                log.warn("FCM send REJECTED for request {} (token …{}): {} {}",
                        request.getVisitorRequestId(), tail(token.getToken()),
                        e.getStatusCode(), e.getResponseBodyAsString());
            } catch (RuntimeException e) {
                // One bad token must not stop the others.
                log.warn("FCM send failed for a token on request {}: {}",
                        request.getVisitorRequestId(), e.toString());
            }
        }
    }

    /** Last few chars of a token, for safe log correlation. */
    private static String tail(String token) {
        if (token == null || token.length() <= 8) {
            return "?";
        }
        return token.substring(token.length() - 8);
    }

    /** Builds an FCM HTTP v1 message payload for a single device token. */
    Map<String, Object> buildMessage(String deviceToken, String title, String body, String requestId) {
        return Map.of("message", Map.of(
                "token", deviceToken,
                "notification", Map.of("title", title, "body", body),
                "data", Map.of(
                        "type", "VISITOR_REQUEST",
                        "visitorRequestId", requestId,
                        "click_action", "/member/visitor-approvals")
        ));
    }

    /**
     * Mints a short-lived OAuth2 access token from the configured service-account
     * credentials. Isolated so tests can override without real credentials.
     *
     * <p>Fails with an actionable message for the two common misconfigurations: a
     * missing credentials file, and a file that is the Firebase <em>web app</em> config
     * (apiKey/appId) rather than a <em>service-account</em> key — the latter is the
     * usual cause of a {@code 400 Bad Request} from Google's token endpoint.</p>
     */
    protected String acquireAccessToken() throws Exception {
        if (credentialsPath == null || credentialsPath.isBlank()) {
            throw new IllegalStateException(
                    "app.fcm.credentials-path is not set. Point it at the service-account JSON.");
        }
        File file = new File(credentialsPath);
        if (!file.exists()) {
            throw new FileNotFoundException(
                    "FCM service-account file not found at '" + credentialsPath
                    + "'. Mount the JSON there (host ./secrets/fcm-service-account.json) and "
                    + "check the filename uses dashes, not dots.");
        }

        try (InputStream in = new FileInputStream(file)) {
            GoogleCredentials credentials = GoogleCredentials.fromStream(in)
                    .createScoped(List.of(FCM_SCOPE));
            credentials.refreshIfExpired();
            return credentials.getAccessToken().getTokenValue();
        } catch (IOException e) {
            // GoogleCredentials.fromStream throws IOException when the JSON is not a
            // recognisable service-account key (e.g. the web-app config was pasted in).
            throw new IllegalStateException(
                    "Could not read '" + credentialsPath + "' as a Google service-account key. "
                    + "Make sure it is the file from Firebase Console > Project settings > "
                    + "Service accounts > Generate new private key (it must contain "
                    + "\"type\": \"service_account\" and a \"private_key\"), not the web app config "
                    + "(apiKey/appId). Cause: " + e.getMessage(), e);
        }
    }

    /**
     * Performs the actual HTTP POST to the FCM v1 send endpoint. Isolated in a single
     * seam so unit tests can assert the payload and simulate failures without network.
     */
    protected void sendMessage(String accessToken, Map<String, Object> message) {
        RestTemplate restTemplate = new RestTemplate();
        String url = "https://fcm.googleapis.com/v1/projects/" + projectId + "/messages:send";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(message, headers);
        restTemplate.postForEntity(url, entity, String.class);
    }

    /** Visible for configuration/testing. */
    boolean isEnabled() {
        return enabled;
    }
}
