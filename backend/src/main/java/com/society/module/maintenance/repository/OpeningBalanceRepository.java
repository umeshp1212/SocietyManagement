package com.society.module.maintenance.repository;

import com.society.module.maintenance.entity.OpeningBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface OpeningBalanceRepository extends JpaRepository<OpeningBalance, Long> {

    Optional<OpeningBalance> findByUnit_UnitId(Long unitId);

    List<OpeningBalance> findAllByOrderByUnit_UnitNumberAsc();

    @Query("SELECT ob FROM OpeningBalance ob WHERE ob.balanceAmount > 0 ORDER BY ob.unit.unitNumber")
    List<OpeningBalance> findWithOutstandingBalance();

    @Query("SELECT COALESCE(SUM(ob.balanceAmount), 0) FROM OpeningBalance ob WHERE ob.unit.unitId = :unitId")
    BigDecimal getTotalOpeningBalanceByUnit(@Param("unitId") Long unitId);

    @Query("SELECT COALESCE(SUM(ob.balanceAmount), 0) FROM OpeningBalance ob")
    BigDecimal getTotalOpeningBalanceOutstanding();

    boolean existsByUnit_UnitId(Long unitId);
}
