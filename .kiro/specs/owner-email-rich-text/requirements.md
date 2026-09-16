# Requirements Document

## Introduction

The Owner Email Rich Text feature enhances the existing Owner Email feature so that a user composing an owner email can format the message body using a rich text (WYSIWYG) editor instead of the current plain textarea. The rich text editor allows word-level styling (bold, italic, underline, strikethrough), heading and font styles, ordered (numbered) lists, unordered (bulleted) lists, and basic structural formatting (paragraphs and line breaks), and optionally hyperlinks.

The formatted body is produced as HTML, sanitized against a safe allow-list on the server, and embedded into the existing three-part email template (Society_Header, then Body, then Footer, in that order) that the Owner Email feature already uses. All existing Owner Email behaviors are preserved: recipient scope selection (all owners or selected owners), exclusion and reporting of owners without an email address, graceful behavior when the mail transport is not configured, partial-send reporting, and authorization. The rich text change replaces only how the message content of the Body is composed, validated, and rendered.

Because HTML markup inflates the raw character count relative to the visible text, this feature redefines the body length boundary in terms of visible (plain) text and a bounded stored-HTML size, and preserves the existing rejection of an empty or whitespace-only body (a body with no visible content). Sanitization is the most critical concern: the authoritative sanitization happens on the server so that scripts, event handlers, iframes, and other unsafe markup can never reach a sent email; client-side sanitization is a secondary defense only.

### Open Decisions (to be confirmed with the user)

- **D1 — Hyperlinks and images:** This document assumes hyperlinks are IN scope for the message body, restricted to a safe allow-list of URL schemes (`http`, `https`, `mailto`), and that images are OUT of scope for the MVP. Confirm before proceeding.
- **D2 — Maximum body size definition and value:** This document assumes the body limit is defined as a maximum of 10,000 visible (plain-text) characters AND a maximum stored sanitized-HTML size of 50,000 characters, whichever is reached first. Confirm both the metric and the numeric values.
- **D3 — Plain-text multipart alternative:** This document assumes a multipart email carrying both an HTML part and a plain-text alternative IS required for the MVP so that email clients that do not render HTML still receive readable content. Confirm whether this is required for the MVP.

## Glossary

- **Owner_Email_Service**: The backend service that assembles the templated email and sends it to owner recipients (existing).
- **Owner_Email_Controller**: The backend REST endpoint that receives email-send requests from the frontend and delegates to the Owner_Email_Service (existing).
- **Owner_Email_Component**: The frontend component that lets users select recipients, compose the email, and submit the send request (existing).
- **Email_Template**: The three-part structure applied to every email, composed of the Society_Header, the Body, and the Footer (existing).
- **Society_Header**: The top section of the email, populated from society settings (existing).
- **Body**: The middle section of the email, containing the subject and message content entered by the user (existing).
- **Footer**: The bottom section of the email, populated from society settings (existing).
- **Send_Report**: The response returned after a send attempt, listing recipient counts and per-recipient outcomes (existing).
- **Mail_Sender**: The email transport (Spring JavaMailSender). May be absent when mail is not configured (existing).
- **OWNER_EMAIL_SEND**: The permission that authorizes a user to send emails to owners (existing).
- **Rich_Text_Editor**: The frontend WYSIWYG editing control within the Owner_Email_Component that replaces the plain textarea for the message content of the Body and lets the user apply Allowed_Formatting.
- **Rich_Text_Body**: The formatted message content produced by the Rich_Text_Editor, expressed as HTML, before server-side sanitization.
- **Sanitized_Body**: The Rich_Text_Body after the Server_Sanitizer has removed or rejected all markup outside the Allowed_Formatting allow-list. This is the only form of the body permitted to be embedded into the Email_Template.
- **Allowed_Formatting**: The safe allow-list of formatting the user may apply and that survives sanitization: bold, italic, underline, strikethrough, heading and font styles, ordered (numbered) lists, unordered (bulleted) lists, paragraphs, line breaks, and (subject to D1) hyperlinks with an allowed URL scheme.
- **Server_Sanitizer**: The authoritative backend component that transforms a Rich_Text_Body into a Sanitized_Body by enforcing the Allowed_Formatting allow-list and stripping or rejecting disallowed markup.
- **Visible_Text**: The plain-text content of the Rich_Text_Body with all markup removed, used to measure whether the body has visible content and to enforce the visible-text length limit.
- **Plain_Text_Alternative**: The plain-text rendering of the Sanitized_Body carried as the alternative part of a multipart email for clients that do not render HTML (subject to D3).
- **Allowed_Url_Scheme**: A URL scheme permitted in hyperlink targets after sanitization, limited to `http`, `https`, and `mailto` (subject to D1).

