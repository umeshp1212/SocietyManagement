# Implementation Plan: Owner Email

## Overview

This plan implements the Owner Email feature incrementally, backend-first. It starts with the data contracts (DTOs and enums), then the pure `OwnerEmailTemplateBuilder` and its property tests, then `OwnerEmailServiceImpl` (recipient resolution + best-effort send) with its property tests, then the guarded `OwnerEmailController` with security/validation tests, then permission provisioning (`data.sql` + `DataInitializer`). It then moves to the frontend: shared models, the `OwnerService` method, the routed `OwnerEmailComponent`, and the permission-gated entry-point button, followed by the frontend and integration/wiring tests. Every step builds on the previous ones and ends with wiring the feature end-to-end.

Backend: Spring Boot (`com.society`), jqwik for property-based tests (already present). Frontend: Angular standalone + Angular Material.

## Tasks

- [x] 1. Define backend data contracts (DTOs and enums)
  - [x] 1.1 Create `RecipientScope` and `NotEmailedReason` enums
    - Create `RecipientScope` (`ALL`, `SELECTED`) in `com.society.module.owner.dto`
    - Create `NotEmailedReason` (`MISSING_EMAIL`, `SEND_FAILURE`, `MAIL_NOT_CONFIGURED`) in `com.society.module.owner.dto`
    - _Requirements: 2.1, 5.3, 7.2, 8.1_

  - [x] 1.2 Create request and report DTOs
    - Create `SendOwnerEmailRequest` in `com.society.module.owner.dto` with `@NotNull recipientScope`, `List<Long> ownerIds`, `@NotBlank @Size(max=200) subject`, `@NotBlank @Size(max=10000) body`
    - Create `NotEmailedEntry` (`ownerId`, `ownerName`, `reason`) with `@Data @Builder`
    - Create `SendReportDTO` (`totalAttempted`, `sentCount`, `notEmailedCount`, `mailConfigured`, `List<NotEmailedEntry> notEmailed`) with `@Data @Builder`
    - _Requirements: 2.3, 3.1, 3.5, 3.6, 5.3, 5.4, 7.1, 7.2, 8.1_

- [x] 2. Implement the templated email assembly
  - [x] 2.1 Implement `OwnerEmailTemplateBuilder`
    - Create `OwnerEmailTemplateBuilder` (`@Component`) in `com.society.module.owner.service`
    - Implement `buildBody(SocietySettings, subject, message)` assembling header → body → footer in that fixed order (header from society name/address/registration number/phone/email; body from subject + message; footer from chairman/secretary/treasurer names)
    - Implement `buildSubject(subject)` returning the user-entered subject
    - Throw `BusinessException` identifying the offending field when subject/message is empty/whitespace or a required header/footer settings field is missing/blank
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7_

  - [x] 2.2 Write property test for template ordering
    - **Feature: owner-email, Property 1: Template ordering is always header-then-body-then-footer**
    - jqwik, min 100 tries; generated valid `SocietySettings` + non-empty subject/message; assert header precedes body precedes footer
    - **Validates: Requirements 4.1**

  - [x] 2.3 Write property test for header identity fields
    - **Feature: owner-email, Property 2: Header contains all required society identity fields**
    - jqwik, min 100 tries; assert assembled header contains society name, address, registration number, phone, email
    - **Validates: Requirements 4.2**

  - [x] 2.4 Write property test for body content
    - **Feature: owner-email, Property 3: Body contains the user subject and message**
    - jqwik, min 100 tries; assert body contains the user-entered subject and message; subject line equals user subject
    - **Validates: Requirements 4.3, 4.5**

  - [x] 2.5 Write property test for footer signatory names
    - **Feature: owner-email, Property 4: Footer contains the signatory names**
    - jqwik, min 100 tries; assert footer contains chairman, secretary, treasurer names
    - **Validates: Requirements 4.4**

  - [x] 2.6 Write property test for assembly rejection
    - **Feature: owner-email, Property 5: Assembly rejects missing user or settings fields**
    - jqwik, min 100 tries; generators for empty/whitespace subject, empty/whitespace message, and missing/blank required settings fields; assert `BusinessException` thrown identifying the field and no email content produced
    - **Validates: Requirements 4.6, 4.7**

