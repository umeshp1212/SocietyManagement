package com.society.module.tenant.repository;

import com.society.module.tenant.entity.TenantDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TenantDocumentRepository extends JpaRepository<TenantDocument, Long> {

    List<TenantDocument> findByTenant_TenantIdOrderByUploadedOnDesc(Long tenantId);
}
