# Implementation Plan: Owner Email Rich Text

## Overview

This plan modifies the already-shipped Owner Email feature to compose the message body with a rich-text (WYSIWYG) editor, sanitize the produced HTML authoritatively on the server, embed only the sanitized body into the three-part `Email_Template`, and send a `multipart/alternative` message (HTML + derived plain text). It is backend-first. It starts with the coarse DTO guard and the two new backend dependencies (OWASP Java HTML Sanitizer + Jsoup), then adds the new `OwnerEmailSanitizer` and `OwnerEmailPlainTextRenderer`, then changes `OwnerEmailTemplateBuilder` from plain-text to HTML assembly, then wires the sanitize-once-per-request step and `multipart/alternative` send into `OwnerEmailServiceImpl`, with a backend checkpoint. It then moves to the frontend: add `ngx-quill` + `quill`, replace the body `<textarea>` in `OwnerEmailComponent` with a Quill editor, switch body validation from raw-length to visible-text length, and harden accessibility, followed by the frontend and integration/wiring tests and a final checkpoint. Every step builds on the previous ones; nothing is created from scratch where the design says to modify existing classes.

Backend: Spring Boot 3.2.5 (`com.society`), Java 17, Maven (`backend/pom.xml`), jqwik `1.8.5` for property-based tests (already present). Frontend: Angular 17.3 standalone + Angular Material.

## Tasks

- [x] 1. Add backend dependencies and widen the body guard
  - [x] 1.1 Add sanitization dependencies to `backend/pom.xml`
    - Add OWASP Java HTML Sanitizer `com.googlecode.owasp-java-html-sanitizer:owasp-java-html-sanitizer:20240325.1`
    - Add `org.jsoup:jsoup` explicitly if it is not already available transitively (used for visible-text extraction and plain-text rendering)
    - _Requirements: 3.2, 3.3, 3.4, 3.5, 7.2_

  - [x] 1.2 Widen `SendOwnerEmailRequest.body` bean-validation guard
    - In `com.society.module.owner.dto.SendOwnerEmailRequest`, change `body` from `@Size(max=10000)` to `@Size(max=50000)` on the raw HTML; keep `@NotBlank`
    - Leave `recipientScope`, `ownerIds`, and `subject` (`@NotBlank @Size(max=200)`) unchanged
    - _Requirements: 4.6_

- [x] 2. Implement server-side sanitization and plain-text derivation
  - [x] 2.1 Create `OwnerEmailSanitizer`
    - Create `OwnerEmailSanitizer` (`@Component`) in `com.society.module.owner.service`
    - Build an allow-list `PolicyFactory` via `HtmlPolicyBuilder`: `b`/`strong`, `i`/`em`, `u`, `s`/`strike`; `h1`-`h6`, `p`, `br`, `ul`, `ol`, `li`; `a` with `href` restricted via `allowUrlProtocols("http","https","mailto")` and `requireRelNofollowOnLinks()`
    - Implement `sanitize(String)` returning `""` for null/blank input and never throwing on malformed markup
    - Implement `visibleText(String)` using Jsoup to strip tags, decode entities, and trim (used for the 1..10000 visible-text bound)
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5_

  - [x] 2.2 Write property test for sanitization allow-list closure
    - **Feature: owner-email-rich-text, Property 1: Sanitization allow-list closure**
    - jqwik, min 100 tries; generators that always inject `script`/`iframe`/`object`/`embed`/`style`/`link` elements and `on*` attributes plus random allowed/disallowed markup; parse output with Jsoup and assert every surviving element/attribute is in the allow-list and none of the injected dangerous elements/attributes or inline script survive
    - **Validates: Requirements 3.2, 3.3, 3.4, 3.6**

  - [x] 2.3 Write property test for hyperlink scheme allow-list
    - **Feature: owner-email-rich-text, Property 2: Hyperlink scheme allow-list**
    - jqwik, min 100 tries; generate anchors with random schemes (`http`/`https`/`mailto` and disallowed such as `javascript`/`data`) and visible text; assert allowed schemes keep `href` and disallowed schemes drop `href` while the anchor's visible text remains
    - **Validates: Requirements 3.5**

  - [x] 2.4 Write property test for allowed-formatting preservation
    - **Feature: owner-email-rich-text, Property 3: Allowed formatting is preserved**
    - jqwik, min 100 tries; generate documents composed only of allowed-formatting elements (bold/italic/underline/strike, headings, ordered/unordered lists, paragraphs, line breaks, allowed-scheme links); assert each element is still present in the `Sanitized_Body`
    - **Validates: Requirements 2.3**

  - [x] 2.5 Create `OwnerEmailPlainTextRenderer`
    - Create `OwnerEmailPlainTextRenderer` (`@Component`) in `com.society.module.owner.service`
    - Implement `render(sanitizedHtml, subject, settings)` walking the sanitized DOM (Jsoup): `<p>`/`<br>` become newlines, `<li>` in `<ol>` becomes `"1. "` (numbered), `<li>` in `<ul>` becomes `"- "`; render header/footer as plain lines mirroring the HTML template order
    - Operate only on the sanitized content; never touch the raw `Rich_Text_Body`
    - _Requirements: 7.2, 7.3_

  - [x] 2.6 Write property test for plain-text derivation
    - **Feature: owner-email-rich-text, Property 6: Plain-text alternative derives from visible text**
    - jqwik, min 100 tries; generate sanitized bodies; assert the rendered plain text contains no HTML tag markup and includes the visible textual content of the sanitized body
    - **Validates: Requirements 7.2**

