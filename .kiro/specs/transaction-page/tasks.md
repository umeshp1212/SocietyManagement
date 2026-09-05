# Implementation Plan: Transaction Page

## Overview

This plan implements the read-only Transaction Page feature incrementally, in
dependency order. The backend adds a new `com.society.module.transaction` module
that queries the existing `MaintenancePayment` records through a
`JpaSpecificationExecutor`-backed repository, enforces role-based access scope,
and exposes two secured REST endpoints. The frontend adds an Angular 17 standalone
feature (list, filter panel, detail dialog) that consumes those endpoints.

The plan is test-driven: each backend logical unit is implemented, then its
property-based tests (jqwik) implementing the 16 design correctness properties are
added, followed by example/integration (MockMvc) and Angular component tests.
Test sub-tasks are marked optional with `*` and can be skipped for a faster MVP;
core implementation tasks are never optional.

Languages/build: Backend is Java + Spring Boot built with Maven
(`backend/pom.xml`); property tests use jqwik. Frontend is Angular 17 built with
Angular CLI (`frontend/package.json`); tests run via `ng test`.

## Tasks

- [x] 1. Backend foundation: build dependency and repository capability
  - [x] 1.1 Add jqwik test dependency to Maven build
    - Add the `net.jqwik:jqwik` dependency (JUnit 5 platform) with `<scope>test</scope>` to `backend/pom.xml`
    - Ensure the Surefire configuration runs the JUnit Platform so jqwik `@Property` tests are discovered
    - Verify with `mvn -q -DskipTests=false test-compile` that the dependency resolves
    - _Requirements: (build enablement for property tests across all backend properties)_

  - [x] 1.2 Add JpaSpecificationExecutor to MaintenancePaymentRepository
    - Extend `MaintenancePaymentRepository` to also implement `JpaSpecificationExecutor<MaintenancePayment>` (interface addition only, no schema change, existing finders unchanged)
    - This supplies `Page<MaintenancePayment> findAll(Specification, Pageable)` used by the list query
    - _Requirements: 1.1, 7.1_

- [ ] 2. Transaction module DTOs, filter request, and value objects
  - [x] 2.1 Create TransactionFilterRequest DTO
    - Create `com.society.module.transaction.dto.TransactionFilterRequest` with fields: `startDate` (LocalDate), `endDate` (LocalDate), `paymentMode` (MaintenancePayment.PaymentMode), `statuses` (List<MaintenancePayment.PaymentStatus>), `payerType` (String), `unitId` (Long), `unitSearch` (String), `reference` (String)
    - _Requirements: 3.1, 4.1, 5.1, 5.3, 6.1, 6.3, 6.4, 9.1_

  - [x] 2.2 Create TransactionSummaryDTO and TransactionDetailDTO
    - Create `TransactionSummaryDTO` with: paymentId, unitNumber, payerName, payerType, amount, paymentDate, paymentMode, status, transactionId, receiptNumber
    - Create `TransactionDetailDTO` (superset): the summary fields plus originalAmount, discountAmount, discountPercent, remarks, verifiedOn, verifiedBy, reversedOn, reversedBy, reversalReason
    - _Requirements: 1.2, 8.1, 8.3, 8.4_

  - [x] 2.3 Create AccessScope value object
    - Create `com.society.module.transaction.service.AccessScope` as an immutable record `(boolean societyWide, Set<Long> unitIds)` with factory methods `societyWide()` and `memberScoped(Set<Long>)`
    - _Requirements: 10.2, 10.4_

- [x] 3. Transaction mapper (source entity -> DTOs)
  - [x] 3.1 Implement TransactionMapper
    - Create `com.society.module.transaction.mapper.TransactionMapper` mapping `MaintenancePayment` to `TransactionSummaryDTO` and to `TransactionDetailDTO`
    - Preserve null values on the detail mapping (do not drop fields) so the frontend can render placeholders
    - Read unit number via the payment's `unit` relation
    - _Requirements: 1.2, 8.1, 8.3, 8.4_

  - [x] 3.2 Write property test for summary mapping completeness
    - **Property 15: Summary mapping is complete**
    - **Validates: Requirements 1.2**
    - jqwik `@Property(tries = 100)` over generated payments asserting every summary field equals the source value

  - [x] 3.3 Write property test for detail mapping completeness and null preservation
    - **Property 16: Detail mapping is complete and preserves nulls**
    - **Validates: Requirements 8.1, 8.3, 8.4**
    - jqwik `@Property` over generated payments (including null verification/reversal fields) asserting every detail field is exposed and nulls are preserved

