# Implementation Plan

- [x] 1. Write bug condition exploration test (BEFORE implementing the fix)
  - **Property 1: Bug Condition** - Multipart JSON Part Accepted Regardless of Content-Type
  - **CRITICAL**: This test MUST FAIL on the unfixed `OwnerEmailController` - failure confirms the bug exists
  - **DO NOT attempt to fix the test or the code when it fails**
  - **NOTE**: This test encodes the expected behavior - it will validate the fix when it passes after implementation
  - **GOAL**: Surface counterexamples demonstrating that a typed `request` part cannot bind to `@RequestParam("request") String`
  - **Scoped PBT Approach**: This is a deterministic binding defect. Scope the property to the concrete failing case(s): a `multipart/form-data` `POST /api/owners/email/send` whose `request` part is a `MockMultipartFile` carrying `Content-Type: application/json` (reproducing the frontend `Blob`), across attachment variations (one, multiple, none)
  - Use `MockMvc` with `multipart("/api/owners/email/send")` and a `MockMultipartFile("request", ..., "application/json", jsonBytes)`; mock `OwnerEmailService`
  - Bug Condition (from design): `isBugCondition(input)` = `input.contentType = MULTIPART_FORM_DATA AND input.requestPart.hasContentType = true`
  - The assertions should match Expected Behavior (Property 1): no type-conversion error, payload deserialised and validated, and (when valid) email dispatched with attachments returning `SendReportDTO` in the `ApiResponse` envelope
  - Run test on UNFIXED code
  - **EXPECTED OUTCOME**: Test FAILS with `500 Internal Server Error` and message `Failed to convert value of type '...StandardMultipartFile' to required type 'java.lang.String'` (this is correct - it proves the bug exists)
  - Document counterexamples found: e.g. typed `request` part + one attachment → 500; typed `request` part + multiple attachments → 500; typed `request` part + no attachments → 500 (binding fails before attachment handling); curl-style `;type=application/json` part → 500
  - Mark task complete when the test is written, run, and the failure is documented
  - _Requirements: 1.1, 1.2, 1.3, 2.1, 2.2, 2.3_

- [x] 2. Write preservation property tests (BEFORE implementing the fix)
  - **Property 2: Preservation** - Non-Buggy Inputs Behave Identically
  - **IMPORTANT**: Follow the observation-first methodology - observe behavior on UNFIXED code, then encode it
  - Non-bug condition (from design): `isBugCondition(input) = false` - the JSON-only path, unauthorised callers, no-attachment multipart requests, and payloads that fail deserialisation or validation
  - Observe on UNFIXED code and record actual outputs:
    - `application/json` request with a valid payload deserialises, `@Valid`-validates, and dispatches via `sendOwnerEmail` (record the `ApiResponse`/`SendReportDTO` result) (3.1)
    - Caller lacking `SUPER_ADMIN` role or `OWNER_EMAIL_SEND` authority is rejected by the `@PreAuthorize` guard on both endpoints (record the rejection status) (3.3)
    - Invalid subject/body/recipient-scope JSON payloads return the first constraint-violation message (record the exact messages) (3.5)
  - Write property-based tests capturing the observed behavior across the input domain:
    - Generate random valid `SendOwnerEmailRequest` payloads sent via the JSON body and assert deserialisation, validation, and dispatch are unchanged (3.1)
    - Generate random valid payloads and assert the same first-violation messages surface for validation failures (3.5)
    - Assert the security guard rejects unauthorised callers regardless of payload (3.3)
  - **NOTE on the multipart no-attachment case (3.2, 3.4)**: on the UNFIXED code this input still satisfies the bug condition (typed `request` part) and will 500, so its correct post-fix behavior is covered by the fix-checking tests in task 3.2. Add a preservation assertion here for the JSON no-attachment path that it must equal the multipart no-attachment result after the fix
  - Run tests on UNFIXED code
  - **EXPECTED OUTCOME**: JSON-path, security-guard, and validation-messaging tests PASS on unfixed code (confirms baseline behavior to preserve)
  - Mark task complete when tests are written, run, and passing on unfixed code
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5_

