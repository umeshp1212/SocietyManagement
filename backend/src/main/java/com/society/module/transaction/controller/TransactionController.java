package com.society.module.transaction.controller;

import com.society.common.ApiResponse;
import com.society.common.PagedResponse;
import com.society.exception.BusinessException;
import com.society.module.maintenance.entity.MaintenancePayment;
import com.society.module.transaction.dto.TransactionDetailDTO;
import com.society.module.transaction.dto.TransactionFilterRequest;
import com.society.module.transaction.dto.TransactionSummaryDTO;
import com.society.module.transaction.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only REST endpoints for the Transaction Page.
 *
 * <p>Both endpoints are gated by method security
 * ({@code hasRole('SUPER_ADMIN') or hasAuthority('TRANSACTION_VIEW')}); the
 * service decides society-wide vs. member data scope. Enum-valued query
 * parameters (payment mode, status) are bound as raw strings and parsed here so
 * an unrecognized value surfaces as a {@link BusinessException} naming the
 * offending value rather than a raw framework bind failure.
 */
@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    /**
     * Returns a paginated, filtered, access-scoped list of transactions.
     *
     * <p>{@code status} is repeatable ({@code ?status=SUCCESS&status=VERIFIED})
     * to support multi-select OR semantics. {@code page} is zero-based;
     * {@code size} is optional and resolved to a role default then clamped in the
     * service.
     */
    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('TRANSACTION_VIEW')")
    public ResponseEntity<ApiResponse<PagedResponse<TransactionSummaryDTO>>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String paymentMode,
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false) String payerType,
            @RequestParam(required = false) Long unitId,
            @RequestParam(required = false) String unitSearch,
            @RequestParam(required = false) String reference,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size,
            Authentication authentication) {

        TransactionFilterRequest filter = new TransactionFilterRequest();
        filter.setStartDate(startDate);
        filter.setEndDate(endDate);
        filter.setPaymentMode(parsePaymentMode(paymentMode));
        filter.setStatuses(parseStatuses(status));
        filter.setPayerType(payerType);
        filter.setUnitId(unitId);
        filter.setUnitSearch(unitSearch);
        filter.setReference(reference);

        PagedResponse<TransactionSummaryDTO> result =
                transactionService.listTransactions(filter, page, size, authentication);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * Returns the full detail of a single transaction, scope-checked in the
     * service (out-of-scope for a member yields a 403; unknown id yields a 404).
     */
    @GetMapping("/{paymentId}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('TRANSACTION_VIEW')")
    public ResponseEntity<ApiResponse<TransactionDetailDTO>> detail(
            @PathVariable Long paymentId, Authentication authentication) {
        TransactionDetailDTO detail = transactionService.getTransaction(paymentId, authentication);
        return ResponseEntity.ok(ApiResponse.success(detail));
    }

    /**
     * Translates a raw payment-mode value into the enum, naming the offending
     * value on failure (Req 4.3). Blank/absent values contribute no filter.
     */
    private MaintenancePayment.PaymentMode parsePaymentMode(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            return MaintenancePayment.PaymentMode.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("Invalid payment mode: '" + raw + "'");
        }
    }

    /**
     * Translates the repeatable raw status values into enums, naming the first
     * offending value on failure (Req 5.4). Blank entries are skipped; an
     * absent/empty parameter contributes no filter.
     */
    private List<MaintenancePayment.PaymentStatus> parseStatuses(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        List<MaintenancePayment.PaymentStatus> statuses = new ArrayList<>();
        for (String value : raw) {
            if (value == null || value.trim().isEmpty()) {
                continue;
            }
            try {
                statuses.add(MaintenancePayment.PaymentStatus.valueOf(value.trim().toUpperCase()));
            } catch (IllegalArgumentException ex) {
                throw new BusinessException("Invalid status: '" + value + "'");
            }
        }
        return statuses.isEmpty() ? null : statuses;
    }
}
