# Design Document: Transaction Page

## Overview

The Transaction Page is a read-only feature that presents member maintenance
transactions in a single searchable, filterable, paginated list. Each
transaction corresponds to one existing `MaintenancePayment` record; the feature
does not create, mutate, or delete payment data.

The design introduces a **new, read-only `transaction` module**
(`com.society.module.transaction`) on the backend rather than extending the
`maintenance` module. Rationale:

- **Separation of concerns.** The `maintenance` module owns the write/lifecycle
  of payments (creation, verification, reversal, gateway callbacks). The
  transaction feature is purely a *query/read model* over that same data with a
  different access-control shape (society-wide vs. member self-scope). Keeping it
  separate avoids overloading `PaymentService` with cross-cutting filter and
  scoping logic.
- **Reuse without coupling.** The new module reads `MaintenancePayment` through
  its own `JpaSpecificationExecutor`-based repository and reuses the shared
  `ApiResponse<T>` / `PagedResponse<T>` envelopes and the existing exception
  types. It does not duplicate the entity or persistence mapping.
- **Distinct authority.** Transaction viewing is gated by a dedicated
  `TRANSACTION_VIEW` authority (plus `SUPER_ADMIN`), independent of maintenance
  write permissions.

The `MaintenancePayment` entity is treated as the single source of truth. To
enable dynamic multi-filter querying, `MaintenancePayment` gains
`JpaSpecificationExecutor` support (interface addition only — no schema change).

Key requirement drivers:

- Society-wide list for administrators (Req 1), member self-scope list (Req 2, Req 10).
- Composable filters — date range (Req 3), payment mode (Req 4), status (Req 5),
  unit + payer type (Req 6), reference search (Req 9) — combined with logical AND (Req 7).
- Single-transaction detail with access scoping (Req 8).
- Role-based access control throughout (Req 10).

## Architecture

The feature follows the existing layered architecture: Angular standalone
components → `HttpClient` service → REST controller → service (scope + validation)
→ specification-based repository → `MaintenancePayment` table.

```mermaid
flowchart TD
    subgraph Frontend["Frontend (Angular 17, standalone + Material)"]
        TP[TransactionListComponent<br/>mat-table + mat-paginator]
        FP[TransactionFilterPanelComponent<br/>date range, mode, status, payer, unit, reference]
        DD[TransactionDetailDialogComponent]
        TS[TransactionService<br/>HttpClient]
        TP --> FP
        TP --> DD
        TP --> TS
    end

    subgraph Backend["Backend (Spring Boot, com.society.module.transaction)"]
        TC[TransactionController<br/>@PreAuthorize TRANSACTION_VIEW / SUPER_ADMIN]
        TSVC[TransactionService<br/>scope resolution + validation]
        TSPEC[TransactionSpecificationBuilder<br/>composes AND predicates]
        TREPO[MaintenancePaymentRepository<br/>+ JpaSpecificationExecutor]
        AS[AccessScopeResolver<br/>Authentication -> unit IDs]
    end

    DBP[(maintenance_payments)]
    DBU[(units / unit_owners / tenants)]

    TS -- "HTTP GET /api/transactions, /api/transactions/{id}" --> TC
    TC --> TSVC
    TSVC --> AS
    TSVC --> TSPEC
    TSPEC --> TREPO
    TREPO --> DBP
    AS --> DBU

    GEH[GlobalExceptionHandler<br/>ResourceNotFound / Business / Auth] -.maps errors.-> TC
```

### Request flow

1. The controller authenticates the request (Spring Security; unauthenticated →
   401 per Req 10.1) and authorizes it via `@PreAuthorize` (missing authority →
   403 per Req 10.5).
2. `TransactionService` resolves the caller's **access scope** through
   `AccessScopeResolver`:
   - Administrator (`SUPER_ADMIN` or a society-wide role/authority) → society-wide,
     no unit restriction (Req 10.4).
   - Member (holds `TRANSACTION_VIEW` but not society-wide) → the set of unit IDs
     linked to that member (Req 2.1, Req 10.2).
3. `TransactionService` validates the incoming filter values (dates, enums,
   lengths). Invalid filters raise `BusinessException` and no query runs
   (Req 3.4/3.5, Req 4.3, Req 5.4, Req 6.2/6.5/6.6, Req 9.3, Req 7.6).
4. `TransactionSpecificationBuilder` composes one `Specification<MaintenancePayment>`
   from the access-scope constraint AND every active filter.
5. The repository executes the specification with a `Pageable` carrying the
   sort (`paymentDate DESC, paymentId DESC`) and page bounds.
