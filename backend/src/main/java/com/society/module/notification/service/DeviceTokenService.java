package com.society.module.notification.service;

import com.society.exception.BusinessException;
import com.society.module.notification.entity.DeviceToken;
import com.society.module.notification.repository.DeviceTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Manages FCM device-token registrations for members. Registration is idempotent on the
 * token value: re-registering a token simply re-points it at the current owner (handles
 * a shared device or a token that migrated between accounts) rather than creating
 * duplicates.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceTokenService {

    private final DeviceTokenRepository repository;

    @Transactional
    public void register(Long ownerId, String token, String platform) {
        if (ownerId == null) {
            throw new BusinessException("Owner id is required to register a device token.");
        }
        if (token == null || token.isBlank()) {
            throw new BusinessException("A device token is required.");
        }

        String platformValue = (platform == null || platform.isBlank()) ? "WEB" : platform.trim().toUpperCase();

        repository.findByToken(token.trim())
                .ifPresentOrElse(existing -> {
                    existing.setOwnerId(ownerId);
                    existing.setPlatform(platformValue);
                    repository.save(existing);
                    log.debug("Re-registered existing device token for owner {}.", ownerId);
                }, () -> {
                    repository.save(DeviceToken.builder()
                            .ownerId(ownerId)
                            .token(token.trim())
                            .platform(platformValue)
                            .build());
                    log.debug("Registered new device token for owner {}.", ownerId);
                });
    }

    /** All registered tokens for a set of owners (recipients of a visitor request). */
    public List<DeviceToken> tokensForOwners(List<Long> ownerIds) {
        if (ownerIds == null || ownerIds.isEmpty()) {
            return List.of();
        }
        return repository.findByOwnerIdIn(ownerIds);
    }
}
