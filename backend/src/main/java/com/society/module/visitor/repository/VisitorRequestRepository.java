package com.society.module.visitor.repository;

import com.society.enums.VisitorStatus;
import com.society.module.visitor.entity.VisitorRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface VisitorRequestRepository extends JpaRepository<VisitorRequest, Long> {

    /** All requests in a given status, newest first (gate history / pending list). */
    @Query("SELECT r FROM VisitorRequest r JOIN FETCH r.unit " +
           "WHERE r.status = :status ORDER BY r.createdOn DESC")
    List<VisitorRequest> findByStatusOrderByCreatedOnDesc(@Param("status") VisitorStatus status);

    /** Full gate log (any status), newest first. */
    @Query("SELECT r FROM VisitorRequest r JOIN FETCH r.unit ORDER BY r.createdOn DESC")
    List<VisitorRequest> findAllByOrderByCreatedOnDesc();

    /** Requests for a specific unit, newest first. */
    @Query("SELECT r FROM VisitorRequest r JOIN FETCH r.unit " +
           "WHERE r.unit.unitId = :unitId ORDER BY r.createdOn DESC")
    List<VisitorRequest> findByUnitIdOrderByCreatedOnDesc(@Param("unitId") Long unitId);

    /**
     * Pending requests for any of the given units (the units an owner owns), newest
     * first. Used by the member portal to list what the owner can decide on.
     */
    @Query("SELECT r FROM VisitorRequest r JOIN FETCH r.unit " +
           "WHERE r.status = :status AND r.unit.unitId IN :unitIds " +
           "ORDER BY r.createdOn DESC")
    List<VisitorRequest> findByStatusAndUnitIds(@Param("status") VisitorStatus status,
                                                @Param("unitIds") List<Long> unitIds);

    /**
     * Still-PENDING requests whose expiry instant has passed — the set the
     * auto-expiry scheduler flips to TIMED_OUT.
     */
    @Query("SELECT r FROM VisitorRequest r " +
           "WHERE r.status = :status AND r.expiresAt < :now")
    List<VisitorRequest> findExpiredPending(@Param("status") VisitorStatus status,
                                            @Param("now") LocalDateTime now);

    /**
     * Still-PENDING requests that have NOT yet expired — the set the re-push ("nag")
     * scheduler re-notifies every few seconds until the owner acts or the request
     * times out. Unit is fetched so the notification body can name the flat.
     */
    @Query("SELECT r FROM VisitorRequest r JOIN FETCH r.unit " +
           "WHERE r.status = :status AND r.expiresAt > :now")
    List<VisitorRequest> findLivePending(@Param("status") VisitorStatus status,
                                         @Param("now") LocalDateTime now);

    long countByStatus(VisitorStatus status);
}
