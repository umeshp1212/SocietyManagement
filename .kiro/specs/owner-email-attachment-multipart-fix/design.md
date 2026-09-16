# Owner Email Attachment Multipart Fix Bugfix Design

## Overview

The owner email endpoint `POST /api/owners/email/send` supports two representations:
a plain `application/json` body (no attachments) and a `multipart/form-data` body
carrying a `request` JSON part plus optional `attachments` file parts. The multipart
handler, `OwnerEmailController.sendOwnerEmailWithAttachments`, binds the JSON payload
as `@RequestParam("request") String requestJson`. The frontend appends the `request`
part as a `Blob` typed `application/json`
(`formData.append('request', new Blob([JSON.stringify(request)], { type: 'application/json' }))`).
Because that part carries a `Content-Type` header, Spring's multipart resolver treats it
as a file part (`MultipartFile`) rather than a simple form field, so it cannot convert it
to `String`. The result is a `500 Internal Server Error`:

> Failed to convert value of type `...StandardMultipartFile` to required type
> `java.lang.String`; ... no matching editors or conversion strategy found.

The fix is minimal and targeted: change the `request` parameter binding so Spring accepts
the typed part, read its content into a string, and feed it into the controller's existing
`parseAndValidate(String)` helper. This keeps deserialisation, bean-validation, the
security guard, the JSON-only path, and the no-attachment multipart path completely
unchanged. The failure occurs during argument binding, before any controller logic runs;
fixing the binding is the only change required.

## Glossary

- **Bug_Condition (C)**: The condition that triggers the bug — a `multipart/form-data`
  request whose `request` part carries a `Content-Type` header (delivered as a typed
  `Blob`), causing Spring to resolve it as a `MultipartFile` and fail to bind it to a
  `String @RequestParam`.
- **Property (P)**: The desired behavior — the endpoint reads the `request` part
  regardless of its `Content-Type`, deserialises it into `SendOwnerEmailRequest`,
  validates it, and processes the send without a type-conversion error.
- **Preservation**: The JSON-only endpoint, method-security guard, validation messaging,
  and the no-attachment multipart path must remain exactly as they behave today.
- **sendOwnerEmailWithAttachments**: The multipart handler in
  `backend/src/main/java/com/society/module/owner/controller/OwnerEmailController.java`
  that currently binds `@RequestParam("request") String requestJson`.
- **parseAndValidate**: The existing private helper in `OwnerEmailController` that
  deserialises the JSON string via `ObjectMapper` and applies bean-validation via the
  injected `Validator`, mirroring the `@Valid @RequestBody` guarantees of the JSON endpoint.
- **requestPart.hasContentType**: The property of the incoming `request` part that
  determines whether Spring resolves it as a `MultipartFile` (has Content-Type) or a
  plain form field (no Content-Type).

## Bug Details

### Bug Condition

The bug manifests when a `POST /api/owners/email/send` request arrives as
`multipart/form-data` and the `request` part carries a `Content-Type` header (as the
frontend `Blob` does). Spring's multipart resolver classifies any part with a
`Content-Type` header as a file part and exposes it as a `MultipartFile`. The handler
declares that part as `@RequestParam("request") String`, so the framework attempts to
convert a `StandardMultipartFile` to `String`, finds no converter, and throws before the
controller body executes.

**Formal Specification:**
```
FUNCTION isBugCondition(input)
  INPUT: input of type EmailSendRequest
  OUTPUT: boolean

  RETURN input.contentType = MULTIPART_FORM_DATA
         AND input.requestPart.hasContentType = true
END FUNCTION
```

### Examples

- Frontend send with one attachment: `request` appended as a `Blob` typed
  `application/json`, `attachments` contains `document.pdf`. Expected: email dispatched
  with attachment. Actual: `500` type-conversion error, no email sent.
- Frontend send with multiple attachments: same `Blob`-typed `request` part. Expected:
  email dispatched with all attachments. Actual: `500` type-conversion error.
- curl reproduction `-F "request=@payload.json;type=application/json" -F "attachments=@document.pdf"`:
  the `;type=application/json` gives the part a Content-Type. Expected: processed. Actual:
  `500` type-conversion error.