- [x] 3. Change the template builder to emit HTML
  - [x] 3.1 Modify `OwnerEmailTemplateBuilder` to build an HTML document
    - In `com.society.module.owner.service.OwnerEmailTemplateBuilder`, add `buildHtmlBody(SocietySettings, subject, sanitizedBody)` assembling header → body → footer in that fixed order
    - Re-express `buildHeader`/`buildFooter`/`buildBodySection` as HTML (`buildHtmlHeader`/`buildHtmlFooter`) that wrap every settings value in `<p>`/`<br/>` after `HtmlUtils.htmlEscape`; escape the subject; insert `sanitizedBody` verbatim (already sanitized, not re-escaped)
    - Retain `buildSubject(String)` and the existing `BusinessException` validation (`validateUserInput`, `validateHeaderSettings`, `validateFooterSettings`, `hasAnyAddressLine`) that names the offending field
    - _Requirements: 2.2, 2.3_

  - [x] 3.2 Write property test for template ordering
    - **Feature: owner-email-rich-text, Property 4: Template ordering is header-then-body-then-footer**
    - jqwik, min 100 tries; generate valid `SocietySettings`, non-empty subject, and a `Sanitized_Body`; assert `index(header) < index(body) < index(footer)` in the `buildHtmlBody` output
    - **Validates: Requirements 2.2**