6. Results map to `TransactionSummaryDTO` (list) / `TransactionDetailDTO` (detail)
   and are wrapped in `PagedResponse` / `ApiResponse`.

## Components and Interfaces

### Backend package layout (`com.society.module.transaction`)

```
transaction/
├── controller/
│   └── TransactionController.java
├── dto/
│   ├── TransactionSummaryDTO.java
│   ├── TransactionDetailDTO.java
│   └── TransactionFilterRequest.java
├── service/
│   ├── TransactionService.java
│   ├── AccessScopeResolver.java
│   └── AccessScope.java              // value object: isSocietyWide + Set<Long> unitIds
├── specification/
│   └── TransactionSpecificationBuilder.java
└── mapper/
    └── TransactionMapper.java        // MaintenancePayment -> DTOs
```

Repository change lives in the existing maintenance module (extends
`MaintenancePaymentRepository` with `JpaSpecificationExecutor`).

### TransactionController

`@RestController`, `@RequestMapping("/transactions")`, returns
`ResponseEntity<ApiResponse<...>>`. Every endpoint is secured with
`@PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('TRANSACTION_VIEW')")`.
Society-wide vs. member scope is decided inside the service via
`AccessScopeResolver` (method security guards *access*; the service enforces
*data scope*).

| Method | Path | Query params | Purpose | Requirements |
|---|---|---|---|---|
| GET | `/transactions` | `startDate`, `endDate`, `paymentMode`, `status` (repeatable), `payerType`, `unitId`, `unitSearch`, `reference`, `page` (default 0), `size` (default 25) | Paginated, filtered, scoped list | 1, 2, 3, 4, 5, 6, 7, 9, 10 |
| GET | `/transactions/{paymentId}` | — | Single transaction detail, scope-checked | 8, 10.3 |

Notes:
- `status` is a repeatable param (`?status=SUCCESS&status=VERIFIED`) to support
  multi-select OR semantics (Req 5.3).
- `page` is zero-based internally (matching Spring `Pageable` and the existing
  `UserController` convention); the response surfaces the page number back to the
  client. Page-size default is resolved per-role in the service: 25 for
  administrators (Req 1.4) and 50 for members (Req 2.1), each clamped to
  [1, 100] (Req 1.4).
- `Authentication` is injected into each handler and passed to the service.

Handler shape (illustrative):

```java
@GetMapping
@PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('TRANSACTION_VIEW')")
public ResponseEntity<ApiResponse<PagedResponse<TransactionSummaryDTO>>> list(
        @ModelAttribute TransactionFilterRequest filter,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(required = false) Integer size,
        Authentication authentication) {
    PagedResponse<TransactionSummaryDTO> result =
            transactionService.listTransactions(filter, page, size, authentication);
    return ResponseEntity.ok(ApiResponse.success(result));
}

@GetMapping("/{paymentId}")
@PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('TRANSACTION_VIEW')")
public ResponseEntity<ApiResponse<TransactionDetailDTO>> detail(
        @PathVariable Long paymentId, Authentication authentication) {
    return ResponseEntity.ok(
            ApiResponse.success(transactionService.getTransaction(paymentId, authentication)));
}
```

### TransactionService

Responsibilities:

1. **Scope resolution** — delegate to `AccessScopeResolver.resolve(authentication)`.
2. **Filter validation** — validate before building any query:
   - Date range: both parsable calendar dates; if both present, `startDate <= endDate`
     else `BusinessException` (Req 3.4/3.5).
   - `paymentMode`: must map to `MaintenancePayment.PaymentMode`, else `BusinessException` (Req 4.3).
   - `status`: each supplied value must map to `MaintenancePayment.PaymentStatus`,
     else `BusinessException` (Req 5.4).
   - `payerType`: must be `OWNER` or `TENANT`, else `BusinessException` (Req 6.6).
   - `unitId`: if supplied, must exist (`UnitRepository.existsById`) else
     `BusinessException` "unit not found" (Req 6.2). *(Not-found here is a filter
     validation error, distinct from the detail 404.)*
   - `unitSearch`: length 1–50, else `BusinessException` (Req 6.5).
   - `reference`: trimmed; blank → ignored (Req 9.2); length > 100 →
     `BusinessException` (Req 9.3).
3. **Page size resolution** — apply role default (25 admin / 50 member) then clamp to [1,100].
4. **Specification assembly** — pass the resolved `AccessScope` and validated
   filter to `TransactionSpecificationBuilder`.
5. **Query + map** — execute with `PageRequest.of(page, size, Sort.by(desc(paymentDate), desc(paymentId)))`,
   map to DTOs, wrap in `PagedResponse`.
