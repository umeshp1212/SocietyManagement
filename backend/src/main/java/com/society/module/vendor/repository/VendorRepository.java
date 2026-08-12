package com.society.module.vendor.repository;

import com.society.enums.VendorCategory;
import com.society.enums.VendorStatus;
import com.society.module.vendor.entity.Vendor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface VendorRepository extends JpaRepository<Vendor, Long> {

    Page<Vendor> findByStatus(VendorStatus status, Pageable pageable);

    Page<Vendor> findByCategory(VendorCategory category, Pageable pageable);

    Page<Vendor> findByStatusAndCategory(VendorStatus status, VendorCategory category, Pageable pageable);

    @Query("SELECT v FROM Vendor v WHERE " +
           "(LOWER(v.vendorName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(v.contactPerson) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "v.phone LIKE CONCAT('%', :search, '%'))")
    Page<Vendor> searchVendors(@Param("search") String search, Pageable pageable);

    @Query("SELECT v FROM Vendor v WHERE v.status = :status AND " +
           "(LOWER(v.vendorName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(v.contactPerson) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "v.phone LIKE CONCAT('%', :search, '%'))")
    Page<Vendor> searchVendorsByStatus(@Param("status") VendorStatus status,
                                       @Param("search") String search,
                                       Pageable pageable);

    List<Vendor> findByStatusOrderByVendorNameAsc(VendorStatus status);

    // Find vendors with contracts expiring within given days
    @Query("SELECT v FROM Vendor v WHERE v.status = 'ACTIVE' AND " +
           "v.agreementEndDate IS NOT NULL AND " +
           "v.agreementEndDate BETWEEN :today AND :expiryDate")
    List<Vendor> findVendorsWithExpiringContracts(@Param("today") LocalDate today,
                                                  @Param("expiryDate") LocalDate expiryDate);

    // Find vendors with expired contracts
    @Query("SELECT v FROM Vendor v WHERE v.status = 'ACTIVE' AND " +
           "v.agreementEndDate IS NOT NULL AND v.agreementEndDate < :today")
    List<Vendor> findVendorsWithExpiredContracts(@Param("today") LocalDate today);

    long countByStatus(VendorStatus status);

    long countByCategory(VendorCategory category);
}
