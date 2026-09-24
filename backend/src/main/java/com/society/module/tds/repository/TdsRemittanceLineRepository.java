package com.society.module.tds.repository;

import com.society.module.tds.entity.TdsRemittanceLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Set;

/**
 * Repository for {@link TdsRemittanceLine} voucher links. The unique constraint on
 * {@code voucher_id} guarantees a voucher is remitted at most once; these lookups let the
 * {@code TdsRemittanceService} detect already-linked vouchers up front so batch creation can
 * reject them with a clear message rather than relying solely on the constraint violation.
 */
@Repository
public interface TdsRemittanceLineRepository extends JpaRepository<TdsRemittanceLine, Long> {

    /** True if the given voucher is already attached to any remittance batch. */
    boolean existsByVoucher_VoucherId(Long voucherId);

    /**
     * Returns the subset of the supplied voucher ids that are already attached to a remittance
     * line. Projects only the voucher id so the check stays cheap for large selections.
     */
    @Query("SELECT l.voucher.voucherId FROM TdsRemittanceLine l WHERE l.voucher.voucherId IN :voucherIds")
    Set<Long> findLinkedVoucherIds(@Param("voucherIds") Collection<Long> voucherIds);
}
