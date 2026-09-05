package com.society.module.transaction.mapper;

import com.society.module.maintenance.entity.MaintenancePayment;
import com.society.module.maintenance.entity.MaintenancePayment.PaymentMode;
import com.society.module.maintenance.entity.MaintenancePayment.PaymentStatus;
import com.society.module.owner.entity.Unit;
import com.society.module.transaction.dto.TransactionDetailDTO;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property test for {@link TransactionMapper#toDetail}.
 *
 * <p><b>Property 16: Detail mapping is complete and preserves nulls.</b> For every
 * generated {@link MaintenancePayment} - including cases where optional fields such
 * as the verification fields ({@code verifiedOn}, {@code verifiedBy}) and reversal
 * fields ({@code reversedOn}, {@code reversedBy}, {@code reversalReason}) are
 * {@code null} - mapping to {@link TransactionDetailDTO} produces a DTO in which
 * every detail field equals the corresponding source value, and every {@code null}
 * source value is preserved as {@code null} (never dropped or defaulted). Enum
 * fields are exposed as their {@code String} name (null-guarded).</p>
 *
 * <p><b>Validates: Requirements 8.1, 8.3, 8.4</b></p>
 */
class TransactionDetailMapperPropertyTest {

    @Property(tries = 100)
    void detailMappingIsCompleteAndPreservesNulls(@ForAll("payments") MaintenancePayment payment) {
        TransactionDetailDTO dto = TransactionMapper.toDetail(payment);

        assertThat(dto).isNotNull();

        // Summary-superset fields.
        assertThat(dto.getPaymentId()).isEqualTo(payment.getPaymentId());
        assertThat(dto.getUnitNumber())
                .isEqualTo(payment.getUnit() != null ? payment.getUnit().getUnitNumber() : null);
        assertThat(dto.getPayerName()).isEqualTo(payment.getPayerName());
        assertThat(dto.getPayerType()).isEqualTo(payment.getPayerType());
        assertThat(dto.getAmount()).isEqualTo(payment.getAmount());
        assertThat(dto.getPaymentDate()).isEqualTo(payment.getPaymentDate());
        assertThat(dto.getPaymentMode())
                .isEqualTo(payment.getPaymentMode() != null ? payment.getPaymentMode().name() : null);
        assertThat(dto.getStatus())
                .isEqualTo(payment.getStatus() != null ? payment.getStatus().name() : null);
        assertThat(dto.getTransactionId()).isEqualTo(payment.getTransactionId());
        assertThat(dto.getReceiptNumber()).isEqualTo(payment.getReceiptNumber());

        // Detail-only fields (amounts / discount).
        assertThat(dto.getOriginalAmount()).isEqualTo(payment.getOriginalAmount());
        assertThat(dto.getDiscountAmount()).isEqualTo(payment.getDiscountAmount());
        assertThat(dto.getDiscountPercent()).isEqualTo(payment.getDiscountPercent());
        assertThat(dto.getRemarks()).isEqualTo(payment.getRemarks());

        // Verification fields - must be preserved verbatim, including null.
        assertThat(dto.getVerifiedOn()).isEqualTo(payment.getVerifiedOn());
        assertThat(dto.getVerifiedBy()).isEqualTo(payment.getVerifiedBy());

        // Reversal fields - must be preserved verbatim, including null.
        assertThat(dto.getReversedOn()).isEqualTo(payment.getReversedOn());
        assertThat(dto.getReversedBy()).isEqualTo(payment.getReversedBy());
        assertThat(dto.getReversalReason()).isEqualTo(payment.getReversalReason());

        // Explicit null-preservation checks: a null source field yields a null DTO
        // field (not defaulted or dropped).
        if (payment.getOriginalAmount() == null) {
            assertThat(dto.getOriginalAmount()).isNull();
        }
        if (payment.getDiscountAmount() == null) {
            assertThat(dto.getDiscountAmount()).isNull();
        }
        if (payment.getDiscountPercent() == null) {
            assertThat(dto.getDiscountPercent()).isNull();
        }
        if (payment.getRemarks() == null) {
            assertThat(dto.getRemarks()).isNull();
        }
        if (payment.getVerifiedOn() == null) {
            assertThat(dto.getVerifiedOn()).isNull();
        }
        if (payment.getVerifiedBy() == null) {
            assertThat(dto.getVerifiedBy()).isNull();
        }
        if (payment.getReversedOn() == null) {
            assertThat(dto.getReversedOn()).isNull();
        }
        if (payment.getReversedBy() == null) {
            assertThat(dto.getReversedBy()).isNull();
        }
        if (payment.getReversalReason() == null) {
            assertThat(dto.getReversalReason()).isNull();
        }
    }

    @Provide
    Arbitrary<MaintenancePayment> payments() {
        Arbitrary<Long> paymentIds = Arbitraries.longs().between(1L, 1_000_000L);
        Arbitrary<Unit> units = units();
        Arbitrary<String> payerNames =
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(150).injectNull(0.1);
        Arbitrary<String> payerTypes = Arbitraries.of("OWNER", "TENANT");
        Arbitrary<BigDecimal> amounts = Arbitraries.longs().between(0L, 10_000_000L)
                .map(cents -> BigDecimal.valueOf(cents, 2));
        Arbitrary<LocalDate> paymentDates = Arbitraries.integers().between(0, 3650)
                .map(offset -> LocalDate.of(2020, 1, 1).plusDays(offset));

        return Combinators.combine(paymentIds, units, payerNames, payerTypes, amounts, paymentDates)
                .as(TransactionDetailMapperPropertyTest::baseBuilder)
                .flatMap(this::withOptionalFields);
    }

    private static MaintenancePayment.MaintenancePaymentBuilder baseBuilder(
            Long paymentId, Unit unit, String payerName, String payerType,
            BigDecimal amount, LocalDate paymentDate) {
        return MaintenancePayment.builder()
                .paymentId(paymentId)
                .unit(unit)
                .payerName(payerName)
                .payerType(payerType)
                .amount(amount)
                .paymentDate(paymentDate);
    }

    private Arbitrary<MaintenancePayment> withOptionalFields(
            MaintenancePayment.MaintenancePaymentBuilder builder) {
        Arbitrary<PaymentMode> paymentModes = Arbitraries.of(PaymentMode.class).injectNull(0.1);
        Arbitrary<PaymentStatus> statuses = Arbitraries.of(PaymentStatus.class).injectNull(0.1);
        Arbitrary<String> transactionIds =
                Arbitraries.strings().alpha().numeric().ofMinLength(1).ofMaxLength(100).injectNull(0.3);
        Arbitrary<String> receiptNumbers =
                Arbitraries.strings().alpha().numeric().ofMinLength(1).ofMaxLength(50).injectNull(0.3);

        Arbitrary<BigDecimal> originalAmounts = optionalMoney();
        Arbitrary<BigDecimal> discountAmounts = optionalMoney();
        Arbitrary<BigDecimal> discountPercents = Arbitraries.longs().between(0L, 10_000L)
                .map(v -> BigDecimal.valueOf(v, 2)).injectNull(0.4);
        Arbitrary<String> remarks =
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(500).injectNull(0.4);

        // Verification fields - frequently null to exercise null preservation.
        Arbitrary<LocalDateTime> verifiedOns = optionalTimestamps();
        Arbitrary<String> verifiedBys =
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(100).injectNull(0.5);

        // Reversal fields - frequently null to exercise null preservation.
        Arbitrary<LocalDateTime> reversedOns = optionalTimestamps();
        Arbitrary<String> reversedBys =
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(100).injectNull(0.5);
        Arbitrary<String> reversalReasons =
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(500).injectNull(0.5);

        Arbitrary<MaintenancePayment.MaintenancePaymentBuilder> withCore =
                Combinators.combine(paymentModes, statuses, transactionIds, receiptNumbers)
                        .as((mode, status, txnId, receipt) -> builder
                                .paymentMode(mode)
                                .status(status)
                                .transactionId(txnId)
                                .receiptNumber(receipt));

        Arbitrary<MaintenancePayment.MaintenancePaymentBuilder> withAmounts =
                withCore.flatMap(b -> Combinators.combine(originalAmounts, discountAmounts, discountPercents, remarks)
                        .as((orig, disc, pct, rem) -> b
                                .originalAmount(orig)
                                .discountAmount(disc)
                                .discountPercent(pct)
                                .remarks(rem)));

        Arbitrary<MaintenancePayment.MaintenancePaymentBuilder> withVerification =
                withAmounts.flatMap(b -> Combinators.combine(verifiedOns, verifiedBys)
                        .as((on, by) -> b.verifiedOn(on).verifiedBy(by)));

        return withVerification.flatMap(b -> Combinators.combine(reversedOns, reversedBys, reversalReasons)
                .as((on, by, reason) -> b
                        .reversedOn(on)
                        .reversedBy(by)
                        .reversalReason(reason)
                        .build()));
    }

    private Arbitrary<BigDecimal> optionalMoney() {
        return Arbitraries.longs().between(0L, 10_000_000L)
                .map(cents -> BigDecimal.valueOf(cents, 2))
                .injectNull(0.4);
    }

    private Arbitrary<LocalDateTime> optionalTimestamps() {
        return Arbitraries.integers().between(0, 3650)
                .map(offset -> LocalDateTime.of(2020, 1, 1, 9, 30).plusDays(offset))
                .injectNull(0.5);
    }

    private Arbitrary<Unit> units() {
        Arbitrary<Unit> withNumber = Arbitraries.strings()
                .alpha().numeric().ofMinLength(1).ofMaxLength(20)
                .map(number -> Unit.builder().unitNumber(number).build());
        // Include a fraction of null units to exercise the mapper's null-guard.
        return withNumber.injectNull(0.1);
    }
}