- [x] 3. Implement the owner email service
  - [x] 3.1 Add active-owner and by-id resolution to `OwnerRepository`
    - Ensure `OwnerRepository` exposes the active-owner query used by `getActiveOwnersList()` for the `ALL` scope and `findAllById(ownerIds)` for the `SELECTED` scope
    - _Requirements: 2.3, 2.4_

  - [x] 3.2 Create `OwnerEmailService` interface
    - Create `OwnerEmailService` in `com.society.module.owner.service` with `SendReportDTO sendOwnerEmail(SendOwnerEmailRequest request)`
    - _Requirements: 5.4, 7.1_

  - [x] 3.3 Implement `OwnerEmailServiceImpl`
    - Create `OwnerEmailServiceImpl` (`@Service`, `@RequiredArgsConstructor`, `@Slf4j`) with `@Autowired(required=false) JavaMailSender`, `@Value("${spring.mail.username:noreply@society.com}") fromEmail`, `OwnerRepository`, `SocietySettingsService`, `OwnerEmailTemplateBuilder`
    - Resolve recipients by scope; assemble template via builder from `SocietySettingsService.getSettings()`
    - Classify null/blank-email owners as `MISSING_EMAIL` (never sent to); best-effort per-recipient send with `MimeMessageHelper` using `fromEmail`, individual try/catch logging + `SEND_FAILURE`, continue on failure
    - When `mailSender == null`: send nothing, return report with `mailConfigured=false`, `sentCount=0`, every attempted recipient recorded `MAIL_NOT_CONFIGURED`
    - Build `SendReportDTO` preserving `sentCount + notEmailedCount == totalAttempted == resolvedRecipientCount` and `notEmailed.size() == notEmailedCount`; handle empty set (all-zero) and all-invalid (sent=0) edge cases
    - _Requirements: 2.3, 2.4, 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 7.1, 7.2, 7.3, 8.1, 8.2, 8.3, 8.4, 8.5_

  - [x] 3.4 Write property test for count reconciliation
    - **Feature: owner-email, Property 6: Send report counts reconcile with attempted recipients**
    - jqwik, min 100 tries; mocked `JavaMailSender`, generated recipient sets; assert `sentCount + notEmailedCount == totalAttempted` and `notEmailed.size() == notEmailedCount`
    - **Validates: Requirements 5.4, 7.1, 8.4**

  - [x] 3.5 Write property test for valid-only sending
    - **Feature: owner-email, Property 7: Only valid recipients are sent to; invalid recipients are excluded and reported**
    - jqwik, min 100 tries; mocked `JavaMailSender`, mixed valid/invalid-email owners; assert send invoked only for valid recipients, never for invalid, and each invalid recorded `MISSING_EMAIL`
    - **Validates: Requirements 5.1, 5.2, 5.3**

  - [x] 3.6 Write property test for not-emailed entry completeness
    - **Feature: owner-email, Property 8: Every not-emailed recipient carries an identity and a categorized reason**
    - jqwik, min 100 tries; mocked `JavaMailSender`; assert each not-emailed entry has ownerId, ownerName, and a categorized reason
    - **Validates: Requirements 5.3, 7.2**

  - [x] 3.7 Write property test for per-recipient failure isolation
    - **Feature: owner-email, Property 10: A per-recipient send failure does not halt processing of the others**
    - jqwik, min 100 tries; mocked `JavaMailSender` with a generated subset of sends throwing; assert every remaining valid recipient is still attempted, failures recorded `SEND_FAILURE`, count invariant preserved
    - **Validates: Requirements 8.3, 8.5**

  - [x] 3.8 Write property test for mail-absent behavior
    - **Feature: owner-email, Property 9: Mail-absent yields a graceful, zero-sent report with no error**
    - jqwik, min 100 tries; `mailSender = null`; assert `sentCount == 0`, `mailConfigured == false`, `notEmailedCount == totalAttempted`, no error propagated
    - **Validates: Requirements 8.1, 8.2**

  - [x] 3.9 Write property test for recipient scope resolution
    - **Feature: owner-email, Property 11: Recipient scope resolution matches the requested scope**
    - jqwik, min 100 tries; generated active-owner sets and selected-id subsets; assert `SELECTED` resolves exactly the provided IDs and `ALL` resolves exactly the active-owner set
    - **Validates: Requirements 2.3, 2.4**

  - [x] 3.10 Write unit tests for service edge cases
    - No recipients → all-zero report (Req 5.6); all-invalid → sent=0, notEmailed=attempted (Req 5.5); zero successes → sent=0 (Req 7.3); `MimeMessageHelper` uses `fromEmail`
    - _Requirements: 5.5, 5.6, 7.3_

