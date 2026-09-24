# Implementation Plan: Vendor TDS Management

## Overview

Incremental, bottom-up build of the `com.society.module.tds` backend (enum → entities → read model → repositories → filter spec → DTOs/mappers → services → controller → PDF), followed by unit/property/integration tests, then the Angular `tds` feature wired into the Vendor module. Each task builds on the prior one and ends with integration/navigation wiring so nothing is orphaned. Implementation language is Java (Spring Boot) for the backend and TypeScript/Angular for the frontend, matching the existing codebase and the design document.

## Tasks

- [x] 1. Add `TdsRemittanceStatus` enum
  - Create `com.society.enums.TdsRemittanceStatus` with `DEDUCTED`, `PAID_TO_ACCOUNTANT`, `PAID_TO_IT_DEPARTMENT` in forward ordinal order
  - Javadoc noting `DEDUCTED` is implicit/never persisted on a batch
  - _Requirements: 1.5, 7.1_

- [ ] 2. Create remittance entities
  - [x] 2.1 Implement `TdsRemittance` (batch) entity
    - `com.society.module.tds.entity.TdsRemittance` extending `BaseEntity`, Lombok, `@Table("tds_remittance")`
    - Fields: `batchReference` (unique, len 30), `status` (`@Enumerated(STRING)`), `financialYear`, `totalAmount` (precision 12 scale 2), stage-1 fields, stage-2 fields, `remarks`, `@OneToMany` `lines`
    - _Requirements: 5.1, 5.9, 5.10, 5.11, 5.12, 6.1, 6.2, 8.1_

  - [x] 2.2 Implement `TdsRemittanceLine` (voucher link) entity
    - `com.society.module.tds.entity.TdsRemittanceLine` extending `BaseEntity`, `@ManyToOne` to `TdsRemittance` and `Voucher`, `uniqueConstraint(uk_tds_line_voucher)` on `voucher_id`, snapshot `tdsAmount` (precision 12 scale 2)
    - _Requirements: 5.2, 7.5, 7.6, 8.2_

- [x] 3. Create DB migration and `TdsLineView` read model
  - [x] 3.1 Add migration for tables and view
    - DDL for `tds_remittance`, `tds_remittance_line` (audit columns, FKs, unique `voucher_id`, indexes `idx_tds_remittance_fy_status`, composite `vouchers(tds_applicable, status, voucher_date)`) and `tds_line_view` per design SQL
    - _Requirements: 1.1, 1.2, 1.3, 7.5_

  - [x] 3.2 Implement `TdsLineView` `@Immutable` entity
    - Map read-only entity to `tds_line_view` projecting voucher, vendor, section, rate, amount, deduction date, FY, `remittanceStatus` (COALESCE `DEDUCTED`), stage dates, challan
    - _Requirements: 1.4, 1.5, 1.6, 1.7_

- [ ] 4. Create repositories
  - [x] 4.1 Implement `TdsLineViewRepository`
    - `JpaRepository<TdsLineView, Long>` + `JpaSpecificationExecutor<TdsLineView>`
    - _Requirements: 1.1, 2.1, 3.1_

  - [x] 4.2 Implement `TdsRemittanceRepository`
    - `JpaRepository<TdsRemittance, Long>` + `JpaSpecificationExecutor`; lookups for existing voucher lines (to detect already-linked vouchers) and batch listing
    - _Requirements: 5.5, 8.1, 8.4, 8.5_

- [x] 5. Implement `TdsSpecificationBuilder`
  - `com.society.module.tds.specification` mirroring `TransactionSpecificationBuilder`: blank/absent → no predicate, `statuses` → `IN`, remittance-date OR across both stage dates, AND-composition so results only narrow
  - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7, 2.8, 2.9, 2.10, 2.11, 2.12, 2.13_

- [x] 6. Create DTOs and mappers
  - [x] 6.1 Add request/response DTOs
    - `TdsFilterRequest`, `TdsLineDTO`, `TdsSummaryDTO`, `TdsRemittanceDTO`, `CreateRemittanceRequest` (`@NotEmpty`/`@NotBlank`/`@NotNull`), `RecordItRemittanceRequest`
    - _Requirements: 2.15, 3.4, 3.5, 5.1, 5.3, 5.7, 6.2, 6.3_

  - [x] 6.2 Add mapping from `TdsLineView`/`TdsRemittance` to DTOs
    - Map view → `TdsLineDTO` (status `DEDUCTED` when no batch); batch entity → `TdsRemittanceDTO` with lines
    - _Requirements: 1.4, 1.5, 1.6, 1.7, 8.1, 8.2, 8.3_

