package com.society.module.member.repository;

import com.society.module.member.entity.OtpToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface OtpTokenRepository extends JpaRepository<OtpToken, Long> {

    Optional<OtpToken> findTopByPhoneAndVerifiedFalseOrderByCreatedAtDesc(String phone);

    @Modifying
    @Query("DELETE FROM OtpToken o WHERE o.phone = :phone AND o.verified = false")
    void deleteUnverifiedByPhone(@Param("phone") String phone);

    @Modifying
    @Query("DELETE FROM OtpToken o WHERE o.expiresAt < :now")
    void deleteExpiredTokens(@Param("now") LocalDateTime now);

    long countByPhoneAndCreatedAtAfter(String phone, LocalDateTime after);
}