- [x] 4. Implement and secure the controller
  - [x] 4.1 Implement `OwnerEmailController`
    - Create `OwnerEmailController` (`@RestController`, `@RequestMapping("/owners/email")`, `@RequiredArgsConstructor`) in `com.society.module.owner.controller`
    - `POST /send` with `@Valid @RequestBody SendOwnerEmailRequest`, `@PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('OWNER_EMAIL_SEND')")`, returning `ResponseEntity<ApiResponse<SendReportDTO>>`
    - Delegate to `OwnerEmailService` and wrap with `ApiResponse.success(...)`
    - _Requirements: 3.5, 3.6, 6.1, 6.2, 6.3_

  - [x] 4.2 Write property test for server-side validation boundary
    - **Feature: owner-email, Property 12: Server-side validation rejects invalid subject/body without side effects**
    - jqwik, min 100 tries; generated empty/whitespace/over-length subjects (>200) and bodies (>10000); assert validation error and the service is never invoked (no partial record)
    - **Validates: Requirements 3.5, 3.6**

- [x] 5. Provision the OWNER_EMAIL_SEND permission
  - [x] 5.1 Seed permission and role grants in `data.sql`
    - Add `OWNER_EMAIL_SEND` permission row (module `OWNER`) via `INSERT IGNORE`
    - By-name grant to `CHAIRMAN`, `SECRETARY`, `COMMITTEE_MEMBER`; re-run SUPER_ADMIN grant-all to pick up the new permission
    - _Requirements: 6.3_

  - [x] 5.2 Add idempotent `seedOwnerEmailSendPermission()` to `DataInitializer`
    - Mirror `seedTransactionViewPermission`/`seedPaymentReassignPermission`; ensure the permission exists and is granted to committee/admin roles on every startup
    - _Requirements: 6.3_

- [x] 6. Checkpoint - Ensure all backend tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 7. Add frontend models and service method
  - [x] 7.1 Add owner-email models to `owner.model.ts`
    - Add `RecipientScope`, `NotEmailedReason`, `SendOwnerEmailRequest`, `NotEmailedEntry`, `SendReport` to `@core/models/owner.model.ts`
    - _Requirements: 2.3, 3.1, 5.3, 7.1, 7.2, 8.1_

  - [x] 7.2 Add `sendOwnerEmail` to `OwnerService`
    - Add `sendOwnerEmail(request): Observable<ApiResponse<SendReport>>` posting to `/owners/email/send` in `@core/services/owner.service.ts`; reuse existing `getActiveOwnersList()`
    - _Requirements: 2.4, 7.1_