- [x] 7. Implement `TdsService` (read model + summary)
  - [x] 7.1 Implement `listTdsLines`
    - Build spec, page via repository, default page 0 / size 20, validate page size 1–200 and non-negative page, map to `PagedResponse<TdsLineDTO>`; validate/reject malformed filters and inverted date ranges
    - _Requirements: 1.1, 2.1, 2.14, 2.15, 2.16, 3.1, 3.2, 3.3, 3.4, 3.5, 3.6_

  - [x] 7.2 Implement `summarize`
    - Aggregate over the same spec: `totalDeducted`, `totalPaidToAccountant`, `totalPaidToItDept`, `totalPending = deducted - paidToIt`, `lineCount`; `HALF_UP` scale 2; zeros on empty set; pending ≥ 0.00
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7, 4.8, 4.9, 4.10_

  - [ ]* 7.3 Write unit tests for summary math and filter mapping
    - HALF_UP boundary (`0.005`), empty set zeros, single line, blank-field no-predicate
    - _Requirements: 4.8, 4.9, 2.2_

- [ ] 8. Implement `TdsRemittanceService` (lifecycle write model)
  - [x] 8.1 Implement `createBatch` (stage 1) with validations
    - Validate 1–500 voucher ids, non-empty list, each eligible Source_Voucher, none already lined, single financial year, accountant name 1–100, paid-to-accountant date not future; create batch `PAID_TO_ACCOUNTANT`, one line per voucher snapshotting `tdsAmount`, `totalAmount` = HALF_UP scale-2 sum, derive FY, record actor/reference, assign unique `batchReference`
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7, 5.8, 5.9, 5.10, 5.11, 5.12, 7.5, 7.6_

  - [x] 8.2 Implement `recordItRemittance` (stage 2) with forward-only guard
    - `assertForwardTransition`; require current status `PAID_TO_ACCOUNTANT`; validate challan 1–50 and paid-to-IT date present and not future; advance to `PAID_TO_IT_DEPARTMENT`, store challan/date/actor; reject unknown batch, wrong-status, backward/skip transitions leaving state unchanged
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6, 7.1, 7.2, 7.3, 7.4_

  - [x] 8.3 Implement `getBatch` and `listBatches`
    - `getBatch` returns batch + lines or `ResourceNotFoundException`; empty line collection allowed; `listBatches` paginated over filter spec with defaults page 0 / size 20
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5, 8.6, 8.7_

  - [ ]* 8.4 Write unit tests for transition guard and eligibility rejections
    - Each valid + forbidden transition, each `createBatch` rejection reason, mixed-FY rejection
    - _Requirements: 5.4, 5.5, 5.6, 6.5, 7.2, 7.3, 7.4_

- [x] 9. Implement `TdsController`
  - `@RequestMapping("/tds")`, `@ModelAttribute TdsFilterRequest`, `@DateTimeFormat(iso=DATE)`; endpoints: `GET /tds/lines`, `GET /tds/summary`, `GET /tds/remittances`, `GET /tds/remittances/{id}`, `POST /tds/remittances` (201), `PATCH /tds/remittances/{id}/it-remittance`; `ApiResponse`/`PagedResponse`; errors via `GlobalExceptionHandler`
  - _Requirements: 1.1, 2.1, 3.1, 4.1, 5.1, 6.1, 8.1, 8.5_

- [x] 10. Implement `TdsReportPdfService` and PDF endpoint
  - Follow `VendorLedgerPdfService` pattern: render filtered lines (same order as list) + summary; empty-filter produces zero-line PDF with 0 totals and "no records" indication; fail cleanly without partial output; add `GET /tds/lines/pdf` returning `ResponseEntity<byte[]>`
  - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.5_