- [x] 4. Access scope resolution
  - [x] 4.1 Implement AccessScopeResolver
    - Create `AccessScopeResolver.resolve(Authentication)`: society-wide for `ROLE_SUPER_ADMIN` or configured committee roles; otherwise resolve member unit IDs from `User.ownerId` (via `UnitRepository.findByOwnerId`) and `User.tenantId` (via `Tenant.unit`)
    - A member with no linked units returns an empty member scope (empty result, not an error)
    - Throw an authorization error when an authenticated principal maps to no user
    - Centralize the society-wide predicate so list and detail apply it identically
    - _Requirements: 2.1, 2.5, 10.2, 10.4_

  - [x] 4.2 Write example/unit tests for AccessScopeResolver
    - Cover owner-only, tenant-only, owner+tenant, no-link (empty scope), and society-wide role mappings
    - _Requirements: 2.1, 2.5, 10.2, 10.4_

- [x] 5. Specification builder (dynamic AND-composed filters)
  - [x] 5.1 Implement TransactionSpecificationBuilder
    - Create `TransactionSpecificationBuilder.build(AccessScope, TransactionFilterRequest)` returning a single `Specification<MaintenancePayment>`
    - Access scope: society-wide adds no predicate; member scope adds `unit.unitId IN (ids)`, and an empty member scope adds an always-false predicate
    - Date range: `paymentDate >= startDate` and/or `<= endDate` when present (inclusive)
    - Payment mode: equals when present
    - Status: `status IN (statuses)` (OR within, when non-empty)
    - Unit id: equals when present; payer type: equals when present
    - Unit search: case-insensitive `LIKE %term%` on unit number
    - Reference: case-insensitive `LIKE %term%` on receiptNumber OR transactionId
    - Absent/blank filters contribute no predicate; combine all with `cb.and(...)`
    - _Requirements: 3.1, 3.2, 3.3, 3.7, 4.1, 4.2, 5.1, 5.2, 5.3, 6.1, 6.3, 6.4, 7.1, 7.4, 9.1, 9.2, 10.2, 10.4_

  - [x] 5.2 Write property test for AND composition with absent-filter identity
    - **Property 4: Filters compose by logical AND with absent filters as identity**
    - **Validates: Requirements 3.7, 4.2, 5.2, 7.1, 7.4, 9.2**
    - Model-based test: compare specification results against an in-memory reference filter over generated payment/filter sets

  - [x] 5.3 Write property test for access-scope containment
    - **Property 2: Member results never escape access scope**
    - **Validates: Requirements 2.1, 7.2, 7.3, 10.2, 10.4**

  - [x] 5.4 Write property test for inclusive date-range selection
    - **Property 5: Date-range filter selects an inclusive interval**
    - **Validates: Requirements 3.1, 3.2, 3.3**

  - [x] 5.5 Write property test for payment-mode selection
    - **Property 7: Payment-mode filter selects exactly one mode**
    - **Validates: Requirements 4.1**

  - [x] 5.6 Write property test for status-set selection
    - **Property 8: Status filter selects the chosen status set**
    - **Validates: Requirements 5.1, 5.3**

  - [x] 5.7 Write property test for unit-id selection
    - **Property 9: Unit-id filter selects a single unit**
    - **Validates: Requirements 6.1**

  - [x] 5.8 Write property test for payer-type selection
    - **Property 10: Payer-type filter selects a single payer type**
    - **Validates: Requirements 6.3**

  - [x] 5.9 Write property test for case-insensitive unit-number search
    - **Property 11: Unit-search matches unit number case-insensitively**
    - **Validates: Requirements 6.4**

  - [x] 5.10 Write property test for case-insensitive reference search
    - **Property 12: Reference search matches receipt or transaction id case-insensitively**
    - **Validates: Requirements 9.1**