- Edge case — multipart request where the `request` part carries a Content-Type but no
  `attachments` part: Expected: email sent with no attachments (identical to JSON path).
  Actual: `500` type-conversion error (binding fails before attachment handling).

## Expected Behavior

### Preservation Requirements

**Unchanged Behaviors:**
- The JSON-only endpoint `sendOwnerEmail` (`consumes = application/json`) SHALL continue
  to deserialise, `@Valid`-validate, and process requests exactly as before.
- The method-security guard `@PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('OWNER_EMAIL_SEND')")`
  SHALL continue to reject unauthorised callers on both endpoints.
- Validation messaging produced by `parseAndValidate` (subject/body/recipient-scope rules)
  SHALL remain identical, still surfacing the first constraint-violation message.
- The no-attachment multipart path SHALL continue to behave identically to the JSON path.
- Malformed JSON SHALL continue to produce the same `BusinessException("Invalid email
  request payload: ...")` message.

**Scope:**
All inputs that do NOT satisfy the bug condition should be completely unaffected by this
fix. This includes:
- `application/json` requests with no attachments (routed to `sendOwnerEmail`).
- Requests from unauthorised callers (rejected by the security guard before binding).
- Multipart requests whose payloads fail deserialisation or bean-validation (still handled
  by `parseAndValidate`).

**Note:** The expected correct behavior for buggy inputs is defined in the Correctness
Properties section (Property 1). This section focuses on what must NOT change.

## Hypothesized Root Cause

Confirmed root cause (validated against the source in
`OwnerEmailController.sendOwnerEmailWithAttachments`):

1. **Incorrect part binding type**: The `request` part is declared as
   `@RequestParam("request") String requestJson`. Spring resolves a multipart part that
   carries a `Content-Type` header as a `MultipartFile`, not a form field, so it cannot
   bind it to a `String` and no converter exists for `MultipartFile → String`.
   - The frontend sends the part as `new Blob([...], { type: 'application/json' })`, which
     sets the part's Content-Type header.
   - The curl equivalent `;type=application/json` reproduces the same condition.

2. **Binding happens before controller logic**: Because argument resolution fails, the
   existing `parseAndValidate` helper — which is already correct — never runs. The fix is
   purely at the binding boundary.

3. **Not a deserialisation or validation defect**: `ObjectMapper` and `Validator` usage in
   `parseAndValidate` are correct; they simply need a `String` derived from the part's
   content rather than a `String`-bound parameter.

## Correctness Properties

Property 1: Bug Condition - Multipart JSON Part Accepted Regardless of Content-Type

_For any_ input where the bug condition holds (isBugCondition returns true — a
`multipart/form-data` request whose `request` part carries a `Content-Type` header), the
fixed `sendOwnerEmailWithAttachments` SHALL read the JSON payload, deserialise it into
`SendOwnerEmailRequest`, apply bean-validation, and process the send without a
type-conversion error; when the payload is valid the email SHALL be dispatched with any
supplied attachments and the `SendReportDTO` SHALL be returned in the standard
`ApiResponse` envelope.

**Validates: Requirements 2.1, 2.2, 2.3**

Property 2: Preservation - Non-Buggy Inputs Behave Identically

_For any_ input where the bug condition does NOT hold (isBugCondition returns false — the
JSON-only path, unauthorised callers, no-attachment multipart requests, and payloads that
fail deserialisation or validation), the fixed code SHALL produce the same result as the
original function, preserving the JSON endpoint, the method-security guard, the validation
messaging, and the no-attachment multipart behaviour.

**Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5**

## Fix Implementation

### Changes Required

Assuming our root cause analysis is correct (and it has been confirmed against the source):

**File**: `backend/src/main/java/com/society/module/owner/controller/OwnerEmailController.java`

**Function**: `sendOwnerEmailWithAttachments`

**Specific Changes**:

