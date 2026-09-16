# Requirements Document

## Introduction

The Owner Email feature enables authorized users to send email messages to owners in the Owners module. Users can send an email to all owners at once or to a manually selected subset of owners. The Owners module exposes a new "Email" action alongside the existing "Manage Unit Owners" action.

Each email is composed from a fixed three-part template: a Society Header (society identity details drawn from society settings), a Body (subject and message content entered by the user), and a Footer (society contact/signatory details drawn from society settings). Owners without an email address on file are excluded from sending and reported back to the user. Consistent with the existing mail infrastructure, the feature degrades gracefully when the mail transport is not configured, and reports partial-send outcomes when some recipients succeed and others fail.

## Glossary

- **Owner_Email_Service**: The backend service that assembles the templated email and sends it to owner recipients.
- **Owner_Email_Controller**: The backend REST endpoint that receives email-send requests from the frontend and delegates to the Owner_Email_Service.
- **Owner_Email_Component**: The frontend component that lets users select recipients, compose the email, and submit the send request.
- **Recipient**: An owner selected to receive the email, identified by owner ID.
- **Valid_Recipient**: A selected owner that has a non-blank email address on file.
- **Invalid_Recipient**: A selected owner that has no email address or a blank email address on file.
- **Email_Template**: The three-part structure applied to every email, composed of the Society_Header, the Body, and the Footer.
- **Society_Header**: The top section of the email, populated from society settings (society name, address, registration number, phone, and email).
- **Body**: The middle section of the email, containing the subject and message content entered by the user.
- **Footer**: The bottom section of the email, populated from society settings (chairman, secretary, and treasurer names).
- **Society_Settings**: The stored society profile record accessed via the existing society settings service.
- **Mail_Sender**: The email transport (Spring JavaMailSender). May be absent when mail is not configured.
- **Send_Report**: The response returned after a send attempt, listing recipient counts and per-recipient outcomes.
- **OWNER_EMAIL_SEND**: The permission that authorizes a user to send emails to owners.
- **getActiveOwnersList()**: The existing operation that returns the list of active owners eligible to receive emails.

## Requirements

### Requirement 1: Email Action Entry Point

**User Story:** As a committee member, I want an Email action in the Owners module beside the Manage Unit Owners action, so that I can start composing an email to owners from a single, familiar location.

#### Acceptance Criteria

1. WHERE the current user holds the OWNER_EMAIL_SEND permission, THE Owner_Email_Component SHALL display an Email action positioned immediately adjacent to the Manage Unit Owners action within the Owners module toolbar.
2. WHERE the current user does not hold the OWNER_EMAIL_SEND permission, THE Owner_Email_Component SHALL render the Owners module without the Email action present in the interface.
3. WHEN the user activates the Email action, THE Owner_Email_Component SHALL display the email composition view within 2 seconds.
4. IF the email composition view fails to load after the user activates the Email action, THEN THE Owner_Email_Component SHALL retain the current Owners module view and display an error indication informing the user that the email composition view could not be opened.

### Requirement 2: Recipient Selection

**User Story:** As a committee member, I want to choose whether to email all owners or only specific owners, so that I can target my message appropriately.

#### Acceptance Criteria

1. WHEN the email composition view is displayed, THE Owner_Email_Component SHALL present a recipient scope choice between all owners and selected owners, with the all owners scope pre-selected as the default.
2. WHERE the user chooses selected owners, THE Owner_Email_Component SHALL present the list of owners returned by getActiveOwnersList() for individual selection, supporting up to 10,000 owners in the displayed list.
3. WHERE the user chooses selected owners, THE Owner_Email_Component SHALL include exactly the owners the user marks as selected in the send request.
4. WHERE the user chooses all owners, THE Owner_Email_Component SHALL include every active owner returned by getActiveOwnersList() in the send request.
5. IF the user submits a send request in the selected owners scope with zero owners selected, THEN THE Owner_Email_Component SHALL block submission, retain the current recipient scope and any composed message content, and display a message requesting at least one recipient be selected.
6. IF the user submits a send request in the all owners scope and getActiveOwnersList() returns zero owners, THEN THE Owner_Email_Component SHALL block submission, retain any composed message content, and display a message indicating no active owners are available to receive the email.

