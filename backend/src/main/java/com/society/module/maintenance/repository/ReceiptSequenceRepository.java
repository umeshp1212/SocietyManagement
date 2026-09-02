package com.society.module.maintenance.repository;

import com.society.module.maintenance.entity.ReceiptSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReceiptSequenceRepository extends JpaRepository<ReceiptSequence, Long> {

    /**
     * Fetch the sequence row for a period with a pessimistic write lock (SELECT ... FOR
     * UPDATE) so concurrent receipt generation is serialized and numbers never collide.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT rs FROM ReceiptSequence rs WHERE rs.period = :period")
    Optional<ReceiptSequence> findByPeriodForUpdate(@Param("period") String period);

    Optional<ReceiptSequence> findByPeriod(String period);
}