6. **Detail retrieval** — load by id; `ResourceNotFoundException` if absent
   (Req 8.6); if the caller is a member and the transaction's unit is outside
   their scope, throw an authorization error (Req 8.5 / Req 10.3).

Method signatures:

```java
PagedResponse<TransactionSummaryDTO> listTransactions(
        TransactionFilterRequest filter, int page, Integer size, Authentication auth);

TransactionDetailDTO getTransaction(Long paymentId, Authentication auth);
```

### AccessScopeResolver

Maps the authenticated principal to a data scope. The principal is a Spring
Security `User` whose username matches `users.username`; the `User` entity carries
`ownerId` and `tenantId`.

```java
public AccessScope resolve(Authentication auth) {
    if (isSocietyWide(auth)) {                 // SUPER_ADMIN or society-wide role
        return AccessScope.societyWide();
    }
    User user = userRepository.findByUsername(auth.getName())
            .orElseThrow(() -> new AccessDeniedException("No transaction access"));
    Set<Long> unitIds = new HashSet<>();
    if (user.getOwnerId() != null) {
        unitRepository.findByOwnerId(user.getOwnerId())
                .forEach(u -> unitIds.add(u.getUnitId()));
    }
    if (user.getTenantId() != null) {
        tenantRepository.findById(user.getTenantId())
                .ifPresent(t -> unitIds.add(t.getUnit().getUnitId()));
    }
    return AccessScope.memberScoped(unitIds);   // may be empty -> empty result set
}
```

- Society-wide detection: authority `ROLE_SUPER_ADMIN`, or any configured
  society-wide role/authority (e.g. `CHAIRMAN`, `SECRETARY`, or a
  `TRANSACTION_VIEW_ALL` authority). This predicate is centralized so the rule is
  applied identically for list and detail.
- Owner → units via existing `UnitRepository.findByOwnerId` (joins `unit_owners`).
- Tenant → unit via `Tenant.unit`.
- A member with no linked units yields an empty scope, which produces an empty
  result set (Req 2.5), not an error.

`AccessScope` is an immutable value object:

```java
record AccessScope(boolean societyWide, Set<Long> unitIds) {
    static AccessScope societyWide() { return new AccessScope(true, Set.of()); }
    static AccessScope memberScoped(Set<Long> ids) { return new AccessScope(false, ids); }
}
```

### TransactionSpecificationBuilder

Builds a single `Specification<MaintenancePayment>` by ANDing the access-scope
predicate with one predicate per active filter. Absent/blank filters contribute
no predicate (identity), which is how "no filter" behaves (Req 3.7, 4.2, 5.2,
7.4). This is the cleanest way to compose date-range, mode, status, unit,
payer-type, and reference filters dynamically without a combinatorial explosion of
repository finders.

```java
public Specification<MaintenancePayment> build(AccessScope scope, TransactionFilterRequest f) {
    return (root, query, cb) -> {
        List<Predicate> predicates = new ArrayList<>();

        // --- Access scope (Req 10.2 / 10.4) ---
        if (!scope.societyWide()) {
            if (scope.unitIds().isEmpty()) {
                predicates.add(cb.disjunction());           // always false -> empty result
            } else {
                predicates.add(root.get("unit").get("unitId").in(scope.unitIds()));
            }
        }

        // --- Date range (Req 3) ---
        if (f.getStartDate() != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("paymentDate"), f.getStartDate()));
        }
        if (f.getEndDate() != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get("paymentDate"), f.getEndDate()));
        }

        // --- Payment mode (Req 4.1) ---
        if (f.getPaymentMode() != null) {
            predicates.add(cb.equal(root.get("paymentMode"), f.getPaymentMode()));
        }

        // --- Status: OR within, AND with rest (Req 5.1 / 5.3) ---
        if (f.getStatuses() != null && !f.getStatuses().isEmpty()) {
            predicates.add(root.get("status").in(f.getStatuses()));
        }

        // --- Unit id filter (Req 6.1) ---
        if (f.getUnitId() != null) {
            predicates.add(cb.equal(root.get("unit").get("unitId"), f.getUnitId()));
        }

        // --- Payer type (Req 6.3) ---
        if (f.getPayerType() != null) {
            predicates.add(cb.equal(root.get("payerType"), f.getPayerType()));
        }

        // --- Unit-number search, case-insensitive contains (Req 6.4) ---
        if (hasText(f.getUnitSearch())) {
            predicates.add(cb.like(cb.lower(root.get("unit").get("unitNumber")),
                    "%" + f.getUnitSearch().toLowerCase() + "%"));
        }

        // --- Reference search: receiptNumber OR transactionId (Req 9.1) ---
        if (hasText(f.getReference())) {
            String like = "%" + f.getReference().toLowerCase() + "%";
            predicates.add(cb.or(
                    cb.like(cb.lower(root.get("receiptNumber")), like),
                    cb.like(cb.lower(root.get("transactionId")), like)));
        }

        return cb.and(predicates.toArray(new Predicate[0]));
    };
}
```

