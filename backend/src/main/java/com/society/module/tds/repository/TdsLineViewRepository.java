package com.society.module.tds.repository;

import com.society.module.tds.entity.TdsLineView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * Read-only repository over the {@code tds_line_view} projection.
 *
 * <p>Extends {@link JpaSpecificationExecutor} so callers (e.g. the TDS specification builder)
 * can supply {@code Specification<TdsLineView>} filters for paginated, dynamically-filtered
 * queries. The underlying entity is {@link com.society.module.tds.entity.TdsLineView @Immutable};
 * only read operations are meaningful.
 */
@Repository
public interface TdsLineViewRepository extends JpaRepository<TdsLineView, Long>,
        JpaSpecificationExecutor<TdsLineView> {
}
