# Bugfix Requirements Document

## Introduction

The owner email module exposes `POST /api/owners/email/send`, which supports two
representations: a plain `application/json` body (no attachments) and a
`multipart/form-data` body carrying a `request` JSON part plus optional
`attachments` file parts. In production, sending an email with attachments through
the multipart path returns `500 Internal Server Error` with the message:

> An unexpected error occurred: Failed to convert value of type
> `...StandardMultipartFile` to required type `java.lang.String`; cannot convert
> value... no matching editors or conversion strategy found.

Root cause: the multipart handler declares the JSON payload as
`@RequestParam("request") String requestJson`. The frontend appends the `request`
part as a `Blob` with `type: 'application/json'`
(`formData.append('request', new Blob([JSON.stringify(request)], { type: 'application/json' }))`).
Because that part carries a `Content-Type` header, Spring's multipart resolver
treats it as a file part (`MultipartFile`) rather than a simple form field, so it
cannot bind it to a `String` parameter and the conversion fails before any
controller logic runs. As a result, owners cannot receive emails whenever the
request is submitted as `multipart/form-data`, regardless of whether attachments
are actually present.

**Impact:** The primary attachment-sending flow of the owner email feature is
fully broken in production. Every multipart send fails with a 500 error and no
email is dispatched. The JSON-only path (no attachments) is unaffected.

## Bug Analysis

### Current Behavior (Defect)

When the email send request arrives as `multipart/form-data` with the `request`
part typed as `application/json`, Spring tries to bind that file part to the
`String requestJson` parameter and fails.

1.1 WHEN a `POST /api/owners/email/send` request is submitted as `multipart/form-data` with the `request` part carrying a `Content-Type` of `application/json` THEN the system responds with `500 Internal Server Error` and a type-conversion message (`Failed to convert value of type '...StandardMultipartFile' to required type 'java.lang.String'`)
1.2 WHEN a valid owner email is sent with one or more attachments (which forces the multipart representation) THEN the system fails to process the request and no email is dispatched to any recipient
1.3 WHEN the multipart binding fails THEN the failure occurs before request deserialisation and bean-validation run, so callers receive an opaque conversion error rather than a meaningful validation or business message

### Expected Behavior (Correct)

The endpoint SHALL accept the JSON payload from a `multipart/form-data` request
regardless of whether the `request` part carries a `Content-Type` header, then
proceed with the existing deserialisation, validation, and send flow.

2.1 WHEN a `POST /api/owners/email/send` request is submitted as `multipart/form-data` with the `request` part carrying a `Content-Type` of `application/json` THEN the system SHALL read the JSON payload, deserialise it into `SendOwnerEmailRequest`, and process the send without a type-conversion error
2.2 WHEN a valid owner email is sent with one or more attachments THEN the system SHALL dispatch the email with the attachments and return the `SendReportDTO` inside the standard `ApiResponse` envelope
2.3 WHEN the multipart `request` payload is malformed or fails bean-validation THEN the system SHALL return a meaningful business/validation error (as the JSON endpoint does today), not an opaque multipart type-conversion error

### Unchanged Behavior (Regression Prevention)

3.1 WHEN a `POST /api/owners/email/send` request is submitted as `application/json` (no attachments) THEN the system SHALL CONTINUE TO deserialise, `@Valid`-validate, and process the request exactly as before
3.2 WHEN a valid owner email is sent with no attachments via the multipart path THEN the system SHALL CONTINUE TO behave identically to the JSON endpoint
3.3 WHEN the caller lacks `SUPER_ADMIN` role or `OWNER_EMAIL_SEND` authority THEN the system SHALL CONTINUE TO reject the request via the existing method-security guard
3.4 WHEN a multipart request omits the optional `attachments` part THEN the system SHALL CONTINUE TO send the email with no attachments
3.5 WHEN the JSON payload violates subject/body/recipient-scope validation rules THEN the system SHALL CONTINUE TO return the same validation messages produced today

## Reproduction Steps

1. Authenticate as a user with `SUPER_ADMIN` role or `OWNER_EMAIL_SEND` authority.
2. From the frontend owner email screen, compose an email and attach at least one file (this forces the request to be sent as `multipart/form-data`).
3. Submit the email. The frontend appends the JSON payload as a `Blob` typed `application/json` under the `request` part.
4. Observe a `500 Internal Server Error` with the message `Failed to convert value of type '...StandardMultipartFile' to required type 'java.lang.String'`.
5. No email is dispatched.

Equivalent raw reproduction with curl (a typed `request` part triggers the bug):

```
curl -X POST "https://<host>/api/owners/email/send" \
  -H "Authorization: Bearer <token>" \
  -F "request=@payload.json;type=application/json" \
  -F "attachments=@document.pdf"
```

## Bug Condition and Properties

**Key Definitions:**
- **F**: The original (unfixed) multipart send handler binding `@RequestParam("request") String`.
- **F'**: The fixed handler that reads the `request` part regardless of its `Content-Type`.

**Bug Condition Function** — identifies inputs that trigger the bug:

```pascal
FUNCTION isBugCondition(X)
  INPUT: X of type EmailSendRequest
  OUTPUT: boolean

  // The request reaches the multipart endpoint AND the "request" part is
  // delivered as a typed part (carries a Content-Type header, e.g. a Blob),
  // so Spring resolves it as a MultipartFile instead of a String form field.
  RETURN X.contentType = MULTIPART_FORM_DATA
         AND X.requestPart.hasContentType = true
END FUNCTION
```

**Property: Fix Checking** — desired behavior for buggy inputs:

```pascal
FOR ALL X WHERE isBugCondition(X) DO
  result ← sendOwnerEmailWithAttachments'(X)
  ASSERT no_type_conversion_error(result)
         AND payload_deserialised_and_validated(result)
         AND (valid(X) IMPLIES email_dispatched(result))
END FOR
```

**Property: Preservation Checking** — non-buggy inputs must be unchanged:

```pascal
FOR ALL X WHERE NOT isBugCondition(X) DO
  ASSERT F(X) = F'(X)
END FOR
```

This preserves the JSON endpoint, security guarding, validation messaging, and the
no-attachment multipart path exactly as they behave today.
