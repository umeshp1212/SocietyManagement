package com.society.module.voucher.repository;

import com.society.module.voucher.entity.VoucherAuditTrail;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VoucherAuditTrailRepository extends JpaRepository<VoucherAuditTrail, Long> {

    List<VoucherAuditTrail> findByVoucher_VoucherIdOrderByChangedOnDesc(Long voucherId);

    Page<VoucherAuditTrail> findAllByOrderByChangedOnDesc(Pageable pageable);

    List<VoucherAuditTrail> findByChangedByOrderByChangedOnDesc(String changedBy);
}