Every top-level predicate is joined with `cb.and(...)`, guaranteeing the logical
AND composition across all active filters (Req 7.1). Multi-value status uses
`in(...)` for OR-within-filter semantics (Req 5.3).

### Repository change

Extend the existing repository (no new persistence class, no schema change):

```java
public interface MaintenancePaymentRepository
        extends JpaRepository<MaintenancePayment, Long>,
                JpaSpecificationExecutor<MaintenancePayment> {
    // ... existing finders unchanged ...
}
```

`JpaSpecificationExecutor` supplies
`Page<MaintenancePayment> findAll(Specification<T>, Pageable)`, which the service
uses for the list query.

### Frontend components (Angular 17 standalone + Material)

```
frontend/src/app/features/transactions/
├── transaction-list/transaction-list.component.ts       // mat-table + mat-paginator, orchestrator
├── transaction-filter-panel/transaction-filter-panel.component.ts
├── transaction-detail-dialog/transaction-detail-dialog.component.ts
├── services/transaction.service.ts                      // HttpClient
└── models/transaction.models.ts                         // TS interfaces mirroring DTOs
```

- **TransactionListComponent** — owns the `mat-table` (columns: payment id, unit
  number, payer name, payer type, amount, payment date, payment mode, status,
  reference, receipt number — Req 1.2), a `mat-paginator` (page size options
  reflecting role: 25-based for admin, 50-based for member), an empty-state
  template (Req 1.3, 2.5, 3.6), and an error banner that leaves the current rows
  intact on failure (Req 1.7, 2.6). Row click opens the detail dialog.
- **TransactionFilterPanelComponent** — reactive form: `mat-date-range-input`
  (start/end), `mat-select` for payment mode, multi-`mat-select` for status,
  `mat-select` for payer type (admin only), unit search text, reference search
  text. Emits a `TransactionFilter` on apply/clear. Client-side guards mirror
  server validation (start ≤ end, max lengths) for fast feedback, but the server
  remains authoritative (Req 3.4, 6.5, 9.3). On a server validation error the
  panel shows the message and the list keeps its prior rows (Req 7.6).
- **TransactionDetailDialogComponent** — `MatDialog` showing all detail fields
  (Req 8.1) with an explicit placeholder (e.g. "—") for null fields; verification
  block shown when verified (Req 8.3); reversal block shown when reversed
  (Req 8.4). On 403 shows "access denied", on 404 shows "not found", on other
  errors shows "could not load", each without partial fields (Req 8.5/8.6/8.7).
- **TransactionService** — `HttpClient` calls to `/transactions` and
  `/transactions/{id}`, translating the filter object into query params (status
  as repeated params). Role awareness (admin vs member) is derived from the
  auth/roles the app already holds; members simply do not render the unit/payer
  admin-only filters, and the backend enforces scope regardless.

## Data Models

### Source entity (existing, unchanged)

`com.society.module.maintenance.entity.MaintenancePayment` (table
`maintenance_payments`) is the sole data source. Relevant fields: `paymentId`,
`unit` (→ `unitNumber`), `amount`, `originalAmount`, `discountAmount`,
`discountPercent`, `paymentDate`, `paymentMode` (enum), `transactionId`,
`payerName`, `payerType` (String `OWNER`/`TENANT`), `receiptNumber`, `status`
(enum), `remarks`, `verifiedOn`, `verifiedBy`, `reversedOn`, `reversedBy`,
`reversalReason`, plus `BaseEntity` audit fields.

### TransactionFilterRequest (request DTO)

```java
@Data
public class TransactionFilterRequest {
    private LocalDate startDate;                    // Req 3
    private LocalDate endDate;                      // Req 3
    private MaintenancePayment.PaymentMode paymentMode;   // Req 4 (bind failure -> validation error)
    private List<MaintenancePayment.PaymentStatus> statuses; // Req 5 (repeatable param)
    private String payerType;                       // Req 6 (OWNER/TENANT)
    private Long unitId;                            // Req 6.1
    private String unitSearch;                      // Req 6.4 (1..50)
    private String reference;                       // Req 9 (1..100)
}
```

Enum params bind by name; an unrecognized value is translated to a
`BusinessException` (Req 4.3, Req 5.4) rather than a raw bind failure — handled in
the controller/service boundary so the error message names the offending value.

### TransactionSummaryDTO (list response element)