1. **Rebind the `request` part as a part, not a form field**: Replace
   `@RequestParam("request") String requestJson` with a binding that accepts the typed
   part. Preferred approach: `@RequestPart("request") MultipartFile requestPart`. Spring
   resolves the typed `Blob` part directly to a `MultipartFile`, so no conversion is
   attempted and the `500` is eliminated.
   - Alternative considered: `@RequestPart("request") byte[] requestBytes`. Also works and
     avoids the `MultipartFile` abstraction, but `MultipartFile` is already imported and
     used for `attachments`, keeping the change consistent.
   - Alternative rejected: keeping `String` and adding a custom `HttpMessageConverter` or
     `Content-Type`-stripping filter — larger blast radius and touches shared infrastructure,
     violating the minimal-fix goal.

2. **Convert the part content to a JSON string**: Read the part's bytes as UTF-8, e.g.
   `new String(requestPart.getBytes(), StandardCharsets.UTF_8)`, wrapping any `IOException`
   into the same `BusinessException("Invalid email request payload: ...")` used by
   `parseAndValidate` so malformed/unreadable input surfaces a meaningful message (2.3, 3.5).

3. **Reuse the existing `parseAndValidate` helper unchanged**: Pass the derived string into
   `parseAndValidate(requestJson)`. Deserialisation via `ObjectMapper`, bean-validation via
   `Validator`, and the first-violation messaging are preserved exactly (3.5).

4. **Leave the send flow unchanged**: Continue calling
   `ownerEmailService.sendOwnerEmail(request, attachments)` and returning the
   `SendReportDTO` inside `ApiResponse.success(...)` (2.2).

5. **Leave all preservation surfaces untouched**: The JSON `sendOwnerEmail` method, the
   `@PreAuthorize` guards on both methods, the `attachments` binding
   (`@RequestPart(required = false) List<MultipartFile>`), and the injected
   `ObjectMapper`/`Validator` beans remain as-is (3.1, 3.2, 3.3, 3.4).

### Illustrative Diff (target shape)

```java
public ResponseEntity<ApiResponse<SendReportDTO>> sendOwnerEmailWithAttachments(
        @RequestPart("request") MultipartFile requestPart,
        @RequestPart(value = "attachments", required = false) List<MultipartFile> attachments) {
    String requestJson;
    try {
        requestJson = new String(requestPart.getBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
        throw new BusinessException("Invalid email request payload: " + e.getMessage());
    }
    SendOwnerEmailRequest request = parseAndValidate(requestJson);
    SendReportDTO report = ownerEmailService.sendOwnerEmail(request, attachments);
    return ResponseEntity.ok(ApiResponse.success("Email send processed", report));
}
```

(Add imports `java.io.IOException` and `java.nio.charset.StandardCharsets`; the now-unused
`org.springframework.web.bind.annotation.RequestParam` import may be removed.)

## Testing Strategy

### Validation Approach

The testing strategy follows a two-phase approach: first, surface counterexamples that
demonstrate the bug on the unfixed code, then verify the fix works correctly and preserves
existing behavior. Tests use `MockMvc` with `multipart(...)` requests and `MockMultipartFile`
parts, allowing the `request` part's Content-Type to be set (reproducing the `Blob`
condition) or omitted. The `OwnerEmailService` is mocked so tests assert on binding,
deserialisation, validation, and dispatch delegation rather than on real email sending.

### Exploratory Bug Condition Checking

**Goal**: Surface counterexamples that demonstrate the bug BEFORE implementing the fix.
Confirm the root cause (typed `request` part cannot bind to `String`). If refuted, we
re-hypothesize.

**Test Plan**: Issue `multipart/form-data` requests to `/api/owners/email/send` where the
`request` part is a `MockMultipartFile` with content type `application/json`. Run against
the UNFIXED controller and observe the `500` type-conversion failure.

**Test Cases**:
1. **Typed request part with one attachment**: `request` part typed `application/json`,
   one `attachments` file (will fail on unfixed code with the conversion error).
2. **Typed request part with multiple attachments**: same, multiple files (will fail on
   unfixed code).
3. **Typed request part, no attachments**: `request` part typed `application/json`, no
   `attachments` (will fail on unfixed code — binding fails before attachment handling).
4. **curl-style typed part (edge)**: part Content-Type set as `application/json` mirroring
   `;type=application/json` (may fail on unfixed code).

**Expected Counterexamples**:
- Response is `500` with `Failed to convert value of type '...StandardMultipartFile' to
  required type 'java.lang.String'`.