- [x] 6. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 7. Transaction service (scope + validation + query orchestration)
  - [x] 7.1 Implement TransactionService.listTransactions
    - Resolve access scope via `AccessScopeResolver`
    - Validate filters before building any query: date range (start <= end when both present; unparsable dates rejected), paymentMode enum, each status enum, payerType in {OWNER, TENANT}, unitId existence (`UnitRepository.existsById`), unitSearch length 1-50, reference trimmed/blank ignored and length <= 100 — invalid values raise `BusinessException` and run no query
    - Resolve effective page size: role default (25 admin / 50 member) then clamp to [1,100]
    - Build the specification, execute with `PageRequest.of(page, size, Sort.by(desc(paymentDate), desc(paymentId)))`, map to `TransactionSummaryDTO`, wrap in `PagedResponse`
    - _Requirements: 1.1, 1.4, 1.5, 1.6, 2.1, 2.4, 3.4, 3.5, 4.3, 5.4, 6.2, 6.5, 6.6, 7.5, 7.6, 9.3_

  - [x] 7.2 Implement TransactionService.getTransaction (detail with scope check)
    - Load by id; throw `ResourceNotFoundException` when absent
    - For a member caller, deny (authorization error) when the transaction's unit is outside their scope; administrators may access any existing transaction
    - Map to `TransactionDetailDTO`
    - _Requirements: 8.5, 8.6, 10.3_

  - [x] 7.3 Write property test for stable descending ordering
    - **Property 1: Result ordering is stable and descending**
    - **Validates: Requirements 1.1, 2.4**

  - [x] 7.4 Write property test for detail access scope enforcement
    - **Property 3: Detail access respects scope**
    - **Validates: Requirements 2.3, 8.5, 10.3**

  - [x] 7.5 Write property test for bounded effective page size
    - **Property 13: Effective page size is bounded**
    - **Validates: Requirements 1.4, 2.1**

  - [x] 7.6 Write property test for pagination metadata consistency
    - **Property 14: Pagination metadata is internally consistent**
    - **Validates: Requirements 1.5, 1.6**

  - [x] 7.7 Write property test for invalid date-range rejection
    - **Property 6: Invalid date range is rejected**
    - **Validates: Requirements 3.4**

  - [x] 7.8 Write example/unit tests for filter validation messages
    - Assert `BusinessException` message names the offending value for invalid payment mode, status, payer type, unit-search/reference length, and unknown unit id; assert detail 404 for a non-existent id
    - _Requirements: 3.4, 3.5, 4.3, 5.4, 6.2, 6.5, 6.6, 7.6, 8.6, 9.3_

- [ ] 8. Access control wiring, controller, and error mapping
  - [x] 8.1 Add AccessDeniedException -> 403 mapping in GlobalExceptionHandler
    - Add a handler for `org.springframework.security.access.AccessDeniedException` returning `ApiResponse.error(...)` with HTTP 403 (only if not already present)
    - _Requirements: 2.3, 8.5, 10.3, 10.5_

  - [x] 8.2 Seed TRANSACTION_VIEW permission/authority and grant to roles
    - Ensure a `TRANSACTION_VIEW` permission/authority is seeded via the existing permission/role setup (e.g. `DataInitializer`) and granted to member-facing roles; society-wide roles (`SUPER_ADMIN`, committee roles) additionally receive society-wide scope
    - _Requirements: 10.2, 10.4, 10.5_

  - [x] 8.3 Implement TransactionController
    - Create `TransactionController` with `@RequestMapping("/transactions")` returning `ResponseEntity<ApiResponse<...>>`
    - `GET /transactions`: bind `TransactionFilterRequest` (status repeatable), `page` (default 0), optional `size`, inject `Authentication`; delegate to `listTransactions`
    - `GET /transactions/{paymentId}`: inject `Authentication`; delegate to `getTransaction`
    - Secure both with `@PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('TRANSACTION_VIEW')")`
    - Translate unrecognized enum bind values into `BusinessException` naming the value (payment mode / status)
    - _Requirements: 1.5, 4.3, 5.4, 10.1, 10.5_

- [ ] 9. Backend integration tests
  - [x] 9.1 Write MockMvc integration tests for access control and scope
    - `@SpringBootTest` + MockMvc: unauthenticated -> 401; authenticated without `TRANSACTION_VIEW`/`SUPER_ADMIN` -> 403; member vs admin scope end-to-end against seeded data; member out-of-scope detail -> 403; non-existent detail -> 404
    - _Requirements: 2.3, 8.5, 8.6, 10.1, 10.3, 10.4, 10.5_

  - [x] 9.2 Write performance smoke integration tests
    - Assert member list, admin unit filter, and detail return within the stated latency budgets on a representative dataset
    - _Requirements: 2.2, 6.1, 8.2_

