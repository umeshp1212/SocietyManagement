package com.society.module.maintenance.repository;

import com.society.module.maintenance.entity.MaintenancePayment;
import com.society.module.maintenance.entity.MaintenancePayment.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MaintenancePaymentRepository extends JpaRepository<MaintenancePayment, Long>,
        JpaSpecificationExecutor<MaintenancePayment> {

    List<MaintenancePayment> findByBill_BillIdOrderByPaymentDateDesc(Long billId);

    List<MaintenancePayment> findByUnit_UnitIdOrderByPaymentDateDesc(Long unitId);

    Page<MaintenancePayment> findByUnit_UnitIdOrderByPaymentDateDesc(Long unitId, Pageable pageable);

    Page<MaintenancePayment> findByUnit_UnitId(Long unitId, Pageable pageable);

    List<MaintenancePayment> findByBill_BillId(Long billId);

    Optional<MaintenancePayment> findByCashfreeOrderId(String cashfreeOrderId);

    Optional<MaintenancePayment> findByCashfreePaymentId(String cashfreePaymentId);

    Optional<MaintenancePayment> findByRazorpayPaymentId(String razorpayPaymentId);

    Optional<MaintenancePayment> findByRazorpayOrderId(String razorpayOrderId);

    Optional<MaintenancePayment> findByTransactionId(String transactionId);

    @Query("SELECT p FROM MaintenancePayment p WHERE p.status = :status ORDER BY p.paymentDate DESC")
    Page<MaintenancePayment> findByStatus(@Param("status") PaymentStatus status, Pageable pageable);

    @Query("SELECT COUNT(p) FROM MaintenancePayment p WHERE p.bill.billId = :billId AND p.status = 'SUCCESS'")
    long countSuccessfulByBill(@Param("billId") Long billId);
}