Carries exactly the list fields required by Req 1.2. A dedicated summary DTO is
introduced (rather than reusing `PaymentDTO` wholesale) so the list payload stays
lean and stable; `PaymentDTO` remains the maintenance module's write-side DTO.

```java
@Data @Builder
public class TransactionSummaryDTO {
    private Long paymentId;
    private String unitNumber;
    private String payerName;
    private String payerType;
    private BigDecimal amount;
    private LocalDate paymentDate;
    private String paymentMode;
    private String status;
    private String transactionId;   // transaction reference identifier
    private String receiptNumber;
}
```

### TransactionDetailDTO (detail response)

Superset covering all Req 8 fields. Fields may be null; the frontend renders an
explicit placeholder (Req 8.1). Shape mirrors the existing `PaymentDTO` plus the
discount/original fields.

```java
@Data @Builder
public class TransactionDetailDTO {
    private Long paymentId;
    private String unitNumber;
    private String payerName;
    private String payerType;
    private BigDecimal amount;
    private BigDecimal originalAmount;
    private BigDecimal discountAmount;
    private BigDecimal discountPercent;
    private LocalDate paymentDate;
    private String paymentMode;
    private String transactionId;
    private String receiptNumber;
    private String status;
    private String remarks;
    private LocalDateTime verifiedOn;   // Req 8.3
    private String verifiedBy;          // Req 8.3
    private LocalDateTime reversedOn;   // Req 8.4
    private String reversedBy;          // Req 8.4
    private String reversalReason;      // Req 8.4
}
```

### Frontend TypeScript models (mirror DTOs)

```typescript
export type PaymentMode =
  'CASHFREE_LINK'|'CASHFREE_QR'|'RAZORPAY'|'UPI'|'GPAY'|'PHONEPE'|
  'NEFT'|'RTGS'|'IMPS'|'CHEQUE'|'CASH'|'BANK_TRANSFER';
export type TransactionStatus = 'PENDING'|'SUCCESS'|'FAILED'|'VERIFIED'|'REVERSED';
export type PayerType = 'OWNER'|'TENANT';

export interface TransactionFilter {
  startDate?: string; endDate?: string;
  paymentMode?: PaymentMode; statuses?: TransactionStatus[];
  payerType?: PayerType; unitId?: number; unitSearch?: string; reference?: string;
  page?: number; size?: number;
}
export interface TransactionSummary {
  paymentId: number; unitNumber: string; payerName: string; payerType: PayerType;
  amount: number; paymentDate: string; paymentMode: PaymentMode;
  status: TransactionStatus; transactionId: string; receiptNumber: string;
}
export interface TransactionDetail extends TransactionSummary {
  originalAmount?: number; discountAmount?: number; discountPercent?: number;
  remarks?: string; verifiedOn?: string; verifiedBy?: string;
  reversedOn?: string; reversedBy?: string; reversalReason?: string;
}
// Mirrors PagedResponse<T>
export interface PagedResponse<T> {
  content: T[]; page: number; size: number;
  totalElements: number; totalPages: number; last: boolean;
}
// Mirrors ApiResponse<T>
export interface ApiResponse<T> {
  success: boolean; message: string; data: T; timestamp: string;
}
```

## API Contract

All responses use the shared `ApiResponse<T>` envelope
(`{ success, message, data, timestamp }`). List data is a `PagedResponse<T>`
(`{ content, page, size, totalElements, totalPages, last }`).

### GET /transactions

Request params (all optional except paging defaults):

| Param | Type | Notes |
|---|---|---|
| `startDate` | ISO date | inclusive lower bound (Req 3.1/3.2) |
| `endDate` | ISO date | inclusive upper bound (Req 3.1/3.3) |
| `paymentMode` | enum name | one of the 12 modes (Req 4) |
| `status` | enum name(s), repeatable | OR-combined (Req 5.3) |
| `payerType` | `OWNER`\|`TENANT` | admin filter (Req 6.3) |
| `unitId` | long | must exist (Req 6.1/6.2) |
| `unitSearch` | string 1–50 | case-insensitive contains (Req 6.4/6.5) |
| `reference` | string 1–100 | receipt/txn contains (Req 9) |
| `page` | int, default 0 | zero-based |
| `size` | int | default 25 admin / 50 member, clamped [1,100] (Req 1.4, 2.1) |

Success `200`:

```json
{
  "success": true,
  "message": "Operation successful",
  "data": {
    "content": [
      {
        "paymentId": 1042, "unitNumber": "A-101", "payerName": "R. Sharma",
        "payerType": "OWNER", "amount": 2450.00, "paymentDate": "2024-05-18",
        "paymentMode": "UPI", "status": "SUCCESS",
        "transactionId": "TXN-88213", "receiptNumber": "RCPT-000342"
      }
    ],
    "page": 0, "size": 25, "totalElements": 137, "totalPages": 6, "last": false
  },
  "timestamp": "2024-05-20T10:15:30"
}
```

