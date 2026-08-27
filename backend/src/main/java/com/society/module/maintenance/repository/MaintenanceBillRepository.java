package com.society.module.maintenance.repository;

import com.society.module.maintenance.entity.MaintenanceBill;
import com.society.module.maintenance.entity.MaintenanceBill.BillStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MaintenanceBillRepository extends JpaRepository<MaintenanceBill, Long> {

    Optional<MaintenanceBill> findByUnit_UnitIdAndBillMonthAndBillYear(Long unitId, Integer month, Integer year);

    List<MaintenanceBill> findByBillMonthAndBillYear(Integer month, Integer year);

    Page<MaintenanceBill> findByBillMonthAndBillYear(Integer month, Integer year, Pageable pageable);

    List<MaintenanceBill> findByUnit_UnitIdOrderByBillYearDescBillMonthDesc(Long unitId);

    Page<MaintenanceBill> findByStatus(BillStatus status, Pageable pageable);

    @Query("SELECT b FROM MaintenanceBill b WHERE b.status IN ('UNPAID','OVERDUE','PARTIALLY_PAID') AND b.unit.unitId = :unitId ORDER BY b.billYear, b.billMonth")
    List<MaintenanceBill> findOutstandingByUnit(@Param("unitId") Long unitId);

    @Query("SELECT b FROM MaintenanceBill b WHERE b.status IN ('UNPAID','OVERDUE') AND b.billMonth = :month AND b.billYear = :year")
    List<MaintenanceBill> findDefaulters(@Param("month") Integer month, @Param("year") Integer year);

    @Query("SELECT SUM(b.balanceAmount) FROM MaintenanceBill b WHERE b.status IN ('UNPAID','OVERDUE','PARTIALLY_PAID') AND b.unit.unitId = :unitId")
    java.math.BigDecimal getTotalOutstandingByUnit(@Param("unitId") Long unitId);

    @Query("SELECT SUM(b.paidAmount) FROM MaintenanceBill b WHERE b.billMonth = :month AND b.billYear = :year")
    java.math.BigDecimal getTotalCollectedForMonth(@Param("month") Integer month, @Param("year") Integer year);

    @Query("SELECT SUM(b.totalAmount) FROM MaintenanceBill b WHERE b.billMonth = :month AND b.billYear = :year")
    java.math.BigDecimal getTotalBilledForMonth(@Param("month") Integer month, @Param("year") Integer year);

    long countByBillMonthAndBillYearAndStatus(Integer month, Integer year, BillStatus status);

    boolean existsByUnit_UnitIdAndBillMonthAndBillYear(Long unitId, Integer month, Integer year);

    Optional<MaintenanceBill> findByCashfreeOrderId(String cashfreeOrderId);

    Optional<MaintenanceBill> findByRazorpayOrderId(String razorpayOrderId);
}