- [x] 8. Build the composition view and wire the entry point
  - [x] 8.1 Implement `OwnerEmailComponent`
    - Create standalone `OwnerEmailComponent` in `frontend/src/app/modules/owner/owner-email/` with Angular Material imports
    - Recipient scope selector (ALL default); checkbox `MatTable` of active owners (via `getActiveOwnersList()`, supporting up to 10,000) when SELECTED
    - Subject input (1–200) and body textarea (1–10000) with client-side validation and inline messages
    - Block submission with retained scope/content and appropriate message on: SELECTED with zero selected, ALL with zero active owners, empty/whitespace subject, empty/whitespace body, over-length subject/body
    - Submit via `OwnerService.sendOwnerEmail` with loading indicator and RxJS `timeout(30000)`; on timeout show error, retain content/selection, allow retry
    - Render results panel (within 2s) showing `sentCount`, `notEmailedCount`, and a not-emailed table (name/id + reason) when present
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 3.1, 3.2, 3.3, 3.4, 7.4, 7.5, 7.6_

  - [x] 8.2 Add the `email` route in `owner.routes.ts`
    - Add `path: 'email'` lazy-loading `OwnerEmailComponent` before the catch-all `:id` route
    - _Requirements: 1.3, 1.4_

  - [x] 8.3 Add the permission-gated Email button in `owner-list.component.ts`
    - Add an Email action button beside "Manage Unit Owners" with `routerLink="/owners/email"`, gated by `*ngIf="hasPermission('OWNER_EMAIL_SEND')"`
    - _Requirements: 1.1, 1.2, 1.3_

  - [x] 8.4 Write component tests for entry point, selection, composition, and results
    - Email button visible/hidden by permission (1.1, 1.2); default scope ALL (2.1); selected-with-none blocked (2.5); all-scope zero owners blocked (2.6); subject/body bounds + messages (3.1–3.4); results panel counts + not-emailed list (7.4, 7.5); 30s timeout + retry (7.6); composition-view load-failure handling (1.4)
    - _Requirements: 1.1, 1.2, 1.4, 2.1, 2.5, 2.6, 3.1, 3.2, 3.3, 3.4, 7.4, 7.5, 7.6_

  - [x] 8.5 Write client-side validation property test
    - **Feature: owner-email, Property 13: Client-side validation rejects invalid subject/body**
    - Angular test using generated strings (whitespace-only, over-length) via fast-check if available, otherwise a parameterized table of boundary cases; assert submission blocked, message shown, scope/content retained
    - **Validates: Requirements 3.2, 3.3, 3.4**

- [x] 9. Integration and wiring tests
  - [x] 9.1 Write security integration tests
    - `@SpringBootTest` + `MockMvc` against the seeded DB: unauthenticated → 401 (6.1); authenticated without permission → 403 (6.2); with `OWNER_EMAIL_SEND` → processed (6.3); rejection recorded in audit log (6.4)
    - _Requirements: 6.1, 6.2, 6.3, 6.4_

  - [x] 9.2 Write permission-provisioning startup test
    - Assert `DataInitializer` creates `OWNER_EMAIL_SEND` and grants it to the intended roles
    - _Requirements: 6.3_

  - [x] 9.3 Write end-to-end wiring test
    - Mocked/greenmail mail server: verify header→body→footer content reaches the transport and a partial-failure produces the expected report shape
    - _Requirements: 4.1, 7.1, 8.3_

- [x] 10. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional test-writing sub-tasks and can be skipped for a faster MVP.
- Each task references specific requirement clauses for traceability.
- Checkpoints ensure incremental validation between backend and frontend phases.
- Property tests (jqwik backend, min 100 tries each) validate the 13 universal correctness properties; unit, component, and integration tests cover edge cases, security, and infrastructure wiring.
- The design uses a specific language stack (Java backend, TypeScript/Angular frontend), so no implementation-language selection was required.

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "5.1"] },
    { "id": 1, "tasks": ["1.2", "3.1", "5.2", "7.1"] },
    { "id": 2, "tasks": ["2.1", "3.2", "7.2"] },
    { "id": 3, "tasks": ["2.2", "2.3", "2.4", "2.5", "2.6", "3.3", "8.2", "8.3"] },
    { "id": 4, "tasks": ["3.4", "3.5", "3.6", "3.7", "3.8", "3.9", "3.10", "4.1", "8.1"] },
    { "id": 5, "tasks": ["4.2", "8.4", "8.5", "9.1", "9.2", "9.3"] }
  ]
}
```
