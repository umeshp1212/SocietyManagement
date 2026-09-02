package com.society.module.maintenance.repository;

import com.society.module.maintenance.entity.MaintenanceLedger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MaintenanceLedgerRepository extends JpaRepository<MaintenanceLedger, Long> {

    List<MaintenanceLedger> findByBillIdOrderByPerformedOnAscLedgerIdAsc(Long billId);

    List<MaintenanceLedger> findByUnitIdOrderByPerformedOnDescLedgerIdDesc(Long unitId);

    List<MaintenanceLedger> findByPaymentIdOrderByPerformedOnAsc(Long paymentId);
}
