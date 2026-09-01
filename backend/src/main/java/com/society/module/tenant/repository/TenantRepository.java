package com.society.module.tenant.repository;

import com.society.enums.NocStatus;
import com.society.enums.PoliceVerificationStatus;
import com.society.enums.TenantStatus;
import com.society.module.tenant.entity.Tenant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, Long> {

    Page<Tenant> findByStatus(TenantStatus status, Pageable pageable);

    @Query("SELECT t FROM Tenant t JOIN FETCH t.unit WHERE t.status = :status ORDER BY t.createdOn DESC")
    List<Tenant> findByStatusOrderByCreatedOnDesc(@Param("status") TenantStatus status);

    Page<Tenant> findByNocStatus(NocStatus nocStatus, Pageable pageable);

    @Query("SELECT t FROM Tenant t JOIN FETCH t.unit WHERE t.unit.unitId = :unitId AND t.status = :status")
    Optional<Tenant> findActiveByUnitId(@Param("unitId") Long unitId, @Param("status") TenantStatus status);

    @Query("SELECT t FROM Tenant t JOIN FETCH t.unit WHERE t.unit.unitId = :unitId ORDER BY t.rentStartDate DESC")
    List<Tenant> findAllByUnitIdOrderByRentStartDateDesc(@Param("unitId") Long unitId);

    @Query("SELECT t FROM Tenant t JOIN FETCH t.unit WHERE t.status = :status AND " +
           "(LOWER(t.tenantName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "t.contactNumber LIKE CONCAT('%', :search, '%'))")
    Page<Tenant> searchTenantsByStatus(@Param("status") TenantStatus status,
                                       @Param("search") String search,
                                       Pageable pageable);

    @Query("SELECT t FROM Tenant t JOIN FETCH t.unit WHERE " +
           "LOWER(t.tenantName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "t.contactNumber LIKE CONCAT('%', :search, '%')")
    Page<Tenant> searchTenants(@Param("search") String search, Pageable pageable);

    // Agreements expiring within given date range
    @Query("SELECT t FROM Tenant t JOIN FETCH t.unit WHERE t.status = 'ACTIVE' AND " +
           "t.rentEndDate IS NOT NULL AND t.rentEndDate BETWEEN :today AND :expiryDate")
    List<Tenant> findTenantsWithExpiringAgreements(@Param("today") LocalDate today,
                                                   @Param("expiryDate") LocalDate expiryDate);

    // Police verification pending beyond days
    @Query("SELECT t FROM Tenant t JOIN FETCH t.unit WHERE t.status = 'ACTIVE' AND " +
           "t.policeVerificationStatus IN ('NOT_INITIATED', 'SUBMITTED') AND " +
           "t.rentStartDate <= :cutoffDate")
    List<Tenant> findTenantsWithPendingPoliceVerification(@Param("cutoffDate") LocalDate cutoffDate);

    // Police verification expiring (older than 1 year)
    @Query("SELECT t FROM Tenant t JOIN FETCH t.unit WHERE t.status = 'ACTIVE' AND " +
           "t.policeVerificationStatus = 'VERIFIED'")
    List<Tenant> findActiveVerifiedTenants();

    long countByStatus(TenantStatus status);

    long countByNocStatus(NocStatus nocStatus);

    long countByPoliceVerificationStatus(PoliceVerificationStatus status);
}
