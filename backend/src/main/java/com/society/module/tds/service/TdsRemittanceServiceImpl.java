package com.society.module.tds.service;

import com.society.common.PagedResponse;
import com.society.enums.VoucherStatus;
import com.society.enums.TdsRemittanceStatus;
import com.society.exception.BusinessException;
import com.society.exception.ResourceNotFoundException;
import com.society.module.tds.dto.CreateRemittanceRequest;
import com.society.module.tds.dto.RecordItRemittanceRequest;
import com.society.module.tds.dto.TdsFilterRequest;
import com.society.module.tds.dto.TdsRemittanceDTO;
import com.society.module.tds.entity.TdsRemittance;
import com.society.module.tds.entity.TdsRemittanceLine;
import com.society.module.tds.mapper.TdsMapper;
import com.society.module.tds.repository.TdsRemittanceLineRepository;
import com.society.module.tds.repository.TdsRemittanceRepository;
import com.society.module.tds.specification.TdsSpecificationBuilder;
import com.society.module.voucher.entity.Voucher;
import com.society.module.voucher.repository.VoucherRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Default {@link TdsRemittanceService} implementation.
 *
 * <p>Covers the full lifecycle write model: {@link #createBatch(CreateRemittanceRequest)} (stage 1,
 * task 8.1), {@link #recordItRemittance(Long, RecordItRemittanceRequest)} (stage 2, forward-only,
 * task 8.2), and the {@link #getBatch(Long)} / {@link #listBatches(TdsFilterRequest, Pageable)}
 * read operations (task 8.3).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TdsRemittanceServiceImpl implements TdsRemittanceService {

    /** Minimum number of vouchers a batch may group (Req 5.3). */
    private static final int MIN_VOUCHERS = 1;

    /** Maximum number of vouchers a batch may group (Req 5.7). */
    private static final int MAX_VOUCHERS = 500;

    /** Maximum accountant-name length (Req 5.7). */
    private static final int MAX_ACCOUNTANT_NAME_LENGTH = 100;

    /** Scale applied to the batch total (Req 5.9). */
    private static final int MONEY_SCALE = 2;

    /** Zero-padded width of the numeric suffix in a generated batch reference. */
    private static final int BATCH_SEQUENCE_WIDTH = 4;

    /** Retry budget for generating a unique batch reference. */
    private static final int BATCH_REFERENCE_MAX_ATTEMPTS = 20;

    /** Maximum challan-number length (Req 6.3). */
    private static final int MAX_CHALLAN_NUMBER_LENGTH = 50;

    /** Default page number when listing batches with no/unpaged pageable (Req 8.6). */
    private static final int DEFAULT_PAGE_NUMBER = 0;

    /** Default page size when listing batches with no/unpaged pageable (Req 8.6). */
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final TdsRemittanceRepository remittanceRepository;
    private final TdsRemittanceLineRepository remittanceLineRepository;
    private final VoucherRepository voucherRepository;
    private final TdsSpecificationBuilder specificationBuilder;

    /**
     * {@inheritDoc}
     *
     * <p>Validation runs fully before anything is persisted so a rejected request leaves stored
     * data untouched (Req 5.3-5.8). Order of operations:
     * <ol>
     *   <li>voucher-id list present, deduplicated, and within 1-500 (Req 5.3, 5.7);</li>
     *   <li>accountant name 1-100 chars (Req 5.7);</li>
     *   <li>paid-to-accountant date present and not in the future (Req 5.8);</li>
     *   <li>every voucher resolves and is an eligible Source_Voucher — FINAL, tdsApplicable,
     *       tdsAmount &gt; 0, references a vendor (Req 5.4);</li>
     *   <li>no voucher already belongs to a remittance line (Req 5.5);</li>
     *   <li>all vouchers share a single financial year (Req 5.6).</li>
     * </ol>
     *
     * <p>On success a batch is created in {@code PAID_TO_ACCOUNTANT} with one line per voucher
     * snapshotting the voucher {@code tdsAmount}; the batch total is the HALF_UP scale-2 sum of the
     * line amounts (Req 5.1, 5.2, 5.9), the financial year is derived from the linked vouchers
     * (Req 5.10), the accountant name/date/reference and the recording actor are stored
     * (Req 5.11), and a unique batch reference is assigned (Req 5.12).
     */
    @Override
    @Transactional
    public TdsRemittanceDTO createBatch(CreateRemittanceRequest request) {
        if (request == null) {
            throw new BusinessException("A batch-creation request is required");
        }

        // 1. Voucher id list: non-empty, deduplicated, within 1-500 (Req 5.3, 5.7).
        List<Long> rawIds = request.getVoucherIds();
        if (rawIds == null || rawIds.isEmpty()) {
            throw new BusinessException("At least one voucher is required to create a remittance batch");
        }
        if (rawIds.stream().anyMatch(id -> id == null)) {
            throw new BusinessException("Voucher ids must not be null");
        }
        // Preserve request order while removing duplicate ids.
        List<Long> voucherIds = new ArrayList<>(new LinkedHashSet<>(rawIds));
        if (voucherIds.size() > MAX_VOUCHERS) {
            throw new BusinessException("Too many vouchers: a batch may contain at most "
                    + MAX_VOUCHERS + " vouchers, but " + voucherIds.size() + " were supplied");
        }

        // 2. Accountant name 1-100 chars (Req 5.7).
        String accountantName = request.getAccountantName() != null ? request.getAccountantName().trim() : null;
        if (!StringUtils.hasText(accountantName)) {
            throw new BusinessException("Accountant name is required");
        }
        if (accountantName.length() > MAX_ACCOUNTANT_NAME_LENGTH) {
            throw new BusinessException("Accountant name exceeds maximum length of "
                    + MAX_ACCOUNTANT_NAME_LENGTH + " characters");
        }

        // 3. Paid-to-accountant date present and not in the future (Req 5.8).
        LocalDate paidToAccountantDate = request.getPaidToAccountantDate();
        if (paidToAccountantDate == null) {
            throw new BusinessException("Paid-to-accountant date is required");
        }
        if (paidToAccountantDate.isAfter(LocalDate.now())) {
            throw new BusinessException("Paid-to-accountant date must not be in the future");
        }

        // 4. Resolve vouchers and validate eligibility (Req 5.4).
        List<Voucher> vouchers = voucherRepository.findAllById(voucherIds);
        Map<Long, Voucher> byId = vouchers.stream()
                .collect(Collectors.toMap(Voucher::getVoucherId, Function.identity()));

        List<Long> missingIds = voucherIds.stream()
                .filter(id -> !byId.containsKey(id))
                .collect(Collectors.toList());
        if (!missingIds.isEmpty()) {
            throw new BusinessException("The following voucher ids are not eligible deducted-TDS "
                    + "vouchers (not found): " + missingIds);
        }

        List<Long> ineligibleIds = voucherIds.stream()
                .filter(id -> !isEligibleSourceVoucher(byId.get(id)))
                .collect(Collectors.toList());
        if (!ineligibleIds.isEmpty()) {
            throw new BusinessException("The following voucher ids are not eligible deducted-TDS "
                    + "vouchers (must be FINAL, TDS-applicable with a positive TDS amount, and "
                    + "reference a vendor): " + ineligibleIds);
        }

        // 5. None already attached to a remittance line (Req 5.5, 7.6).
        Set<Long> alreadyLinked = remittanceLineRepository.findLinkedVoucherIds(voucherIds);
        if (!alreadyLinked.isEmpty()) {
            throw new BusinessException("The following voucher ids are already remitted (already "
                    + "belong to a batch): " + new ArrayList<>(alreadyLinked));
        }

        // 6. All vouchers share a single financial year (Req 5.6).
        Set<String> financialYears = voucherIds.stream()
                .map(id -> byId.get(id).getFinancialYear())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (financialYears.size() > 1) {
            throw new BusinessException("Vouchers span multiple financial years " + financialYears
                    + "; a batch must belong to a single financial year");
        }
        String financialYear = financialYears.iterator().next();

        // ----- Build the batch (Req 5.1, 5.2, 5.9, 5.10, 5.11, 5.12) -----
        String actor = currentUser();

        TdsRemittance batch = TdsRemittance.builder()
                .batchReference(generateBatchReference(financialYear))
                .status(TdsRemittanceStatus.PAID_TO_ACCOUNTANT)
                .financialYear(financialYear)
                .accountantName(accountantName)
                .paidToAccountantDate(paidToAccountantDate)
                .accountantReference(StringUtils.hasText(request.getAccountantReference())
                        ? request.getAccountantReference().trim() : null)
                .paidToAccountantBy(actor)
                .remarks(StringUtils.hasText(request.getRemarks()) ? request.getRemarks().trim() : null)
                .build();

        BigDecimal total = BigDecimal.ZERO;
        List<TdsRemittanceLine> lines = new ArrayList<>(voucherIds.size());
        for (Long id : voucherIds) {
            Voucher voucher = byId.get(id);
            BigDecimal tdsAmount = voucher.getTdsAmount();
            TdsRemittanceLine line = TdsRemittanceLine.builder()
                    .remittance(batch)
                    .voucher(voucher)
                    .tdsAmount(tdsAmount)
                    .build();
            lines.add(line);
            total = total.add(tdsAmount);
        }
        batch.setLines(lines);
        batch.setTotalAmount(total.setScale(MONEY_SCALE, RoundingMode.HALF_UP));

        TdsRemittance saved = remittanceRepository.save(batch);
        log.info("Created TDS remittance batch {} ({} lines, total {}, FY {}) by {}",
                saved.getBatchReference(), lines.size(), saved.getTotalAmount(), financialYear, actor);

        return TdsMapper.toRemittanceDTO(saved);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Stage 2 is forward-only (Req 7.1). The batch must exist (Req 6.6) and currently be in
     * {@code PAID_TO_ACCOUNTANT}; the forward-only {@link #assertForwardTransition(TdsRemittanceStatus,
     * TdsRemittanceStatus)} guard rejects a batch already in {@code PAID_TO_IT_DEPARTMENT}
     * (backward/same) or any other status (stage skip) and leaves it unchanged (Req 6.5, 7.2, 7.3,
     * 7.4). All challan/date validation runs BEFORE the entity is mutated so a rejected request
     * leaves stored data untouched (Req 6.2, 6.3, 6.4):
     * <ol>
     *   <li>challan number present and 1-50 chars (Req 6.2, 6.3);</li>
     *   <li>paid-to-IT date present (Req 6.3) and not in the future (Req 6.4).</li>
     * </ol>
     *
     * <p>On success the batch advances to {@code PAID_TO_IT_DEPARTMENT} recording the challan
     * number, paid-to-IT date, the acting user, and optional remarks (Req 6.1).
     */
    @Override
    @Transactional
    public TdsRemittanceDTO recordItRemittance(Long remittanceId, RecordItRemittanceRequest request) {
        if (request == null) {
            throw new BusinessException("An IT-remittance request is required");
        }

        // 1. Batch must exist (Req 6.6).
        TdsRemittance batch = remittanceRepository.findById(remittanceId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "TDS remittance batch not found with id: '" + remittanceId + "'"));

        // 2. Forward-only guard: current status must be PAID_TO_ACCOUNTANT so the only legal
        //    target is PAID_TO_IT_DEPARTMENT (Req 6.5, 7.2, 7.3, 7.4). Reject wrong-status
        //    batches before any mutation so state is left unchanged.
        if (batch.getStatus() != TdsRemittanceStatus.PAID_TO_ACCOUNTANT) {
            throw new BusinessException("Batch " + batch.getBatchReference() + " is not in "
                    + TdsRemittanceStatus.PAID_TO_ACCOUNTANT + " status (current status: "
                    + batch.getStatus() + "); IT remittance can only be recorded on a batch that "
                    + "has been paid to the accountant");
        }
        assertForwardTransition(batch.getStatus(), TdsRemittanceStatus.PAID_TO_IT_DEPARTMENT);

        // 3. Validate challan number 1-50 chars (Req 6.2, 6.3), BEFORE mutating the entity.
        String challanNumber = request.getChallanNumber() != null ? request.getChallanNumber().trim() : null;
        if (!StringUtils.hasText(challanNumber)) {
            throw new BusinessException("Challan number is required");
        }
        if (challanNumber.length() > MAX_CHALLAN_NUMBER_LENGTH) {
            throw new BusinessException("Challan number exceeds maximum length of "
                    + MAX_CHALLAN_NUMBER_LENGTH + " characters");
        }

        // 4. Validate paid-to-IT date present (Req 6.3) and not in the future (Req 6.4).
        LocalDate paidToItDate = request.getPaidToItDate();
        if (paidToItDate == null) {
            throw new BusinessException("Paid-to-IT-department date is required");
        }
        if (paidToItDate.isAfter(LocalDate.now())) {
            throw new BusinessException("Paid-to-IT-department date must not be in the future");
        }

        // ----- Advance the batch to stage 2 (Req 6.1) -----
        String actor = currentUser();
        batch.setStatus(TdsRemittanceStatus.PAID_TO_IT_DEPARTMENT);
        batch.setChallanNumber(challanNumber);
        batch.setPaidToItDate(paidToItDate);
        batch.setPaidToItBy(actor);
        if (StringUtils.hasText(request.getRemarks())) {
            batch.setRemarks(request.getRemarks().trim());
        }

        TdsRemittance saved = remittanceRepository.save(batch);
        log.info("Recorded IT remittance for TDS batch {} (challan {}, date {}) by {}",
                saved.getBatchReference(), saved.getChallanNumber(), saved.getPaidToItDate(), actor);

        return TdsMapper.toRemittanceDTO(saved);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns the batch together with its lines mapped via {@link TdsMapper#toRemittanceDTO}
     * (Req 8.1, 8.2), or raises a {@link ResourceNotFoundException} when no batch matches the id
     * (Req 8.4). A batch with no lines yields an empty line collection rather than an error
     * (Req 8.3). Read-only: nothing is modified.
     */
    @Override
    @Transactional(readOnly = true)
    public TdsRemittanceDTO getBatch(Long remittanceId) {
        TdsRemittance batch = remittanceRepository.findById(remittanceId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "TDS remittance batch not found with id: '" + remittanceId + "'"));
        return TdsMapper.toRemittanceDTO(batch);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Lists remittance batches filtered by their own attributes (financial year, status, stage
     * dates) via {@link TdsSpecificationBuilder#buildForBatch(TdsFilterRequest)} and paged through
     * the {@link TdsRemittanceRepository} specification executor (Req 8.5). Absent/blank filter
     * fields contribute no predicate, so an empty filter returns the full set (Req 8.7). Defaults
     * of page 0 / size 20 apply when the pageable is null or unpaged (Req 8.6). Results are ordered
     * by paid-to-accountant date descending with batch id descending as a stable tie-breaker.
     */
    @Override
    @Transactional(readOnly = true)
    public PagedResponse<TdsRemittanceDTO> listBatches(TdsFilterRequest filter, Pageable pageable) {
        TdsFilterRequest effectiveFilter = filter != null ? filter : new TdsFilterRequest();

        PageRequest pageRequest = resolvePageRequest(pageable);

        Specification<TdsRemittance> specification = specificationBuilder.buildForBatch(effectiveFilter);

        Page<TdsRemittance> resultPage = remittanceRepository.findAll(specification, pageRequest);

        List<TdsRemittanceDTO> content = resultPage.getContent().stream()
                .map(TdsMapper::toRemittanceDTO)
                .collect(Collectors.toList());

        return PagedResponse.<TdsRemittanceDTO>builder()
                .content(content)
                .page(resultPage.getNumber())
                .size(resultPage.getSize())
                .totalElements(resultPage.getTotalElements())
                .totalPages(resultPage.getTotalPages())
                .last(resultPage.isLast())
                .build();
    }

    /**
     * Forward-only transition guard (see design.md "Transition guard"). A transition is legal only
     * when {@code to} is exactly one stage ahead of {@code from}; a backward or same-stage move
     * (Req 7.2) and a stage-skipping move (Req 7.3) are both rejected with a
     * {@link BusinessException}, leaving the batch unchanged.
     */
    private void assertForwardTransition(TdsRemittanceStatus from, TdsRemittanceStatus to) {
        if (to.ordinal() <= from.ordinal()) {
            throw new BusinessException(
                    "Invalid TDS status transition: " + from + " -> " + to + " (must move forward)");
        }
        if (to.ordinal() != from.ordinal() + 1) {
            throw new BusinessException(
                    "Cannot skip TDS remittance stage: " + from + " -> " + to);
        }
    }

    /**
     * Resolves the effective page request for batch listing, applying defaults (page 0 / size 20)
     * when no pageable is supplied or it is unpaged (Req 8.6). Batches are ordered by
     * paid-to-accountant date descending, with batch id descending as a stable tie-breaker.
     */
    private PageRequest resolvePageRequest(Pageable pageable) {
        int page = pageable != null && pageable.isPaged() ? pageable.getPageNumber() : DEFAULT_PAGE_NUMBER;
        int size = pageable != null && pageable.isPaged() ? pageable.getPageSize() : DEFAULT_PAGE_SIZE;
        return PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("paidToAccountantDate"), Sort.Order.desc("tdsRemittanceId")));
    }

    /**
     * A voucher is an eligible Source_Voucher when it is {@code FINAL}, {@code tdsApplicable} is
     * true, {@code tdsAmount} is present and strictly greater than zero, and it references a
     * vendor (Req 5.4, mirrors the read model's eligibility conditions in Req 1.2).
     */
    private boolean isEligibleSourceVoucher(Voucher voucher) {
        if (voucher == null) {
            return false;
        }
        return voucher.getStatus() == VoucherStatus.FINAL
                && Boolean.TRUE.equals(voucher.getTdsApplicable())
                && voucher.getTdsAmount() != null
                && voucher.getTdsAmount().compareTo(BigDecimal.ZERO) > 0
                && voucher.getVendor() != null;
    }

    /**
     * Generates a unique batch reference of the form {@code TDS-<FY>-NNNN} (Req 5.12). The
     * financial year is normalized to remove separators (e.g. {@code 2024-2025} &rarr;
     * {@code 20242025}) so the hyphen structure of the reference stays predictable, and a random
     * zero-padded suffix is retried against the uniqueness guard until an unused reference is
     * found. The unique constraint on {@code batch_reference} remains the final guarantee.
     */
    private String generateBatchReference(String financialYear) {
        String fyPart = financialYear != null ? financialYear.replaceAll("[^0-9A-Za-z]", "") : "NA";
        int upperBound = (int) Math.pow(10, BATCH_SEQUENCE_WIDTH);
        for (int attempt = 0; attempt < BATCH_REFERENCE_MAX_ATTEMPTS; attempt++) {
            int sequence = ThreadLocalRandom.current().nextInt(upperBound);
            String reference = String.format("TDS-%s-%0" + BATCH_SEQUENCE_WIDTH + "d", fyPart, sequence);
            if (!remittanceRepository.existsByBatchReference(reference)) {
                return reference;
            }
        }
        throw new BusinessException("Unable to allocate a unique batch reference; please retry");
    }

    /**
     * Resolves the acting username from the security context, falling back to {@code "SYSTEM"}
     * for unauthenticated contexts, mirroring the convention used elsewhere in the codebase
     * (see {@code MaintenanceLedgerService}). Recorded as the stage-1 actor (Req 5.11).
     */
    private String currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getName() != null
                && !"anonymousUser".equalsIgnoreCase(auth.getName())) {
            return auth.getName();
        }
        return "SYSTEM";
    }
}