Empty / out-of-range page (Req 1.6, 3.6, 4.4, 5.5, 7.5, 9.4) → `200` with empty
`content` and accurate `totalElements` / `page` / `size` / `totalPages`.

### GET /transactions/{paymentId}

Success `200`: `ApiResponse<TransactionDetailDTO>`.

### Error responses

| Condition | Status | Envelope | Requirements |
|---|---|---|---|
| Unauthenticated | 401 | Security entry point | 10.1 |
| Authenticated, lacks `TRANSACTION_VIEW`/`SUPER_ADMIN` | 403 | `ApiResponse.error` | 10.5 |
| Member requests unit/transaction outside scope | 403 | `ApiResponse.error` | 2.3, 8.5, 10.3 |
| Invalid date range / date value | 400 | `ApiResponse.error` | 3.4, 3.5 |
| Invalid payment mode | 400 | `ApiResponse.error` | 4.3 |
| Invalid status value | 400 | `ApiResponse.error` | 5.4 |
| Invalid payer type | 400 | `ApiResponse.error` | 6.6 |
| Unit id not found (filter) | 400 | `ApiResponse.error` | 6.2 |
| Unit search / reference too long | 400 | `ApiResponse.error` | 6.5, 9.3 |
| Detail id not found | 404 | `ApiResponse.error` | 8.6 |
| Data-source failure | 500 | `ApiResponse.error` | 1.7, 2.6, 8.7 |

## Correctness Properties

_A property is a characteristic or behavior that should hold true across all valid
executions of a system — essentially, a formal statement about what the system
should do. Properties serve as the bridge between human-readable specifications and
machine-verifiable correctness guarantees._

The transaction feature is a read model with clear input→output behavior over
generated payment sets, filters, and access scopes — a good fit for
property-based testing. The properties below were derived from the prework
analysis and consolidated to remove redundancy (e.g. the same ordering and
access-scope rules stated at both admin and member scope are folded into single
universal properties). Non-functional criteria (performance 2.2/6.1/8.2),
framework-boundary security (10.1/10.5), and pure UI display/error behavior
(1.3/1.7/2.5/2.6/8.7) are covered by integration, example, or component tests in
the Testing Strategy rather than as properties.

### Property 1: Result ordering is stable and descending

_For all_ result pages produced by the read model, for any access scope and any
filter set, the returned transactions are ordered by payment date descending and,
among transactions sharing the same payment date, by payment identifier
descending.

**Validates: Requirements 1.1, 2.4**

### Property 2: Member results never escape access scope

_For all_ member access scopes (a set of linked unit IDs) and any set of payments
and any filter set, every transaction returned in the list belongs to a unit
within that member's scope, and no in-scope transaction that satisfies the filters
is omitted. For an administrator (society-wide) scope, no unit restriction is
imposed.

**Validates: Requirements 2.1, 7.2, 7.3, 10.2, 10.4**

### Property 3: Detail access respects scope

_For all_ members and any transaction, requesting the detail of a transaction
whose unit is outside the member's scope is denied and returns no transaction
data; requesting an in-scope (or, for an administrator, any existing) transaction
returns that transaction's detail.

**Validates: Requirements 2.3, 8.5, 10.3**

### Property 4: Filters compose by logical AND with absent filters as identity

_For all_ payment sets and any filter set, the returned transactions are exactly
those (within access scope) that satisfy every active filter simultaneously
(logical AND); a filter that is absent, empty, or whitespace-only imposes no
restriction, so an empty filter set returns the entire scoped set.

**Validates: Requirements 3.7, 4.2, 5.2, 7.1, 7.4, 9.2**

### Property 5: Date-range filter selects an inclusive interval

_For all_ payment sets and any start date and/or end date, every returned
transaction has a payment date on or after the start date (when provided) and on
or before the end date (when provided), and every scoped transaction within those
bounds is included.

**Validates: Requirements 3.1, 3.2, 3.3**

### Property 6: Invalid date range is rejected

_For all_ start/end pairs where the start date is strictly later than the end
date, the read model raises a validation error and returns no result set.

**Validates: Requirements 3.4**

### Property 7: Payment-mode filter selects exactly one mode

_For all_ payment sets and any recognized payment mode, every returned transaction
has that payment mode, and every scoped transaction with that mode (satisfying
other filters) is included.

**Validates: Requirements 4.1**

