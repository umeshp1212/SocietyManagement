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

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
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

        for (DeviceToken token : tokens) {
            Map<String, Object> message = buildMessage(token.getToken(), title, body,
                    String.valueOf(request.getVisitorRequestId()));
            try {
                sendMessage(accessToken, message);
            } catch (RuntimeException e) {
                // One bad token must not stop the others.
                log.warn("FCM send failed for a token on request {}: {}",
                        request.getVisitorRequestId(), e.toString());
            }
        }
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
     */
    protected String acquireAccessToken() throws Exception {
        try (InputStream in = new FileInputStream(credentialsPath)) {
            GoogleCredentials credentials = GoogleCredentials.fromStream(in)
                    .createScoped(List.of(FCM_SCOPE));
            credentials.refreshIfExpired();
            return credentials.getAccessToken().getTokenValue();
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