- Confirms cause: the `request` part is resolved as `MultipartFile`, not bound as `String`.

### Fix Checking

**Goal**: Verify that for all inputs where the bug condition holds, the fixed handler
produces the expected behavior (maps to Property 1 / Requirements 2.1, 2.2, 2.3).

**Pseudocode:**
```
FOR ALL input WHERE isBugCondition(input) DO
  result := sendOwnerEmailWithAttachments'(input)
  ASSERT no_type_conversion_error(result)
         AND payload_deserialised_and_validated(result)
         AND (valid(input) IMPLIES email_dispatched(result))
END FOR
```

**Test Cases (post-fix)**:
1. **Typed request part + attachments → 200**: assert response is the `ApiResponse`
   envelope with `SendReportDTO`, and that `ownerEmailService.sendOwnerEmail(request,
   attachments)` was invoked with the deserialised request and the attachment list.
2. **Typed request part, no attachments → 200**: assert dispatch occurs with an empty/null
   attachment list, behaving like the JSON path.
3. **Typed request part with malformed JSON → business/validation error**: assert a
   meaningful `BusinessException` message ("Invalid email request payload: ...") rather
   than a multipart conversion error (2.3).
4. **Typed request part failing bean-validation → validation message**: assert the same
   first-violation message the JSON endpoint returns (2.3).

### Preservation Checking

**Goal**: Verify that for all inputs where the bug condition does NOT hold, the fixed
handler produces the same result as the original (maps to Property 2 / Requirements
3.1–3.5).

**Pseudocode:**
```
FOR ALL input WHERE NOT isBugCondition(input) DO
  ASSERT sendOwnerEmail_original(input) = sendOwnerEmail_fixed(input)
END FOR
```

**Testing Approach**: Property-based testing is recommended for preservation checking because:
- It generates many test cases automatically across the input domain (valid payloads,
  varying recipient scopes, presence/absence of attachments).
- It catches edge cases manual unit tests might miss.
- It provides strong guarantees that behavior is unchanged for all non-buggy inputs.

**Test Plan**: Observe behavior on UNFIXED code first for the JSON path, the security guard,
validation messaging, and the no-attachment multipart path, then write tests capturing that
behavior and confirm it is identical after the fix.

**Test Cases**:
1. **JSON-only path preservation**: `application/json` request with a valid payload still
   deserialises, validates, and dispatches identically (3.1). Verified unchanged before and
   after the fix (the JSON method is not modified).
2. **Security guard preservation**: a caller lacking `SUPER_ADMIN`/`OWNER_EMAIL_SEND` is
   rejected on both endpoints (3.3).
3. **Validation messaging preservation**: invalid subject/body/recipient-scope payloads
   return the same messages via both JSON and multipart paths (3.5).
4. **No-attachment multipart preservation**: multipart request omitting `attachments`
   sends the email with no attachments, matching the JSON path (3.4, 3.2).

### Unit Tests

- Multipart binding accepts a `request` part with `Content-Type: application/json` and
  reaches `parseAndValidate` (fix core).
- Multipart binding accepts a `request` part without a Content-Type header (form-field
  style) — ensures the fix does not regress the untyped case.
- Malformed JSON in the `request` part yields `BusinessException("Invalid email request
  payload: ...")`.
- Bean-validation failure yields the first constraint-violation message.
- JSON-only endpoint continues to work with `@Valid @RequestBody`.

### Property-Based Tests

- Generate random valid `SendOwnerEmailRequest` payloads and assert that, when sent as a
  typed multipart `request` part, they are deserialised, validated, and dispatched (Fix
  Checking / Property 1).
- Generate random valid payloads across both representations (JSON body vs multipart typed
  part) with and without attachments and assert the resulting `SendOwnerEmailRequest`
  passed to the service is identical, demonstrating preservation (Property 2).
- Generate random attachment lists (including empty) and assert the service receives the
  same list the handler bound.

### Integration Tests

- Full multipart send with attachments end-to-end (through `MockMvc`) returns `200` with a
  `SendReportDTO` and delegates to the service with the attachments (2.2).
- Full multipart send with no attachments behaves identically to the JSON endpoint (3.2, 3.4).
- Unauthorised caller is rejected across both content types (3.3).