- [x] 3. Fix for multipart `request` part binding in OwnerEmailController

  - [x] 3.1 Implement the fix
    - **File**: `backend/src/main/java/com/society/module/owner/controller/OwnerEmailController.java`, method `sendOwnerEmailWithAttachments`
    - Replace `@RequestParam("request") String requestJson` with `@RequestPart("request") MultipartFile requestPart` so Spring resolves the typed `Blob` part directly and attempts no `MultipartFile → String` conversion
    - Read the part content as UTF-8: `new String(requestPart.getBytes(), StandardCharsets.UTF_8)`, wrapping any `IOException` in `throw new BusinessException("Invalid email request payload: " + e.getMessage())` to surface a meaningful message
    - Pass the derived string into the existing `parseAndValidate(requestJson)` helper unchanged (deserialisation via `ObjectMapper`, bean-validation via `Validator`, first-violation messaging preserved)
    - Leave the send flow unchanged: continue calling `ownerEmailService.sendOwnerEmail(request, attachments)` and returning `SendReportDTO` inside `ApiResponse.success(...)`
    - Keep the `attachments` binding (`@RequestPart(value = "attachments", required = false) List<MultipartFile>`), the `@PreAuthorize` guards, and the JSON `sendOwnerEmail` method untouched
    - Add imports `java.io.IOException` and `java.nio.charset.StandardCharsets`; remove the now-unused `org.springframework.web.bind.annotation.RequestParam` import if no longer referenced
    - _Bug_Condition: isBugCondition(input) = input.contentType = MULTIPART_FORM_DATA AND input.requestPart.hasContentType = true (from design)_
    - _Expected_Behavior: read the request part regardless of Content-Type, deserialise into SendOwnerEmailRequest, validate, and dispatch without a type-conversion error (Property 1 from design)_
    - _Preservation: JSON endpoint, method-security guard, validation messaging, and no-attachment multipart path remain unchanged (Preservation Requirements from design)_
    - _Requirements: 2.1, 2.2, 2.3, 3.1, 3.2, 3.3, 3.4, 3.5_

  - [x] 3.2 Verify bug condition exploration test now passes (fix checking)
    - **Property 1: Expected Behavior** - Multipart JSON Part Accepted Regardless of Content-Type
    - **IMPORTANT**: Re-run the SAME test from task 1 - do NOT write a new test
    - The test from task 1 encodes the expected behavior; when it passes it confirms the fix
    - Run the bug condition exploration test from step 1 against the fixed code
    - Confirm the fix-checking cases from the design testing strategy hold: typed `request` part + attachments → `200` with `SendReportDTO` and `ownerEmailService.sendOwnerEmail(request, attachments)` invoked; typed part with no attachments → `200` (empty/null attachment list); malformed JSON → `BusinessException("Invalid email request payload: ...")` not a conversion error; bean-validation failure → same first-violation message as the JSON endpoint
    - **EXPECTED OUTCOME**: Test PASSES (confirms the bug is fixed and no type-conversion error occurs)
    - _Requirements: 2.1, 2.2, 2.3_

  - [x] 3.3 Verify preservation tests still pass (preservation checking)
    - **Property 2: Preservation** - Non-Buggy Inputs Behave Identically
    - **IMPORTANT**: Re-run the SAME tests from task 2 - do NOT write new tests
    - Run the preservation property tests from step 2 against the fixed code
    - Confirm the JSON-only path, security guard, validation messaging, and no-attachment behavior are identical to the observed baseline, and that the multipart no-attachment path now matches the JSON path
    - **EXPECTED OUTCOME**: Tests PASS (confirms no regressions across non-buggy inputs)
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5_

- [x] 4. Add supporting unit, property-based, and integration tests
  - Implement the remaining test coverage from the design testing strategy (in addition to the exploration/preservation tests above)
  - **Unit tests**: multipart binding accepts a `request` part with `Content-Type: application/json` and reaches `parseAndValidate`; multipart binding accepts a `request` part without a Content-Type header (form-field style) — ensures the fix does not regress the untyped case; malformed JSON yields `BusinessException("Invalid email request payload: ...")`; bean-validation failure yields the first constraint-violation message; JSON-only endpoint continues to work with `@Valid @RequestBody`
  - **Property-based tests**: generate random valid `SendOwnerEmailRequest` payloads sent as a typed multipart `request` part and assert they are deserialised, validated, and dispatched (Fix Checking / Property 1); generate random valid payloads across both representations (JSON body vs multipart typed part) with and without attachments and assert the resulting `SendOwnerEmailRequest` passed to the service is identical (Preservation / Property 2); generate random attachment lists (including empty) and assert the service receives the same list the handler bound
  - **Integration tests**: full multipart send with attachments end-to-end via `MockMvc` returns `200` with a `SendReportDTO` and delegates to the service with the attachments (2.2); full multipart send with no attachments behaves identically to the JSON endpoint (3.2, 3.4); unauthorised caller is rejected across both content types (3.3)
  - _Requirements: 2.1, 2.2, 2.3, 3.1, 3.2, 3.3, 3.4, 3.5_

- [-] 5. Checkpoint - Ensure all tests pass
  - Run the full backend test suite (`mvn test` in `backend`) and confirm the exploration test now passes, the preservation tests still pass, and the supporting unit/property/integration tests pass with no regressions
  - Ensure all tests pass; ask the user if questions arise
