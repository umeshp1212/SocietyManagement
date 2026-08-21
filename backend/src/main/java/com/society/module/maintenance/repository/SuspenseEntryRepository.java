package com.society.module.maintenance.repository;

import com.society.module.maintenance.entity.SuspenseEntry;
import com.society.module.maintenance.entity.SuspenseEntry.SuspenseStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface SuspenseEntryRepository extends JpaRepository<SuspenseEntry, Long> {

    Page<SuspenseEntry> findByStatusOrderByReceivedDateDesc(SuspenseStatus status, Pageable pageable);

    Page<SuspenseEntry> findAllByOrderByReceivedDateDesc(Pageable pageable);

    List<SuspenseEntry> findByStatusOrderByReceivedDateDesc(SuspenseStatus status);

    List<SuspenseEntry> findByAssignedToUnit_UnitIdOrderByAssignedOnDesc(Long unitId);

    @Query("SELECT COUNT(s) FROM SuspenseEntry s WHERE s.status = :status")
    long countByStatus(@Param("status") SuspenseStatus status);

    @Query("SELECT COALESCE(SUM(s.amount), 0) FROM SuspenseEntry s WHERE s.status = :status")
    BigDecimal getTotalAmountByStatus(@Param("status") SuspenseStatus status);

    @Query("SELECT s FROM SuspenseEntry s WHERE " +
           "LOWER(s.referenceNumber) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(s.description) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<SuspenseEntry> search(@Param("search") String search, Pageable pageable);
}
