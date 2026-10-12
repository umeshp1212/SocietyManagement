package com.society.module.notification.repository;

import com.society.module.notification.entity.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {

    Optional<DeviceToken> findByToken(String token);

    List<DeviceToken> findByOwnerId(Long ownerId);

    List<DeviceToken> findByOwnerIdIn(List<Long> ownerIds);

    boolean existsByToken(String token);
}