### Property 8: Status filter selects the chosen status set

_For all_ payment sets and any non-empty set of recognized statuses, every
returned transaction has a status that is a member of the selected set (logical OR
within the status filter), and every scoped transaction whose status is in the set
(satisfying other filters) is included.

**Validates: Requirements 5.1, 5.3**

### Property 9: Unit-id filter selects a single unit

_For all_ payment sets and any existing unit id, every returned transaction
belongs to that unit.

**Validates: Requirements 6.1**

### Property 10: Payer-type filter selects a single payer type

_For all_ payment sets and any payer type in {OWNER, TENANT}, every returned
transaction has that payer type.

**Validates: Requirements 6.3**

### Property 11: Unit-search matches unit number case-insensitively

_For all_ payment sets and any unit search term of length 1–50, every returned
transaction's unit number contains the search term under case-insensitive
matching, and every scoped transaction whose unit number contains the term is
included.

**Validates: Requirements 6.4**

### Property 12: Reference search matches receipt or transaction id case-insensitively

_For all_ payment sets and any reference term of length 1–100, every returned
transaction has a receipt number or transaction reference identifier that contains
the term under case-insensitive substring matching, subject to access scope.

**Validates: Requirements 9.1**

### Property 13: Effective page size is bounded

_For all_ requested page sizes (including absent, zero, negative, or greater than
100), the effective page size used for the query lies within [1, 100], defaulting
to the role default (25 administrator / 50 member) when no valid size is supplied,
and the returned page contains at most that many transactions.

**Validates: Requirements 1.4, 2.1**

### Property 14: Pagination metadata is internally consistent

_For all_ queries, the paged response satisfies: `totalPages == ceil(totalElements
/ size)` (with `totalPages == 0` when `totalElements == 0`), `content.size() <=
size`, and `last == true` iff the current page is the final page; requesting a
page number outside the valid range yields empty content while preserving the
correct `totalElements`, `page`, `size`, and `totalPages`.

**Validates: Requirements 1.5, 1.6**

### Property 15: Summary mapping is complete

_For all_ payments, the mapped `TransactionSummaryDTO` exposes the payment
identifier, unit number, payer name, payer type, amount, payment date, payment
mode, status, transaction reference identifier, and receipt number, each equal to
the corresponding source value.

**Validates: Requirements 1.2**

### Property 16: Detail mapping is complete and preserves nulls

_For all_ payments, the mapped `TransactionDetailDTO` exposes every detail field
(payer name, payer type, unit number, amount, original amount, discount amount,
payment date, payment mode, transaction reference identifier, receipt number,
status, remarks, and — when present — verification timestamp/verifier and reversal
timestamp/reverser/reason), preserving null values rather than dropping the field,
so the frontend can render an explicit placeholder.

**Validates: Requirements 8.1, 8.3, 8.4**

## Error Handling

Errors are raised as the existing exception types and translated by
`GlobalExceptionHandler` into `ApiResponse.error(message)` with the appropriate
HTTP status. The frontend maps each status to the required user-facing behavior.

| Requirement | Trigger | Backend | HTTP | Frontend behavior |
|---|---|---|---|---|
| 3.4, 3.5 | start > end / unparsable date | `BusinessException` | 400 | show validation message, keep prior list & filter |
| 4.3 | unrecognized payment mode | `BusinessException` | 400 | show message naming the value, keep prior list |
| 5.4 | unrecognized status | `BusinessException` | 400 | show message, keep prior list |
| 6.2 | unit id not found (filter) | `BusinessException` | 400 | show "unit not found", keep prior list |
| 6.5, 9.3 | unit search > 50 / reference > 100 | `BusinessException` | 400 | show length message, keep prior list |
| 6.6 | invalid payer type | `BusinessException` | 400 | show message, keep prior list |
| 7.6 | any invalid filter value | `BusinessException` (names value) | 400 | preserve previously applied filter set |
| 2.3, 8.5, 10.3 | member accesses out-of-scope unit/transaction | `AccessDeniedException` | 403 | show "access denied", no detail fields |
| 8.6 | detail id does not exist | `ResourceNotFoundException` | 404 | show "transaction not found", no fields |
| 1.7, 2.6, 8.7 | data-source / service failure | propagated → handler | 500 | show "could not load", retain prior view unchanged |
| 10.1 | unauthenticated | security entry point | 401 | redirect to login, no data |
| 10.5 | authenticated, no authority | `@PreAuthorize` denial | 403 | show "access not permitted", no data |

Key rules:

- **Validation errors run no query.** All filter validation happens before the
  specification is built, so rejected filters never touch the data source and the
  prior result set is preserved (Req 3.4, 4.3, 5.4, 6.2, 6.5, 6.6, 7.6, 9.3).