- [ ] 11. Checkpoint - backend compiles and unit tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [ ]* 12. Write property tests (jqwik, ≥100 iterations, tag `Feature: vendor-tds-management, Property N`)
  - [ ]* 12.1 Property 1 - forward-only transitions
    - **Property 1: Status transitions only move forward**
    - **Validates: Requirements 7.1, 7.2, 7.3**

  - [ ]* 12.2 Property 2 - money conserved
    - **Property 2: Money is conserved (remitted + pending = deducted)**
    - **Validates: Requirements 4.2, 4.3, 4.4, 4.5**

  - [ ]* 12.3 Property 3 - filters never widen
    - **Property 3: Filters never widen the result set**
    - **Validates: Requirements 2.1, 2.2, 2.13**

  - [ ]* 12.4 Property 4 - voucher remitted at most once
    - **Property 4: A voucher is remitted at most once**
    - **Validates: Requirements 7.4, 5.5**

  - [ ]* 12.5 Property 5 - batch total = HALF_UP sum of lines
    - **Property 5: Batch total equals sum of its lines**
    - **Validates: Requirements 5.7**

  - [ ]* 12.6 Property 6 - summary agrees with filtered list
    - **Property 6: Summary agrees with the list under identical filters**
    - **Validates: Requirements 4.1, 4.6, 4.7**

- [ ]* 13. Write integration tests
  - [ ]* 13.1 `@DataJpaTest` repository/constraint tests
    - `tds_line_view` projection + eligibility, unique `voucher_id` constraint rejects double-line
    - _Requirements: 1.2, 1.3, 7.5, 7.6_

  - [ ]* 13.2 `@SpringBootTest` slice tests for transition endpoints
    - `POST /tds/remittances` and `PATCH .../it-remittance`: persisted actor/date/reference, 400 on forbidden transitions
    - _Requirements: 5.11, 6.2, 6.5, 7.2, 7.3_

- [x] 14. Implement Angular `TdsService`
  - `frontend/src/app/modules/tds`: typed HTTP wrappers, serialize filters to query params, PDF blob download from `/tds/lines/pdf`
  - _Requirements: 9.1, 10.2_

- [x] 15. Implement TDS Angular components
  - [x] 15.1 `TdsStatusBadgeComponent`
    - Colored badge per status (grey/amber/green)
    - _Requirements: 10.2_

  - [x] 15.2 `TdsSummaryCardsComponent`
    - Cards for deducted / to-accountant / to-IT / pending; recompute on filter change; zeros when no records
    - _Requirements: 4.1, 10.2, 10.5_

  - [x] 15.3 `TdsListComponent`
    - Filterable, paginated table wiring filter bar + badges + summary cards; empty-result indication; error state on load failure/timeout
    - _Requirements: 2.1, 3.1, 10.2, 10.4, 10.5_

  - [x] 15.4 `CreateRemittanceDialogComponent`
    - Select deducted lines, capture accountant name/date/reference; `POST /tds/remittances`
    - _Requirements: 5.1, 10.2_

  - [x] 15.5 `RecordItRemittanceDialogComponent`
    - Capture challan + date; `PATCH /tds/remittances/{id}/it-remittance`
    - _Requirements: 6.1, 10.2_

- [x] 16. Wire Vendor-module navigation and route to TDS screen
  - Add TDS nav entry in the Vendor module routing to `TdsListComponent`; enforce authorization (hide entry / block screen with unauthorized indication)
  - _Requirements: 10.1, 10.3_

- [ ] 17. Final checkpoint - all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional (tests) and can be skipped for a faster MVP; core implementation tasks are required.
- Each task references specific granular requirement clauses for traceability.
- Property tests validate the six universal correctness properties from `design.md`; unit and integration tests cover examples, edge cases, and persistence constraints.
- Property tests use jqwik (already in the backend) with a minimum of 100 iterations each.

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1", "3.1"] },
    { "id": 1, "tasks": ["2.1", "2.2"] },
    { "id": 2, "tasks": ["3.2"] },
    { "id": 3, "tasks": ["4.1", "4.2"] },
    { "id": 4, "tasks": ["5", "6.1"] },
    { "id": 5, "tasks": ["6.2", "7.1"] },
    { "id": 6, "tasks": ["7.2", "8.1"] },
    { "id": 7, "tasks": ["7.3", "8.2", "8.3"] },
    { "id": 8, "tasks": ["8.4", "9"] },
    { "id": 9, "tasks": ["10", "13.1"] },
    { "id": 10, "tasks": ["12.1", "12.2", "12.3", "12.4", "12.5", "12.6", "13.2", "14"] },
    { "id": 11, "tasks": ["15.1", "15.2"] },
    { "id": 12, "tasks": ["15.3", "15.4", "15.5"] },
    { "id": 13, "tasks": ["16"] }
  ]
}
```
