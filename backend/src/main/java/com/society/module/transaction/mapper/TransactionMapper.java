package com.society.module.transaction.mapper;

import com.society.module.maintenance.entity.MaintenancePayment;
import com.society.module.owner.entity.Unit;
import com.society.module.transaction.dto.TransactionDetailDTO;
import com.society.module.transaction.dto.TransactionSummaryDTO;

/**
 * Maps the read-only {@link MaintenancePayment} source entity to the transaction
 * feature's response DTOs.
 *
 * <p>Mapping is intentionally lossless: the detail mapping preserves {@code null}
 * values rather than dropping fields so the frontend can render explicit
 * placeholders. Enum fields are exposed as their {@code String} name and are
 * null-guarded (only {@code .name()} when the enum value is present).</p>
 */
public final class TransactionMapper {

    private TransactionMapper() {
        // utility class
    }

    /**
     * Maps a payment to the lean list-row DTO (see Requirement 1.2).
     */
    public static TransactionSummaryDTO toSummary(MaintenancePayment payment) {
        if (payment == null) {
            return null;
        }
        return TransactionSummaryDTO.builder()
                .paymentId(payment.getPaymentId())
                .unitNumber(unitNumber(payment.getUnit()))
                .payerName(payment.getPayerName())
                .payerType(payment.getPayerType())
                .amount(payment.getAmount())
                .paymentDate(payment.getPaymentDate())
                .paymentMode(enumName(payment.getPaymentMode()))
                .status(enumName(payment.getStatus()))
                .transactionId(payment.getTransactionId())
                .receiptNumber(payment.getReceiptNumber())
                .build();
    }

    /**
     * Maps a payment to the full detail DTO (see Requirements 8.1, 8.3, 8.4).
     * Null values are preserved for every field.
     */
    public static TransactionDetailDTO toDetail(MaintenancePayment payment) {
        if (payment == null) {
            return null;
        }
        return TransactionDetailDTO.builder()
                .paymentId(payment.getPaymentId())
                .unitNumber(unitNumber(payment.getUnit()))
                .payerName(payment.getPayerName())
                .payerType(payment.getPayerType())
                .amount(payment.getAmount())
                .originalAmount(payment.getOriginalAmount())
                .discountAmount(payment.getDiscountAmount())
                .discountPercent(payment.getDiscountPercent())
                .paymentDate(payment.getPaymentDate())
                .paymentMode(enumName(payment.getPaymentMode()))
                .transactionId(payment.getTransactionId())
                .receiptNumber(payment.getReceiptNumber())
                .status(enumName(payment.getStatus()))
                .remarks(payment.getRemarks())
                .verifiedOn(payment.getVerifiedOn())
                .verifiedBy(payment.getVerifiedBy())
                .reversedOn(payment.getReversedOn())
                .reversedBy(payment.getReversedBy())
                .reversalReason(payment.getReversalReason())
                .build();
    }

    private static String unitNumber(Unit unit) {
        return unit != null ? unit.getUnitNumber() : null;
    }

    private static String enumName(Enum<?> value) {
        return value != null ? value.name() : null;
    }
}