## Requirements

### Requirement 1: Rich Text Composition of the Body

**User Story:** As a committee member, I want to format the email message with styling and lists as I type, so that owners receive a clearly structured and emphasized message.

#### Acceptance Criteria

1. WHEN the email composition view is displayed, THE Owner_Email_Component SHALL present the Rich_Text_Editor in place of a plain textarea for the message content of the Body.
2. THE Rich_Text_Editor SHALL provide controls that apply bold, italic, underline, and strikethrough styling to selected text.
3. THE Rich_Text_Editor SHALL provide controls that apply heading and font styles to selected text.
4. THE Rich_Text_Editor SHALL provide a control that formats selected content as an ordered (numbered) list.
5. THE Rich_Text_Editor SHALL provide a control that formats selected content as an unordered (bulleted) list.
6. THE Rich_Text_Editor SHALL preserve paragraph breaks and line breaks entered by the user in the Rich_Text_Body.
7. WHERE hyperlinks are in scope per decision D1, THE Rich_Text_Editor SHALL provide a control that applies a hyperlink with an Allowed_Url_Scheme to selected text.
8. WHEN the user applies any Allowed_Formatting, THE Rich_Text_Editor SHALL produce a Rich_Text_Body expressed as HTML that reflects the applied formatting.

### Requirement 2: End-to-End Preservation of Formatting

**User Story:** As a committee member, I want the formatting I apply in the composer to appear in the email owners receive, so that my intended structure and emphasis are not lost.

#### Acceptance Criteria

1. WHEN the Owner_Email_Component submits a send request, THE Owner_Email_Component SHALL include the Rich_Text_Body as the message content of the Body in the request.
2. WHEN the Owner_Email_Service assembles an email, THE Owner_Email_Service SHALL embed the Sanitized_Body as the Body between the Society_Header and the Footer, in the order Society_Header, then Body, then Footer.
3. WHEN the Owner_Email_Service embeds the Sanitized_Body, THE Owner_Email_Service SHALL retain every element of Allowed_Formatting present in the Sanitized_Body so that the received email renders the same bold, italic, underline, strikethrough, heading, font, ordered-list, unordered-list, paragraph, line-break, and hyperlink formatting the user applied.
4. WHERE decision D3 requires a Plain_Text_Alternative, THE Owner_Email_Service SHALL produce a Plain_Text_Alternative derived from the Sanitized_Body and SHALL send a multipart email containing both the HTML Email_Template and the Plain_Text_Alternative.

### Requirement 3: Server-Side Sanitization of the Formatted Body

**User Story:** As a society administrator, I want all formatted content sanitized on the server before it is sent, so that malicious or unsafe markup can never reach owners.

#### Acceptance Criteria

1. WHEN a send request containing a Rich_Text_Body is received, THE Server_Sanitizer SHALL transform the Rich_Text_Body into a Sanitized_Body before the Owner_Email_Service assembles or sends any email.
2. WHEN the Server_Sanitizer produces a Sanitized_Body, THE Server_Sanitizer SHALL retain only markup within the Allowed_Formatting allow-list and SHALL remove all other markup.
3. WHEN the Server_Sanitizer processes a Rich_Text_Body, THE Server_Sanitizer SHALL remove `script` elements, `iframe` elements, `object` elements, `embed` elements, `style` elements, and `link` elements.
4. WHEN the Server_Sanitizer processes a Rich_Text_Body, THE Server_Sanitizer SHALL remove event-handler attributes (attributes whose name begins with `on`) and inline script from all elements.
5. IF a hyperlink target in the Rich_Text_Body uses a scheme other than an Allowed_Url_Scheme, THEN THE Server_Sanitizer SHALL remove the hyperlink target while retaining the visible text of the hyperlink.
6. THE Owner_Email_Service SHALL embed only a Sanitized_Body into the Email_Template and SHALL NOT embed any unsanitized Rich_Text_Body.
7. IF sanitization of a Rich_Text_Body cannot be completed, THEN THE Owner_Email_Controller SHALL reject the send request with an error indicating the message content could not be processed and SHALL initiate no email send operation.
8. THE Owner_Email_Component SHALL sanitize or escape rendered content in the composition and preview views as a secondary defense, and THE Server_Sanitizer SHALL remain the authoritative sanitization stage.

### Requirement 4: Body Length and Size Validation

**User Story:** As a committee member, I want clear limits on how much content I can send, so that I know when my message is too long or empty before I submit.