- [x] 4. Wire sanitization and multipart send into the service
  - [x] 4.1 Modify `OwnerEmailServiceImpl` for sanitize-once and multipart/alternative
    - Inject `OwnerEmailSanitizer` and `OwnerEmailPlainTextRenderer` alongside the existing `mailSender`, `fromEmail`, `ownerRepository`, `societySettingsService`, `templateBuilder`
    - Before the mail-configured check and recipient loop, sanitize `request.getBody()` once; compute `visibleText`; throw `BusinessException` (no send) when visible text is empty (Req 4.4) or exceeds 10,000 (Req 4.5), and a defence-in-depth `BusinessException` when sanitized HTML exceeds 50,000 (Req 4.6)
    - Derive the plain-text alternative and assemble the HTML template from the sanitized body; switch the per-recipient send from `helper.setText(body, true)` to `helper.setText(plainText, assembledHtml)` (multipart/alternative)
    - Keep `resolveRecipients`, `normaliseAttachments`/`attachAll`, `toEntry`, `buildReport`, the `mailSender == null` branch, `MISSING_EMAIL`/`SEND_FAILURE`/`MAIL_NOT_CONFIGURED` handling, and the count invariant intact
    - _Requirements: 2.2, 2.3, 2.4, 3.1, 3.6, 3.7, 4.4, 4.5, 4.6, 5.2, 5.3, 5.4, 5.6, 7.1_

  - [x] 4.2 Write property test for the server visible-text bound
    - **Feature: owner-email-rich-text, Property 5: Server visible-text-length invariant**
    - jqwik, min 100 tries; mocked `JavaMailSender`; generators for bodies that sanitize to empty visible text, to `1..10000`, and to `>10000`; assert reject-with-no-send at `0` and `>10000`, accept in-range, and that the mail sender is never invoked on rejection (no partial record)
    - **Validates: Requirements 4.4, 4.5**

  - [x] 4.3 Write property test for recipient classification
    - **Feature: owner-email-rich-text, Property 8: Recipient classification is preserved**
    - jqwik, min 100 tries; mocked `JavaMailSender`, mixed valid/invalid-email owners; assert send invoked only for valid recipients, never for invalid, and each invalid recorded `MISSING_EMAIL`
    - **Validates: Requirements 5.2**

  - [x] 4.4 Write property test for mail-absent behavior
    - **Feature: owner-email-rich-text, Property 9: Mail-absent yields a graceful zero-sent report**
    - jqwik, min 100 tries; `mailSender = null`; assert `sentCount == 0`, `mailConfigured == false`, `notEmailedCount == totalAttempted`, no error propagated
    - **Validates: Requirements 5.3**

  - [x] 4.5 Write property test for per-recipient failure isolation
    - **Feature: owner-email-rich-text, Property 10: A per-recipient send failure does not halt the others**
    - jqwik, min 100 tries; mocked `JavaMailSender` with a generated subset of sends throwing; assert every remaining valid recipient is still attempted, failures recorded `SEND_FAILURE`, and the count invariant preserved
    - **Validates: Requirements 5.4**

  - [x] 4.6 Write property test for count reconciliation
    - **Feature: owner-email-rich-text, Property 11: Send report counts reconcile**
    - jqwik, min 100 tries; mocked `JavaMailSender`, generated recipient sets and mixed outcomes; assert `sentCount + notEmailedCount == totalAttempted` and `notEmailed.size() == notEmailedCount`
    - **Validates: Requirements 5.6**

- [x] 5. Checkpoint - Ensure all backend tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 6. Replace the body editor and validation on the frontend
  - [x] 6.1 Add the rich-text editor dependencies to `frontend/package.json`
    - Add `ngx-quill` and its peer `quill` (Quill 2.x)
    - _Requirements: 1.1, 1.8_

  - [x] 6.2 Replace the body `<textarea>` with a Quill editor in `OwnerEmailComponent`
    - In `frontend/src/app/modules/owner/owner-email/owner-email.component.ts` (+ template), replace the body `<textarea>` with `<quill-editor formControlName="body">`
    - Configure `quillModules.toolbar` to exactly the allow-list: bold/italic/underline/strike, header, ordered list, bullet list, link
    - Keep recipient selection, attachments, results panel, spinner, and 30s timeout logic unchanged; the request `body` now carries HTML
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8, 2.1_

  - [x] 6.3 Switch body validation to visible-text length
    - Add `visibleTextLength(html)` (strip tags via a detached element, trim) and a `bodyVisibleValidator` that returns `emptyVisible` at length 0 and `tooLongVisible` above 10,000, otherwise valid
    - On block, prevent submission and retain the composed content and recipient selection; show inline `mat-error` messages and the visible-length hint
    - _Requirements: 4.1, 4.2, 4.3_

  - [x] 6.4 Route preview rendering through `DomSanitizer`
    - Render any body preview via `DomSanitizer.sanitize(SecurityContext.HTML, ...)`; never use `bypassSecurityTrustHtml`
    - _Requirements: 3.8_

  - [x] 6.5 Harden editor accessibility
    - Ensure toolbar buttons are keyboard-reachable/operable and Quill's Ctrl/Cmd+B/I/U shortcuts are enabled (Req 6.1); add an `aria-label` to each toolbar button via a post-init DOM pass (Req 6.2); reflect Quill's format state as `aria-pressed` on toggle controls on selection change (Req 6.3); apply a `:focus-visible` outline to toolbar controls (Req 6.4)
    - _Requirements: 6.1, 6.2, 6.3, 6.4_

  - [x] 6.6 Write client-side visible-text validation property test
    - **Feature: owner-email-rich-text, Property 7: Client-side visible-text validation**
    - Angular test generating HTML whose visible text is empty, in-range, and over-length via `fast-check` if available, otherwise a parameterized table of boundary cases; assert submission blocked at `0` and `>10000`, accepted in-range, and content/selection retained on block
    - **Validates: Requirements 4.1, 4.2, 4.3**

  - [x] 6.7 Write component tests for the editor, request, and preview
    - Editor replaces the textarea (Req 1.1); toolbar exposes bold/italic/underline/strike, headings, ordered list, bullet list, link (Req 1.2–1.7); emitted value is HTML reflecting applied formatting (Req 1.8); the send request carries the editor HTML as `body` (Req 2.1); preview uses `DomSanitizer` without `bypassSecurityTrustHtml` (Req 3.8)
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8, 2.1, 3.8_

  - [x] 6.8 Write accessibility tests for the editor toolbar
    - Toolbar controls reachable/operable by keyboard (6.1); each control has an accessible name / `aria-label` (6.2); toggleable controls expose `aria-pressed` (6.3); focused controls show a visible focus indicator (6.4)
    - _Requirements: 6.1, 6.2, 6.3, 6.4_