- **Empty result is not an error.** Zero matches (Req 1.6, 3.6, 4.4, 5.5, 7.5,
  9.4) return `200` with empty `content` and `totalElements = 0`.
- **Non-partial detail.** Any detail error path returns no partial fields; the
  dialog renders the error message only (Req 8.5, 8.6, 8.7).
- `GlobalExceptionHandler` requires an `AccessDeniedException` mapping to 403 with
  `ApiResponse.error`; if not already present it is added for this feature.

## Security and Access Control

- **Authentication** is enforced by the existing Spring Security chain;
  unauthenticated requests never reach the controller (Req 10.1 → 401).
- **Authorization** is enforced at the method boundary with
  `@PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('TRANSACTION_VIEW')")` on
  both endpoints; an authenticated user lacking both is denied (Req 10.5 → 403).
  A new `TRANSACTION_VIEW` permission is seeded (via the existing
  permission/role setup) and granted to member-facing roles; society-wide roles
  (`SUPER_ADMIN`, and configured committee roles such as `CHAIRMAN`/`SECRETARY`)
  additionally receive society-wide scope.
- **Data scoping** is enforced in the service via `AccessScopeResolver`, not in
  the query params — a member cannot widen their scope by crafting a `unitId`:
  - Society-wide caller → no unit predicate (Req 10.4, 7.2).
  - Member caller → results constrained to `IN (resolved unit IDs)` (Req 10.2,
    7.3); an out-of-scope `unitId` filter or out-of-scope detail id is denied
    (Req 2.3, 8.5, 10.3).
- **Principal → units** resolution: `Authentication.getName()` → `User` (via
  `UserRepository.findByUsername`) → `ownerId` (→ `UnitRepository.findByOwnerId`)
  and/or `tenantId` (→ `Tenant.unit`). A member with no linked units gets an empty
  scope → empty result (Req 2.5), never another member's data.

## Testing Strategy

A dual approach combines property-based tests (universal logic of the read model)
with example, integration, and component tests (framework boundaries, UI, and
performance).

### Property-based tests (backend)

- Library: **jqwik** (JUnit 5 property testing for Java). Properties are not
  implemented from scratch.
- Each of Properties 1–16 is implemented by a **single** property-based test at
  the `TransactionSpecificationBuilder` / `TransactionService` level, using an
  in-memory list model (or an H2-backed repository) so 100+ iterations stay cheap;
  the specification is validated against a reference in-memory filter to confirm
  equivalence (model-based testing for Property 4).
- Minimum **100 iterations** per property (`@Property(tries = 100)` or higher).
- Generators produce random `MaintenancePayment` sets (varying unit, date, mode,
  status, payer type, receipt/transaction ids including case variants and empty
  values), random filter sets, and random access scopes. Edge cases are folded
  into generators: out-of-range pages (1.6), no-match filters (3.6/4.4/5.5/7.5/9.4),
  boundary lengths (6.5/9.3), invalid enum values (4.3/5.4/6.6), unknown unit id
  (6.2), and malformed dates (3.5).
- Each test carries a tag comment referencing its design property, e.g.:
  `// Feature: transaction-page, Property 4: Filters compose by logical AND with absent filters as identity`

### Example / unit tests (backend)

- Enum-membership and length validations produce the correct `BusinessException`
  message naming the offending value (4.3, 5.4, 6.6, 6.5, 9.3, 6.2, 7.6).
- `AccessScopeResolver` maps owner-only, tenant-only, both, and no-link users to
  the expected unit sets, and society-wide roles to the unrestricted scope.
- Detail 404 for a non-existent id (8.6).

### Integration tests (backend)

- `@SpringBootTest` + MockMvc: unauthenticated → 401 (10.1); authenticated without
  `TRANSACTION_VIEW`/`SUPER_ADMIN` → 403 (10.5); member vs admin scope end-to-end
  against seeded data.
- Performance smoke: member list, admin unit filter, and detail return within the
  stated latency budgets on a representative dataset (2.2, 6.1, 8.2).

### Frontend component tests (Angular)

- List renders required columns and paginator (1.2), empty-state on empty content
  (1.3, 2.5, 3.6), error banner retaining prior rows on service failure (1.7,
  2.6).
- Filter panel emits AND-combined filter, mirrors server validation for start≤end
  and max lengths, and preserves prior filter on server rejection (7.6).
- Detail dialog renders all fields with explicit placeholders for nulls (8.1),
  verification block when verified (8.3), reversal block when reversed (8.4), and
  the correct error message (without fields) for 403/404/500 (8.5, 8.6, 8.7).