- [x] 10. Checkpoint - Ensure all backend tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 11. Frontend models and service
  - [x] 11.1 Create TypeScript models mirroring DTOs
    - Create `frontend/src/app/features/transactions/models/transaction.models.ts` with `PaymentMode`, `TransactionStatus`, `PayerType` unions, `TransactionFilter`, `TransactionSummary`, `TransactionDetail`, and `PagedResponse<T>` / `ApiResponse<T>` mirroring the backend envelopes
    - _Requirements: 1.2, 8.1_

  - [x] 11.2 Implement TransactionService (HttpClient)
    - Create `services/transaction.service.ts` calling `GET /transactions` (translate filter to query params; status as repeated params) and `GET /transactions/{id}`
    - _Requirements: 1.1, 1.5, 5.3, 8.1_

- [x] 12. Frontend components
  - [x] 12.1 Implement TransactionFilterPanelComponent
    - Reactive form with `mat-date-range-input`, payment-mode `mat-select`, multi-select status, payer-type `mat-select` (admin only), unit search text, reference search text
    - Emit a `TransactionFilter` on apply/clear; client-side guards for start <= end and max lengths (server authoritative); on server validation error show message and keep prior filter
    - Hide unit/payer admin-only filters for members based on the app's existing role info
    - _Requirements: 3.4, 4.1, 5.1, 5.3, 6.3, 6.4, 6.5, 7.1, 7.4, 7.6, 9.1, 9.3_

  - [x] 12.2 Implement TransactionDetailDialogComponent
    - `MatDialog` rendering all detail fields with an explicit placeholder (e.g. "—") for null values; verification block when verified; reversal block when reversed
    - On 403 show "access denied", on 404 show "not found", on other errors show "could not load" — each without partial fields
    - _Requirements: 8.1, 8.3, 8.4, 8.5, 8.6, 8.7_

  - [x] 12.3 Implement TransactionListComponent (orchestrator)
    - `mat-table` with columns (payment id, unit number, payer name, payer type, amount, payment date, payment mode, status, reference, receipt number) and `mat-paginator` (page-size options reflecting role: 25-based admin / 50-based member)
    - Empty-state template when no content; error banner that retains current rows on service failure
    - Compose the filter panel and open the detail dialog on row click
    - _Requirements: 1.2, 1.3, 1.5, 1.7, 2.5, 2.6, 3.6, 7.1_

  - [x] 12.4 Wire transaction route
    - Add a lazy-loaded `transactions` route in `frontend/src/app/app.routes.ts` guarded by auth, pointing at the transaction feature
    - _Requirements: 1.1, 10.1_

- [~] 13. Frontend component tests
  - [~] 13.1 Write TransactionListComponent tests
    - Renders required columns and paginator; empty-state on empty content; error banner retains prior rows on service failure
    - _Requirements: 1.2, 1.3, 1.7, 2.5, 2.6, 3.6_

  - [~] 13.2 Write TransactionFilterPanelComponent tests
    - Emits AND-combined filter; mirrors server validation for start <= end and max lengths; preserves prior filter on server rejection
    - _Requirements: 3.4, 6.5, 7.1, 7.6, 9.3_

  - [ ]* 13.3 Write TransactionDetailDialogComponent tests
    - Renders all fields with placeholders for nulls; verification block when verified; reversal block when reversed; correct error message (no fields) for 403/404/500
    - _Requirements: 8.1, 8.3, 8.4, 8.5, 8.6, 8.7_

- [ ] 14. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional (unit, property, integration, and component
  tests) and can be skipped for a faster MVP; core implementation tasks are never
  optional.
- Each task references specific requirement clauses for traceability.
- Property-based tests (jqwik, minimum 100 iterations each) implement the 16
  design correctness properties and are placed close to the code they validate to
  catch errors early.
- Checkpoints ensure incremental validation at natural boundaries.
- The backend enforces access scope in the service regardless of client behavior;
  the frontend role-awareness only affects which admin-only filters render.

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2", "2.1", "2.2", "2.3", "11.1"] },
    { "id": 1, "tasks": ["3.1", "4.1", "11.2", "12.4"] },
    { "id": 2, "tasks": ["3.2", "3.3", "4.2", "5.1", "8.1", "8.2", "12.1", "12.2"] },
    { "id": 3, "tasks": ["5.2", "5.3", "5.4", "5.5", "5.6", "5.7", "5.8", "5.9", "5.10", "7.1", "7.2", "12.3"] },
    { "id": 4, "tasks": ["7.3", "7.4", "7.5", "7.6", "7.7", "7.8", "8.3", "13.1", "13.2", "13.3"] },
    { "id": 5, "tasks": ["9.1", "9.2"] }
  ]
}
```
