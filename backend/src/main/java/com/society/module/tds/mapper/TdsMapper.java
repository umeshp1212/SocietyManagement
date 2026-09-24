package com.society.module.tds.mapper;

import com.society.module.tds.dto.TdsLineDTO;
import com.society.module.tds.dto.TdsRemittanceDTO;
import com.society.module.tds.entity.TdsLineView;
import com.society.module.tds.entity.TdsRemittance;
import com.society.module.tds.entity.TdsRemittanceLine;
import com.society.module.vendor.entity.Vendor;
import com.society.module.voucher.entity.Voucher;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Maps the TDS feature's source entities to its response DTOs.
 *
 * <p>Mapping is null-guarded and null-preserving: mapping a {@code null} source yields
 * {@code null}, and individual fields keep their {@code null} values rather than being dropped
 * so the frontend can render explicit placeholders. Nested accessors (voucher &rarr; vendor,
 * line &rarr; parent batch) are null-guarded before dereferencing.</p>
 */
public final class TdsMapper {

    private TdsMapper() {
        // utility class
    }

    /**
     * Maps a {@link TdsLineView} projection to a {@link TdsLineDTO} (see Requirements 1.4, 1.5,
     * 1.6, 1.7). The view already COALESCEs a missing batch status to
     * {@code DEDUCTED}, so {@code remittanceStatus} is mapped directly to {@code status}.
     */
    public static TdsLineDTO toLineDTO(TdsLineView view) {
        if (view == null) {
            return null;
        }
        return TdsLineDTO.builder()
                .voucherId(view.getVoucherId())
                .voucherNumber(view.getVoucherNumber())
                .deductionDate(view.getDeductionDate())
                .financialYear(view.getFinancialYear())
                .vendorId(view.getVendorId())
                .vendorName(view.getVendorName())
                .tdsSection(view.getTdsSection())
                .tdsRate(view.getTdsRate())
                .tdsAmount(view.getTdsAmount())
                .status(view.getRemittanceStatus())
                .remittanceId(view.getRemittanceId())
                .challanNumber(view.getChallanNumber())
                .paidToAccountantDate(view.getPaidToAccountantDate())
                .paidToItDate(view.getPaidToItDate())
                .build();
    }

    /**
     * Maps a persisted {@link TdsRemittanceLine} to a {@link TdsLineDTO} for inclusion in a
     * {@link TdsRemittanceDTO} (see Requirement 8.2). Voucher fields are read from the linked
     * {@link Voucher}, the deducted amount is the line's snapshot {@code tdsAmount}, and the
     * remittance status/dates/challan come from the parent {@link TdsRemittance} batch.
     */
    public static TdsLineDTO toLineDTO(TdsRemittanceLine line) {
        if (line == null) {
            return null;
        }
        Voucher voucher = line.getVoucher();
        Vendor vendor = voucher != null ? voucher.getVendor() : null;
        TdsRemittance batch = line.getRemittance();
        return TdsLineDTO.builder()
                .voucherId(voucher != null ? voucher.getVoucherId() : null)
                .voucherNumber(voucher != null ? voucher.getVoucherNumber() : null)
                .deductionDate(voucher != null ? voucher.getVoucherDate() : null)
                .financialYear(voucher != null ? voucher.getFinancialYear() : null)
                .vendorId(vendor != null ? vendor.getVendorId() : null)
                .vendorName(vendor != null ? vendor.getVendorName() : null)
                .tdsSection(voucher != null ? voucher.getTdsSection() : null)
                .tdsRate(voucher != null ? voucher.getTdsRate() : null)
                .tdsAmount(line.getTdsAmount())
                .status(batch != null ? batch.getStatus() : null)
                .remittanceId(batch != null ? batch.getTdsRemittanceId() : null)
                .challanNumber(batch != null ? batch.getChallanNumber() : null)
                .paidToAccountantDate(batch != null ? batch.getPaidToAccountantDate() : null)
                .paidToItDate(batch != null ? batch.getPaidToItDate() : null)
                .build();
    }

    /**
     * Maps a {@link TdsRemittance} batch to a {@link TdsRemittanceDTO} together with its lines
     * (see Requirements 8.1, 8.2, 8.3). A batch with no lines yields an empty (never
     * {@code null}) line collection.
     */
    public static TdsRemittanceDTO toRemittanceDTO(TdsRemittance batch) {
        if (batch == null) {
            return null;
        }
        List<TdsLineDTO> lines = batch.getLines() == null
                ? Collections.emptyList()
                : batch.getLines().stream()
                        .map(TdsMapper::toLineDTO)
                        .collect(Collectors.toList());
        return TdsRemittanceDTO.builder()
                .remittanceId(batch.getTdsRemittanceId())
                .batchReference(batch.getBatchReference())
                .status(batch.getStatus())
                .financialYear(batch.getFinancialYear())
                .totalAmount(batch.getTotalAmount())
                .accountantName(batch.getAccountantName())
                .paidToAccountantDate(batch.getPaidToAccountantDate())
                .accountantReference(batch.getAccountantReference())
                .challanNumber(batch.getChallanNumber())
                .paidToItDate(batch.getPaidToItDate())
                .remarks(batch.getRemarks())
                .lines(lines)
                .build();
    }
}
