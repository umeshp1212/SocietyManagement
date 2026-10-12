package com.society.module.notification.service;

import com.society.exception.BusinessException;
import com.society.module.notification.entity.DeviceToken;
import com.society.module.notification.repository.DeviceTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DeviceTokenService} (plan Task 6): new-token registration and
 * idempotent re-registration (dedup on the token value), plus input validation.
 */
class DeviceTokenServiceTest {

    private DeviceTokenRepository repository;
    private DeviceTokenService service;

    @BeforeEach
    void setUp() {
        repository = mock(DeviceTokenRepository.class);
        service = new DeviceTokenService(repository);
    }

    @Test
    void registersNewToken() {
        when(repository.findByToken("tok-abc")).thenReturn(Optional.empty());

        service.register(1L, "tok-abc", "WEB");

        ArgumentCaptor<DeviceToken> captor = ArgumentCaptor.forClass(DeviceToken.class);
        verify(repository).save(captor.capture());
        DeviceToken saved = captor.getValue();
        assertThat(saved.getOwnerId()).isEqualTo(1L);
        assertThat(saved.getToken()).isEqualTo("tok-abc");
        assertThat(saved.getPlatform()).isEqualTo("WEB");
    }

    @Test
    void reRegisteringSameTokenUpdatesOwnerNoDuplicate() {
        DeviceToken existing = DeviceToken.builder()
                .deviceTokenId(10L).ownerId(1L).token("tok-abc").platform("WEB").build();
        when(repository.findByToken("tok-abc")).thenReturn(Optional.of(existing));

        service.register(2L, "tok-abc", "web");

        // Same row updated (re-pointed to owner 2), not a new insert.
        assertThat(existing.getOwnerId()).isEqualTo(2L);
        assertThat(existing.getPlatform()).isEqualTo("WEB");
        verify(repository).save(existing);
    }

    @Test
    void defaultsPlatformToWeb() {
        when(repository.findByToken(any())).thenReturn(Optional.empty());
        service.register(1L, "tok-xyz", null);
        ArgumentCaptor<DeviceToken> captor = ArgumentCaptor.forClass(DeviceToken.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getPlatform()).isEqualTo("WEB");
    }

    @Test
    void rejectsMissingToken() {
        assertThatThrownBy(() -> service.register(1L, "  ", "WEB"))
                .isInstanceOf(BusinessException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsMissingOwner() {
        assertThatThrownBy(() -> service.register(null, "tok", "WEB"))
                .isInstanceOf(BusinessException.class);
    }
}
