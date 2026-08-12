package com.society.module.vendor.repository;

import com.society.module.vendor.entity.VendorDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VendorDocumentRepository extends JpaRepository<VendorDocument, Long> {

    List<VendorDocument> findByVendor_VendorIdOrderByUploadedOnDesc(Long vendorId);
}