### Requirement 3: Email Composition

**User Story:** As a committee member, I want to enter a subject and message body, so that I can communicate specific information to owners.

#### Acceptance Criteria

1. WHEN the email composition view is displayed, THE Owner_Email_Component SHALL provide a subject input accepting 1 to 200 characters and a message body input accepting 1 to 10,000 characters.
2. IF the user submits a send request with a subject that is empty or contains only whitespace, THEN THE Owner_Email_Component SHALL block submission and display a message requesting a subject.
3. IF the user submits a send request with a message body that is empty or contains only whitespace, THEN THE Owner_Email_Component SHALL block submission and display a message requesting message content.
4. IF the user submits a send request with a subject exceeding 200 characters or a message body exceeding 10,000 characters, THEN THE Owner_Email_Component SHALL block submission and display a message indicating the maximum allowed length.
5. IF a send request is received with a subject that is empty, contains only whitespace, or exceeds 200 characters, THEN THE Owner_Email_Controller SHALL reject the request with a validation error indicating the subject is invalid and SHALL retain no partial record of the request.
6. IF a send request is received with a message body that is empty, contains only whitespace, or exceeds 10,000 characters, THEN THE Owner_Email_Controller SHALL reject the request with a validation error indicating the message body is invalid and SHALL retain no partial record of the request.

### Requirement 4: Templated Email Assembly

**User Story:** As a society administrator, I want every owner email to follow the society template with a header, body, and footer, so that outbound communication is consistent and branded.

#### Acceptance Criteria

1. WHEN the Owner_Email_Service assembles an email, THE Owner_Email_Service SHALL produce content composed of the Society_Header, then the Body, then the Footer, in that order.
2. WHEN the Owner_Email_Service assembles the Society_Header, THE Owner_Email_Service SHALL populate it from the Society_Settings fields for society name, address, registration number, phone, and email.
3. WHEN the Owner_Email_Service assembles the Body, THE Owner_Email_Service SHALL populate it with the user-entered subject (maximum 200 characters) and message content (maximum 10,000 characters).
4. WHEN the Owner_Email_Service assembles the Footer, THE Owner_Email_Service SHALL populate it from the Society_Settings fields for chairman, secretary, and treasurer names.
5. WHEN the Owner_Email_Service sets the email subject, THE Owner_Email_Service SHALL use the user-entered subject.
6. IF the user-entered subject is empty or the user-entered message content is empty, THEN THE Owner_Email_Service SHALL reject the assembly request, retain the user-entered content, and return an error indication identifying the missing field.
7. IF a required Society_Settings field used in the Society_Header or Footer is missing or empty, THEN THE Owner_Email_Service SHALL reject the assembly request and return an error indication identifying the missing Society_Settings field.

### Requirement 5: Handling Owners Without an Email Address

**User Story:** As a committee member, I want owners without an email address to be skipped and reported, so that I know who did not receive the message.

#### Acceptance Criteria

1. WHEN the Owner_Email_Service processes recipients, THE Owner_Email_Service SHALL send the email only to Valid_Recipients.
2. WHEN the Owner_Email_Service processes recipients, THE Owner_Email_Service SHALL exclude every Invalid_Recipient from sending.
3. WHEN the Owner_Email_Service completes processing, THE Owner_Email_Service SHALL include in the Send_Report, for each excluded Invalid_Recipient, an entry containing the owner's unique identifier, the owner's name, and a reason indicating a missing or blank email address.
4. WHEN the Owner_Email_Service completes processing, THE Owner_Email_Service SHALL include in the Send_Report the total count of emails sent and the total count of recipients skipped, where the two counts sum to the total number of selected recipients.
5. IF every selected recipient is an Invalid_Recipient, THEN THE Owner_Email_Service SHALL send no email and SHALL return a Send_Report with an emails-sent count of zero and a skipped count equal to the number of selected recipients.
6. IF no recipients are selected, THEN THE Owner_Email_Service SHALL send no email and SHALL return a Send_Report with an emails-sent count of zero and a skipped count of zero.

