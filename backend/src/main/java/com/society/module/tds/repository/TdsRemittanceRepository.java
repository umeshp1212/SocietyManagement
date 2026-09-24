package com.society.module.tds.repository;

import com.society.module.tds.entity.TdsRemittance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for {@link TdsRemittance} batches. Extends {@link JpaSpecificationExecutor}
 * so batches can be listed with the same dynamic, filter-driven, paginated approach used
 * elsewhere in the module.
 */
@Repository
public interface TdsRemittanceRepository extends JpaRepository<TdsRemittance, Long>,
        JpaSpecificationExecutor<TdsRemittance> {

    /** Looks up a batch by its unique business reference. */
    Optional<TdsRemittance> findByBatchReference(String batchReference);

    /** Guards {@code batchReference} uniqueness before assigning a generated reference. */
    boolean existsByBatchReference(String batchReference);
}
