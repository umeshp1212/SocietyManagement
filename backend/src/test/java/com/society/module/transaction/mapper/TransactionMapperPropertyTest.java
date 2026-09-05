package com.society.module.transaction.mapper;

import com.society.module.maintenance.entity.MaintenancePayment;
import com.society.module.maintenance.entity.MaintenancePayment.PaymentMode;
import com.society.module.maintenance.entity.MaintenancePayment.PaymentStatus;
import com.society.module.owner.entity.Unit;
import com.society.module.transaction.dto.TransactionSummaryDTO;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.WithNull;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property test for {@link TransactionMapper#toSummary}.
 *
 * <p><b>Property 15: Summary mapping is complete.</b> For every generated
 * {@link MaintenancePayment}, mapping to {@link TransactionSummaryDTO} produces a
 * DTO in which every summary field equals the corresponding source value:
 * {@code paymentId}, {@code unit.unitNumber -> unitNumber}, {@code payerName},
 * {@code payerType}, {@code amount}, {@code paymentDate},
 * {@code paymentMode.name() -> paymentMode}, {@code status.name() -> status},
 * {@code transactionId}, {@code receiptNumber}.</p>
 *
 * <p><b>Validates: Requirements 1.2</b></p>
 */
class TransactionMapperPropertyTest {

    @Property(tries = 100)
    void summaryMappingIsComplete(@ForAll("payments") MaintenancePayment payment) {
        TransactionSummaryDTO dto = TransactionMapper.toSummary(payment);

        assertThat(dto).isNotNull();
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
    }

    @Provide
    Arbitrary<MaintenancePayment> payments() {
        Arbitrary<Long> paymentIds = Arbitraries.longs().between(1L, 1_000_000L);
        Arbitrary<Unit> units = units();
        Arbitrary<String> payerNames = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(150).injectNull(0.1);
        Arbitrary<String> payerTypes = Arbitraries.of("OWNER", "TENANT");
        Arbitrary<BigDecimal> amounts = Arbitraries.longs().between(0L, 10_000_000L)
                .map(cents -> BigDecimal.valueOf(cents, 2));
        Arbitrary<LocalDate> paymentDates = Arbitraries.integers().between(0, 3650)
                .map(offset -> LocalDate.of(2020, 1, 1).plusDays(offset));
        Arbitrary<PaymentMode> paymentModes = Arbitraries.of(PaymentMode.class).injectNull(0.1);
        Arbitrary<PaymentStatus> statuses = Arbitraries.of(PaymentStatus.class).injectNull(0.1);
        Arbitrary<String> transactionIds =
                Arbitraries.strings().alpha().numeric().ofMinLength(1).ofMaxLength(100).injectNull(0.2);
        Arbitrary<String> receiptNumbers =
                Arbitraries.strings().alpha().numeric().ofMinLength(1).ofMaxLength(50).injectNull(0.2);

        return Combinators.combine(paymentIds, units, payerNames, payerTypes, amounts, paymentDates)
                .as(TransactionMapperPropertyTest::baseBuilder)
                .flatMap(builder -> Combinators.combine(paymentModes, statuses, transactionIds, receiptNumbers)
                        .as((mode, status, txnId, receipt) -> builder
                                .paymentMode(mode)
                                .status(status)
                                .transactionId(txnId)
                                .receiptNumber(receipt)
                                .build()));
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

    private Arbitrary<Unit> units() {
        Arbitrary<Unit> withNumber = Arbitraries.strings()
                .alpha().numeric().ofMinLength(1).ofMaxLength(20)
                .map(number -> Unit.builder().unitNumber(number).build());
        // Include a fraction of null units to exercise the mapper's null-guard.
        return withNumber.injectNull(0.1);
    }
}
