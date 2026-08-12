package com.society.module.voucher.repository;

import com.society.enums.VoucherType;
import com.society.module.voucher.entity.VoucherSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VoucherSequenceRepository extends JpaRepository<VoucherSequence, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT vs FROM VoucherSequence vs WHERE vs.voucherType = :type AND vs.financialYear = :fy")
    Optional<VoucherSequence> findByVoucherTypeAndFinancialYearForUpdate(
            @Param("type") VoucherType type,
            @Param("fy") String financialYear);

    Optional<VoucherSequence> findByVoucherTypeAndFinancialYear(VoucherType voucherType, String financialYear);
}