- [x] 7. Integration and wiring tests
  - [x]* 7.1 Write multipart/alternative integration test
    - With a mocked/GreenMail sender, assert the sent `MimeMessage` is `multipart/alternative` carrying a `text/plain` part and a `text/html` part, and that attachments still nest correctly under `multipart/mixed`
    - _Requirements: 2.4, 7.1_

  - [x]* 7.2 Write script-payload-never-sent integration test
    - Submit a body containing `<script>` and `onerror` handlers; capture the sent `MimeMessage` and assert its HTML part contains none of them (authoritative sanitization removed them before send)
    - _Requirements: 3.1, 3.6_

  - [x]* 7.3 Write plain-text structure integration test
    - Assert ordered lists render as `1. `, unordered lists as `- `, and paragraph breaks as blank lines in the plain-text part
    - _Requirements: 7.3_

  - [x]* 7.4 Write authorization integration test
    - `@SpringBootTest` + `MockMvc`: anonymous → 401 (5.5); authenticated without `OWNER_EMAIL_SEND` → 403 (5.5); with the permission → processed
    - _Requirements: 5.5_

  - [x]* 7.5 Write sanitization-failure path integration test
    - Simulate a sanitization/derivation failure and assert a client-facing error is returned with no email send initiated
    - _Requirements: 3.7_

- [x] 8. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional test-writing sub-tasks and can be skipped for a faster MVP.
- Each task references specific requirement clauses for traceability.
- Checkpoints ensure incremental validation between the backend and frontend phases.
- Property tests (jqwik backend, min 100 tries each) validate the 11 universal correctness properties from the design (Property 7 is validated on the frontend); component, accessibility, and integration tests cover the editor UI, the multipart structure, authorization, and the sanitization-failure path.
- This is a modification of already-shipped code: `SendOwnerEmailRequest`, `OwnerEmailTemplateBuilder`, `OwnerEmailServiceImpl`, and `OwnerEmailComponent` are extended, not created; `OwnerEmailController` shape is unchanged; `OwnerEmailSanitizer` and `OwnerEmailPlainTextRenderer` are new.
- The design fixed the implementation-language stack (Java backend, TypeScript/Angular frontend), so no implementation-language selection was required.

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2", "6.1"] },
    { "id": 1, "tasks": ["2.1", "2.5"] },
    { "id": 2, "tasks": ["2.2", "2.3", "2.4", "2.6", "3.1"] },
    { "id": 3, "tasks": ["3.2", "4.1", "6.2"] },
    { "id": 4, "tasks": ["4.2", "4.3", "4.4", "4.5", "4.6", "6.3", "6.4", "6.5"] },
    { "id": 5, "tasks": ["6.6", "6.7", "6.8", "7.1", "7.2", "7.3", "7.4", "7.5"] }
  ]
}
```
