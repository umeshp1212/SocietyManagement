package com.society.module.maintenance.repository;

import com.society.module.maintenance.entity.SuspenseAuditTrail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SuspenseAuditTrailRepository extends JpaRepository<SuspenseAuditTrail, Long> {

    List<SuspenseAuditTrail> findBySuspenseEntry_SuspenseIdOrderByPerformedOnDesc(Long suspenseId);
}