### Requirement 6: Authorization

**User Story:** As a society administrator, I want only authorized users to send owner emails, so that outbound communication is controlled.

#### Acceptance Criteria

1. IF a send request is received from an unauthenticated user, THEN THE Owner_Email_Controller SHALL reject the request with an authentication error indicating that valid credentials are required and SHALL NOT initiate any email send operation.
2. IF a send request is received from an authenticated user who does not hold the OWNER_EMAIL_SEND permission, THEN THE Owner_Email_Controller SHALL reject the request with an authorization error indicating the required permission is missing and SHALL NOT initiate any email send operation.
3. WHERE a send request is received from an authenticated user holding the OWNER_EMAIL_SEND permission, THE Owner_Email_Controller SHALL accept the request for processing.
4. WHEN the Owner_Email_Controller rejects a send request for authentication or authorization reasons, THE Owner_Email_Controller SHALL record the rejected request in the application audit log.

### Requirement 7: Send Result Feedback and Partial-Send Reporting

**User Story:** As a committee member, I want clear feedback on how many emails were sent and which recipients failed, so that I can follow up on incomplete sends.

#### Acceptance Criteria

1. WHEN a send operation completes, THE Owner_Email_Service SHALL return a Send_Report containing the total number of recipients attempted, the count of emails sent successfully, and the count of recipients not emailed, where the sent count plus the not-emailed count equals the total attempted.
2. WHEN a send operation completes with one or more failed recipients, THE Owner_Email_Service SHALL include in the Send_Report, for each not-emailed recipient, an entry containing the owner's identity and a category of failure reason.
3. WHEN a send operation completes with zero successful sends, THE Owner_Email_Service SHALL return a Send_Report with a sent count of zero.
4. WHEN the Owner_Email_Component receives a Send_Report, THE Owner_Email_Component SHALL display the successful send count and the not-emailed count to the user within 2 seconds.
5. WHERE a Send_Report lists one or more not-emailed recipients, THE Owner_Email_Component SHALL display the identity and failure reason category of each not-emailed recipient.
6. IF no Send_Report is received within 30 seconds of submitting a send request, THEN THE Owner_Email_Component SHALL display an error indication, retain the composed message and recipient selection, and allow the user to retry.

### Requirement 8: Graceful Behavior When Mail Is Not Configured

**User Story:** As a society administrator, I want the feature to behave predictably when the mail transport is not configured, so that the application remains stable and users understand why no emails were sent.

#### Acceptance Criteria

1. IF the Mail_Sender is absent when a send request is received, THEN THE Owner_Email_Service SHALL send zero emails and SHALL return a Send_Report within 2 seconds indicating that mail is not configured, with a total recipient count, a sent count equal to 0, and a not-emailed count equal to the total recipient count.
2. IF the Mail_Sender is absent when a send request is received, THEN THE Owner_Email_Service SHALL complete the operation without raising an error to the caller and SHALL return control to the caller in a completed (non-failure) state.
3. IF sending to an individual Valid_Recipient fails, THEN THE Owner_Email_Service SHALL record that recipient as not emailed in the Send_Report and SHALL continue processing each of the remaining Valid_Recipients such that a failure for one recipient does not stop delivery to the others.
4. WHEN a send request completes, THE Owner_Email_Service SHALL return a Send_Report in which the sum of the sent count and the not-emailed count equals the total number of Valid_Recipients in the request.
5. IF sending to an individual Valid_Recipient fails, THEN THE Owner_Email_Service SHALL record the failure to the application log without propagating the failure to the caller.