#### Acceptance Criteria

1. WHEN the email composition view is displayed, THE Owner_Email_Component SHALL allow a Body whose Visible_Text is between 1 and 10,000 characters inclusive (subject to decision D2).
2. IF the user submits a send request whose Rich_Text_Body has a Visible_Text length of zero after removing markup and whitespace, THEN THE Owner_Email_Component SHALL block submission, retain the composed content and recipient selection, and display a message requesting message content.
3. IF the user submits a send request whose Rich_Text_Body Visible_Text exceeds 10,000 characters, THEN THE Owner_Email_Component SHALL block submission and display a message indicating the maximum allowed visible-text length (subject to decision D2).
4. IF a send request is received whose Sanitized_Body has a Visible_Text length of zero after removing markup and whitespace, THEN THE Owner_Email_Controller SHALL reject the request with a validation error indicating the message body is invalid, SHALL initiate no email send operation, and SHALL retain no partial record of the request.
5. IF a send request is received whose Sanitized_Body Visible_Text exceeds 10,000 characters, THEN THE Owner_Email_Controller SHALL reject the request with a validation error indicating the maximum allowed visible-text length, SHALL initiate no email send operation, and SHALL retain no partial record of the request (subject to decision D2).
6. IF a send request is received whose Sanitized_Body size exceeds 50,000 characters of stored HTML, THEN THE Owner_Email_Controller SHALL reject the request with a validation error indicating the maximum allowed body size, SHALL initiate no email send operation, and SHALL retain no partial record of the request (subject to decision D2).

### Requirement 5: Preservation of Existing Owner Email Behaviors

**User Story:** As a society administrator, I want all existing owner email behaviors to keep working after the rich text change, so that recipient handling, reporting, and authorization remain reliable.

#### Acceptance Criteria

1. WHEN a send request is submitted, THE Owner_Email_Component SHALL support the recipient scope choice between all owners and selected owners as it did before the rich text change.
2. WHEN the Owner_Email_Service processes recipients, THE Owner_Email_Service SHALL send the email only to recipients with a non-blank email address and SHALL exclude and report recipients without an email address in the Send_Report.
3. IF the Mail_Sender is absent when a send request is received, THEN THE Owner_Email_Service SHALL send zero emails and SHALL return a Send_Report indicating that mail is not configured, without raising an error to the caller.
4. IF sending to an individual recipient fails, THEN THE Owner_Email_Service SHALL record that recipient as not emailed in the Send_Report and SHALL continue processing the remaining recipients.
5. IF a send request is received from a user who is unauthenticated or who does not hold the OWNER_EMAIL_SEND permission, THEN THE Owner_Email_Controller SHALL reject the request with the corresponding authentication or authorization error and SHALL initiate no email send operation.
6. WHEN a send operation completes, THE Owner_Email_Service SHALL return a Send_Report in which the count of emails sent plus the count of recipients not emailed equals the total number of recipients attempted.

### Requirement 6: Accessibility of the Rich Text Editor

**User Story:** As a committee member who uses assistive technology, I want the formatting controls to be keyboard-operable and labeled, so that I can compose formatted emails without a mouse.

#### Acceptance Criteria

1. THE Rich_Text_Editor SHALL make every formatting control reachable and operable using the keyboard alone.
2. THE Rich_Text_Editor SHALL provide a programmatically associated accessible name for every formatting control so that a screen reader announces the purpose of the control.
3. WHEN a toggleable formatting control changes between active and inactive, THE Rich_Text_Editor SHALL expose the current active or inactive state to assistive technology.
4. WHEN keyboard focus moves to a formatting control, THE Rich_Text_Editor SHALL present a visible focus indicator on the focused control.

### Requirement 7: Plain-Text Email Client Compatibility

**User Story:** As a committee member, I want owners whose email clients do not render HTML to still read my message, so that no recipient receives an unreadable email.

#### Acceptance Criteria

1. WHERE decision D3 requires a Plain_Text_Alternative, WHEN the Owner_Email_Service sends an email, THE Owner_Email_Service SHALL send a multipart email containing an HTML part built from the Email_Template and a Plain_Text_Alternative part.
2. WHERE a Plain_Text_Alternative is produced, THE Owner_Email_Service SHALL derive the Plain_Text_Alternative from the Visible_Text of the Sanitized_Body so that the alternative conveys the same message content without markup.
3. WHERE a Plain_Text_Alternative is produced, THE Owner_Email_Service SHALL represent ordered lists, unordered lists, and paragraph breaks in the Plain_Text_Alternative using plain-text equivalents so that structure remains readable.
