package com.society.module.voucher.repository;

import com.society.enums.ExpenseCategory;
import com.society.enums.VoucherStatus;
import com.society.enums.VoucherType;
import com.society.module.voucher.entity.Voucher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface VoucherRepository extends JpaRepository<Voucher, Long> {

    Optional<Voucher> findByVoucherNumber(String voucherNumber);

    Page<Voucher> findByVoucherType(VoucherType voucherType, Pageable pageable);

    Page<Voucher> findByStatus(VoucherStatus status, Pageable pageable);

    Page<Voucher> findByCategory(ExpenseCategory category, Pageable pageable);

    Page<Voucher> findByFinancialYear(String financialYear, Pageable pageable);

    Page<Voucher> findByVoucherDateBetween(LocalDate startDate, LocalDate endDate, Pageable pageable);

    @Query("SELECT v FROM Voucher v WHERE v.financialYear = :fy AND v.voucherType = :type AND v.status = :status")
    Page<Voucher> findByFinancialYearAndTypeAndStatus(
            @Param("fy") String financialYear,
            @Param("type") VoucherType type,
            @Param("status") VoucherStatus status,
            Pageable pageable);

    @Query("SELECT v FROM Voucher v LEFT JOIN FETCH v.vendor WHERE " +
           "LOWER(v.voucherNumber) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(v.description) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<Voucher> searchVouchers(@Param("search") String search, Pageable pageable);

    @Query("SELECT v FROM Voucher v LEFT JOIN FETCH v.vendor WHERE " +
           "(LOWER(v.voucherNumber) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(v.description) LIKE LOWER(CONCAT('%', :search, '%'))) AND " +
           "(:type IS NULL OR v.voucherType = :type) AND " +
           "(:status IS NULL OR v.status = :status)")
    Page<Voucher> searchVouchersWithFilters(@Param("search") String search,
                                            @Param("type") VoucherType type,
                                            @Param("status") VoucherStatus status,
                                            Pageable pageable);

    @Query("SELECT v FROM Voucher v LEFT JOIN FETCH v.vendor WHERE " +
           "(:type IS NULL OR v.voucherType = :type) AND " +
           "(:status IS NULL OR v.status = :status) AND " +
           "(:category IS NULL OR v.category = :category) AND " +
           "(:financialYear IS NULL OR v.financialYear = :financialYear) AND " +
           "(:startDate IS NULL OR v.voucherDate >= :startDate) AND " +
           "(:endDate IS NULL OR v.voucherDate <= :endDate)")
    Page<Voucher> findWithFilters(@Param("type") VoucherType type,
                                   @Param("status") VoucherStatus status,
                                   @Param("category") ExpenseCategory category,
                                   @Param("financialYear") String financialYear,
                                   @Param("startDate") LocalDate startDate,
                                   @Param("endDate") LocalDate endDate,
                                   Pageable pageable);

    // Monthly expense summary
    @Query("SELECT v.category, SUM(v.amount) FROM Voucher v WHERE " +
           "v.voucherType = 'PAYMENT' AND v.status != 'CANCELLED' AND " +
           "v.voucherDate BETWEEN :startDate AND :endDate GROUP BY v.category")
    List<Object[]> getMonthlyCategoryWiseExpense(@Param("startDate") LocalDate startDate,
                                                 @Param("endDate") LocalDate endDate);

    // Vendor-wise payment summary
    @Query("SELECT v.vendor.vendorId, v.vendor.vendorName, SUM(v.amount) FROM Voucher v WHERE " +
           "v.voucherType = 'PAYMENT' AND v.status != 'CANCELLED' AND v.vendor IS NOT NULL AND " +
           "v.voucherDate BETWEEN :startDate AND :endDate GROUP BY v.vendor.vendorId, v.vendor.vendorName")
    List<Object[]> getVendorWisePaymentSummary(@Param("startDate") LocalDate startDate,
                                               @Param("endDate") LocalDate endDate);

    // Total by type and status in financial year
    @Query("SELECT SUM(v.amount) FROM Voucher v WHERE v.voucherType = :type AND " +
           "v.status != 'CANCELLED' AND v.financialYear = :fy")
    BigDecimal getTotalByTypeInFinancialYear(@Param("type") VoucherType type, @Param("fy") String fy);

    long countByStatus(VoucherStatus status);

    long countByFinancialYearAndVoucherType(String financialYear, VoucherType type);

    // Check for duplicate voucher
    @Query("SELECT v FROM Voucher v WHERE v.vendor.vendorId = :vendorId AND " +
           "v.amount = :amount AND v.voucherDate = :date AND v.status != 'CANCELLED'")
    List<Voucher> findPotentialDuplicates(@Param("vendorId") Long vendorId,
                                          @Param("amount") BigDecimal amount,
                                          @Param("date") LocalDate date);

    // Bulk fetch for PDF generation (ordered by date)
    @Query("SELECT v FROM Voucher v LEFT JOIN FETCH v.vendor WHERE v.financialYear = :fy ORDER BY v.voucherDate ASC, v.voucherNumber ASC")
    List<Voucher> findByFinancialYearOrderByVoucherDateAsc(@Param("fy") String financialYear);

    @Query("SELECT v FROM Voucher v LEFT JOIN FETCH v.vendor WHERE v.voucherDate BETWEEN :startDate AND :endDate ORDER BY v.voucherDate ASC, v.voucherNumber ASC")
    List<Voucher> findByVoucherDateBetweenOrderByVoucherDateAsc(@Param("startDate") LocalDate startDate,
                                                                 @Param("endDate") LocalDate endDate);

    // Vendor ledger - all vouchers for a specific vendor
    @Query("SELECT v FROM Voucher v WHERE v.vendor.vendorId = :vendorId AND v.status != 'CANCELLED' ORDER BY v.voucherDate ASC, v.voucherNumber ASC")
    List<Voucher> findByVendorIdOrderByDate(@Param("vendorId") Long vendorId);

    @Query("SELECT v FROM Voucher v WHERE v.vendor.vendorId = :vendorId AND v.status != 'CANCELLED' AND v.voucherDate BETWEEN :startDate AND :endDate ORDER BY v.voucherDate ASC, v.voucherNumber ASC")
    List<Voucher> findByVendorIdAndDateRange(@Param("vendorId") Long vendorId,
                                             @Param("startDate") LocalDate startDate,
                                             @Param("endDate") LocalDate endDate);

    @Query("SELECT SUM(v.amount) FROM Voucher v WHERE v.vendor.vendorId = :vendorId AND v.status != 'CANCELLED'")
    BigDecimal getTotalAmountByVendorId(@Param("vendorId") Long vendorId);
}
