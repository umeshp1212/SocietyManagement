package com.society.module.notification.service;

import com.society.enums.VisitorStatus;
import com.society.module.notification.entity.DeviceToken;
import com.society.module.owner.entity.Unit;
import com.society.module.visitor.dto.VisitorRecipient;
import com.society.module.visitor.entity.VisitorRequest;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link FcmService} (plan Task 6): asserts the push payload targets the
 * recipient's registered token and carries the deep-link data, and that the service is a
 * graceful no-op when disabled. The network/credential seams
 * ({@code acquireAccessToken}/{@code sendMessage}) are overridden so no real FCM call is made.
 */
class FcmServiceTest {

    /** Captures what would be sent instead of performing a real HTTP/credential call. */
    private static final class CapturingFcmService extends FcmService {
        final List<Map<String, Object>> sent = new ArrayList<>();

        CapturingFcmService(DeviceTokenService deviceTokenService) {
            super(deviceTokenService);
        }

        @Override
        protected String acquireAccessToken() {
            return "fake-access-token";
        }

        @Override
        protected void sendMessage(String accessToken, Map<String, Object> message) {
            sent.add(message);
        }
    }

    private VisitorRequest request() {
        Unit unit = Unit.builder().unitId(5L).unitNumber("A-101").build();
        return VisitorRequest.builder()
                .visitorRequestId(42L).unit(unit).visitorName("Courier Guy").company("Amazon")
                .status(VisitorStatus.PENDING).expiresAt(LocalDateTime.now().plusMinutes(10))
                .build();
    }

    private List<VisitorRecipient> ownerRecipient(Long ownerId) {
        return List.of(VisitorRecipient.builder()
                .type(VisitorRecipient.Type.OWNER).ownerId(ownerId).name("Alice").build());
    }

    @Test
    void buildsPayloadTargetingTokenWithDeepLinkData() {
        DeviceTokenService deviceTokenService = mock(DeviceTokenService.class);
        when(deviceTokenService.tokensForOwners(List.of(1L)))
                .thenReturn(List.of(DeviceToken.builder().ownerId(1L).token("device-token-1").build()));

        CapturingFcmService service = new CapturingFcmService(deviceTokenService);
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "projectId", "demo-project");

        service.notifyNewRequest(request(), ownerRecipient(1L));

        assertThat(service.sent).hasSize(1);
        @SuppressWarnings("unchecked")
        Map<String, Object> message = (Map<String, Object>) service.sent.get(0).get("message");
        assertThat(message.get("token")).isEqualTo("device-token-1");

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) message.get("data");
        assertThat(data.get("type")).isEqualTo("VISITOR_REQUEST");
        assertThat(data.get("visitorRequestId")).isEqualTo("42");

        @SuppressWarnings("unchecked")
        Map<String, Object> notification = (Map<String, Object>) message.get("notification");
        assertThat((String) notification.get("body")).contains("Courier Guy").contains("Amazon").contains("A-101");
    }

    @Test
    void noOpWhenDisabled() {
        DeviceTokenService deviceTokenService = mock(DeviceTokenService.class);
        CapturingFcmService service = new CapturingFcmService(deviceTokenService);
        ReflectionTestUtils.setField(service, "enabled", false);

        service.notifyNewRequest(request(), ownerRecipient(1L));

        assertThat(service.sent).isEmpty();
        verifyNoInteractions(deviceTokenService);
    }

    @Test
    void noOpWhenNoTokensRegistered() {
        DeviceTokenService deviceTokenService = mock(DeviceTokenService.class);
        when(deviceTokenService.tokensForOwners(List.of(1L))).thenReturn(List.of());

        CapturingFcmService service = new CapturingFcmService(deviceTokenService);
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "projectId", "demo-project");

        service.notifyNewRequest(request(), ownerRecipient(1L));

        assertThat(service.sent).isEmpty();
    }
}
