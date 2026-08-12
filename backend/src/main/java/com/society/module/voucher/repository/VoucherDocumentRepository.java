package com.society.module.voucher.repository;

import com.society.module.voucher.entity.VoucherDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VoucherDocumentRepository extends JpaRepository<VoucherDocument, Long> {

    List<VoucherDocument> findByVoucher_VoucherIdOrderByUploadedOnDesc(Long voucherId);
}
