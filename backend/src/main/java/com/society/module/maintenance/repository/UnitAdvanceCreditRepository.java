package com.society.module.maintenance.repository;

import com.society.module.maintenance.entity.UnitAdvanceCredit;
import com.society.module.maintenance.entity.UnitAdvanceCredit.CreditStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface UnitAdvanceCreditRepository extends JpaRepository<UnitAdvanceCredit, Long> {

    List<UnitAdvanceCredit> findByUnit_UnitIdOrderByReceivedDateDescAdvanceCreditIdDesc(Long unitId);

    List<UnitAdvanceCredit> findByUnit_UnitIdAndStatusOrderByReceivedDateAscAdvanceCreditIdAsc(
            Long unitId, CreditStatus status);

    /** Total still-available advance credit for a unit (0 if none). */
    @Query("SELECT COALESCE(SUM(c.balanceAmount), 0) FROM UnitAdvanceCredit c "
            + "WHERE c.unit.unitId = :unitId AND c.status = 'AVAILABLE'")
    BigDecimal getAvailableCreditByUnit(@Param("unitId") Long unitId);
}
