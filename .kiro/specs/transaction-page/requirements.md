# Requirements Document

## Introduction

The Transaction Page presents member maintenance transactions in a single, searchable, filterable list. A transaction represents a maintenance payment made by a member (owner or tenant) against a maintenance bill for a unit, backed by the existing `MaintenancePayment` records.

The feature serves two audiences:

- **Administrators and committee members** who need to view all transactions across the society, apply filters, inspect details, and export records for reconciliation.
- **Members** who need to view the transactions associated with their own unit(s) only.

Both audiences share the same transaction record structure and filtering capabilities; access scope differs by role. This document defines what the Transaction Page must do. Technical design (endpoints, query construction, UI components) is deferred to the design phase.

## Glossary

- **Transaction**: A recorded maintenance payment, corresponding to one `MaintenancePayment` entity instance.
- **Transaction_Service**: The backend component that retrieves, filters, paginates, and returns transaction records.
- **Transaction_Page**: The frontend view that displays the transaction list, filters, and transaction details.
- **Member**: An authenticated user associated with one or more units, in the role of Owner or Tenant.
- **Owner**: A member who holds ownership of a unit.
- **Tenant**: A member who occupies a unit under tenancy.
- **Administrator**: An authenticated user with a role granting society-wide access to all transactions (e.g., admin or committee member).
- **Unit**: A residential/commercial unit in the society, identified by a unit number.
- **Payment_Mode**: The channel of a transaction. One of: CASHFREE_LINK, CASHFREE_QR, RAZORPAY, UPI, GPAY, PHONEPE, NEFT, RTGS, IMPS, CHEQUE, CASH, BANK_TRANSFER.
- **Transaction_Status**: The state of a transaction. One of: PENDING, SUCCESS, FAILED, VERIFIED, REVERSED.
- **Payer_Type**: The role of the person who made the payment. One of: OWNER, TENANT.
- **Date_Range**: A pair of a start date and an end date used to bound transactions by payment date.
- **Filter_Set**: The combined set of active filter criteria applied to a transaction query.
- **Page_Size**: The maximum number of transactions returned in a single page of results.

## Requirements

### Requirement 1: List all transactions (Administrator)

**User Story:** As an Administrator, I want to view all member transactions in one list, so that I can monitor and reconcile society maintenance collections.

#### Acceptance Criteria

1. WHEN an Administrator opens the Transaction_Page, THE Transaction_Service SHALL return the set of transactions for the society ordered by payment date in descending order, and for transactions sharing the same payment date, ordered by payment identifier in descending order.
2. THE Transaction_Service SHALL include in each returned transaction the payment identifier, unit number, payer name, payer type, amount, payment date, payment mode, transaction status, transaction reference identifier, and receipt number.
3. WHERE no transactions match the current Filter_Set, THE Transaction_Page SHALL display an empty-state message indicating that no transactions were found.
4. THE Transaction_Service SHALL return transactions in pages, where each page contains at most the configured Page_Size transactions, the Page_Size defaulting to 25 and constrained to a value between 1 and 100 inclusive.
5. WHEN an Administrator requests a specific page of transactions, THE Transaction_Service SHALL return the requested page along with the total transaction count, the current page number, the Page_Size, and the total page count.
6. IF an Administrator requests a page number that is less than 1 or greater than the total page count, THEN THE Transaction_Service SHALL return an empty transaction page along with the total transaction count, the requested page number, the Page_Size, and the total page count.
7. IF the Transaction_Service fails to retrieve transactions for the society, THEN THE Transaction_Page SHALL display an error message indicating that transactions could not be loaded and SHALL retain any previously displayed transactions without modification.

### Requirement 2: View own transactions (Member)

**User Story:** As a Member, I want to view the transactions for my own unit, so that I can track the maintenance payments I have made.

#### Acceptance Criteria

1. WHEN a Member opens the Transaction_Page, THE Transaction_Service SHALL return only the transactions associated with the units linked to that Member, in a paginated response containing at most 50 transactions per page.
2. WHEN a Member opens the Transaction_Page, THE Transaction_Service SHALL return the response within 3 seconds under normal load.
3. IF a Member requests transactions for a unit not linked to that Member, THEN THE Transaction_Service SHALL deny the request, return no transaction data, and return an authorization error indicating the requested unit is not accessible.
4. THE Transaction_Service SHALL order a Member's transactions by payment date in descending order, and for transactions sharing the same payment date SHALL order them by transaction identifier in descending order.
5. WHERE a Member has no recorded transactions, THE Transaction_Page SHALL display an empty-state message indicating that no transactions were found.
6. IF the Transaction_Service fails to retrieve transactions due to a data source error, THEN THE Transaction_Page SHALL display an error message indicating that transactions could not be loaded and SHALL retain any previously displayed data unchanged.

### Requirement 3: Filter transactions by date range

**User Story:** As a user of the Transaction_Page, I want to filter transactions by a date range, so that I can review payments made within a specific period.

#### Acceptance Criteria

1. WHEN a Date_Range with a start date and an end date is applied, THE Transaction_Service SHALL return only transactions whose payment date is on or after the start date and on or before the end date, sorted by payment date in descending order (most recent first).
2. WHERE only a start date is provided, THE Transaction_Service SHALL return only transactions whose payment date is on or after the start date.
3. WHERE only an end date is provided, THE Transaction_Service SHALL return only transactions whose payment date is on or before the end date.
4. IF the applied start date is later than the applied end date, THEN THE Transaction_Service SHALL reject the filter, retain the previously displayed transaction list unchanged, and return a validation error identifying the invalid Date_Range.
5. IF an applied start date or end date is not a valid calendar date, THEN THE Transaction_Service SHALL reject the filter, retain the previously displayed transaction list unchanged, and return a validation error identifying the invalid date value.
6. WHEN an applied Date_Range matches no transactions, THE Transaction_Service SHALL return an empty result set and indicate to the user that no transactions were found for the selected period.
7. WHERE neither a start date nor an end date is provided, THE Transaction_Service SHALL return all transactions without applying any date filter.

### Requirement 4: Filter transactions by payment mode

**User Story:** As a user of the Transaction_Page, I want to filter transactions by payment mode, so that I can review payments made through a specific channel.

#### Acceptance Criteria

1. WHEN a Payment_Mode filter is applied with a single value from the recognized set of Payment_Mode values {CASHFREE_LINK, CASHFREE_QR, RAZORPAY, UPI, GPAY, PHONEPE, NEFT, RTGS, IMPS, CHEQUE, CASH, BANK_TRANSFER}, THE Transaction_Service SHALL return only transactions whose payment mode equals the selected Payment_Mode, combined with the remaining Filter_Set using logical AND.
2. WHERE no Payment_Mode filter is applied, THE Transaction_Service SHALL return transactions of all payment modes subject to the remaining Filter_Set.
3. IF a supplied Payment_Mode value is not a member of the recognized set of Payment_Mode values {CASHFREE_LINK, CASHFREE_QR, RAZORPAY, UPI, GPAY, PHONEPE, NEFT, RTGS, IMPS, CHEQUE, CASH, BANK_TRANSFER}, THEN THE Transaction_Service SHALL reject the filter, return a validation error indicating the value is not a recognized payment mode, and return no transaction results, leaving stored transaction data unchanged.
4. WHEN a Payment_Mode filter is applied with a recognized value that matches zero transactions in the current Filter_Set, THE Transaction_Service SHALL return an empty result set without an error.

### Requirement 5: Filter transactions by status

**User Story:** As a user of the Transaction_Page, I want to filter transactions by status, so that I can identify pending, successful, failed, verified, or reversed payments.

#### Acceptance Criteria

1. WHEN a single Transaction_Status filter value from the set {PENDING, SUCCESS, FAILED, VERIFIED, REVERSED} is applied, THE Transaction_Service SHALL return only transactions whose status exactly equals the selected Transaction_Status, and SHALL exclude all transactions of any other status.
2. WHERE no Transaction_Status filter is applied, THE Transaction_Service SHALL return transactions of all statuses {PENDING, SUCCESS, FAILED, VERIFIED, REVERSED} subject to the remaining Filter_Set.
3. WHEN two or more Transaction_Status filter values from the set {PENDING, SUCCESS, FAILED, VERIFIED, REVERSED} are applied together, THE Transaction_Service SHALL return transactions whose status matches any one of the selected values (logical OR), subject to the remaining Filter_Set.
4. IF a supplied Transaction_Status value is not a member of the set {PENDING, SUCCESS, FAILED, VERIFIED, REVERSED}, THEN THE Transaction_Service SHALL reject the request, SHALL return a validation error indicating the invalid status value, and SHALL NOT apply any transaction filtering.
5. WHEN a Transaction_Status filter is applied and no transaction matches the selected value within the remaining Filter_Set, THE Transaction_Service SHALL return an empty result set with a total count of zero and SHALL NOT return a validation error.

### Requirement 6: Filter transactions by unit and payer type (Administrator)

**User Story:** As an Administrator, I want to filter transactions by unit and payer type, so that I can isolate the payments of a specific unit or category of payer.

#### Acceptance Criteria

1. WHEN a unit filter is applied by an Administrator with a valid Unit identifier that exists in the system, THE Transaction_Service SHALL return only transactions associated with the specified Unit, ordered by payment date descending, within 3 seconds.
2. IF a unit filter is applied with a Unit identifier that does not exist in the system, THEN THE Transaction_Service SHALL reject the filter and return a validation error indicating the unit was not found, while retaining the previously displayed result set unchanged.
3. WHEN a Payer_Type filter is applied with a value belonging to the set {OWNER, TENANT}, THE Transaction_Service SHALL return only transactions whose payer type equals the selected Payer_Type, within 3 seconds.
4. WHERE a unit search term of 1 to 50 characters is applied, THE Transaction_Service SHALL return only transactions whose unit number contains the search term using case-insensitive matching.
5. IF a unit search term exceeding 50 characters is supplied, THEN THE Transaction_Service SHALL reject the filter and return a validation error indicating the search term exceeds the maximum length, while retaining the previously displayed result set unchanged.
6. IF an unrecognized Payer_Type value that does not belong to the set {OWNER, TENANT} is supplied, THEN THE Transaction_Service SHALL reject the filter and return a validation error indicating the payer type is invalid, while retaining the previously displayed result set unchanged.

### Requirement 7: Combine multiple filters

**User Story:** As a user of the Transaction_Page, I want to apply multiple filters at once, so that I can narrow results precisely.

#### Acceptance Criteria

1. WHEN more than one filter is active in the Filter_Set, THE Transaction_Service SHALL return only transactions that satisfy every active filter using logical AND combination across all active filters.
2. WHEN an Administrator applies a Filter_Set, THE Transaction_Service SHALL apply the filters within the scope of all society transactions.
3. WHEN a Member applies a Filter_Set, THE Transaction_Service SHALL apply the filters within the scope of that Member's own transactions.
4. WHEN a user clears the Filter_Set, THE Transaction_Service SHALL return transactions subject only to the access scope of the requesting user.
5. IF an applied Filter_Set produces zero matching transactions, THEN THE Transaction_Service SHALL return an empty result set with a total count of 0 and an indication that no transactions matched the applied filters.
6. IF any active filter in the Filter_Set contains an invalid value, THEN THE Transaction_Service SHALL reject the request, return an error indicating which filter value is invalid, and preserve the previously applied Filter_Set unchanged.

### Requirement 8: View transaction details

**User Story:** As a user of the Transaction_Page, I want to view the full details of a single transaction, so that I can inspect payment specifics and reconcile records.

#### Acceptance Criteria

1. WHEN a user selects a transaction, THE Transaction_Page SHALL display the payer name, payer type, unit number, amount, original amount, discount amount, payment date, payment mode, transaction reference identifier, receipt number, status, and remarks for the selected transaction, AND for any of these fields that has no stored value THE Transaction_Page SHALL display an explicit empty-value placeholder rather than omitting the field.
2. WHEN a user selects a transaction, THE Transaction_Page SHALL display the requested transaction details within 3 seconds of the selection under normal operating conditions.
3. WHERE a transaction has been verified, THE Transaction_Page SHALL display the verification timestamp and the identifier of the verifier.
4. WHERE a transaction has been reversed, THE Transaction_Page SHALL display the reversal timestamp, the identifier of the reverser, and the reversal reason.
5. IF a user requests details for a transaction outside that user's access scope, THEN THE Transaction_Service SHALL deny the request and return an authorization error indicating the transaction is not accessible, and THE Transaction_Page SHALL display an error message indicating access is denied without displaying any transaction detail fields.
6. IF a user requests details for a transaction identifier that does not exist, THEN THE Transaction_Service SHALL return a not-found error, and THE Transaction_Page SHALL display an error message indicating the transaction was not found without displaying any transaction detail fields.
7. IF retrieval of the selected transaction details fails due to a service or data-source error, THEN THE Transaction_Service SHALL return a retrieval error, and THE Transaction_Page SHALL display an error message indicating the details could not be loaded and SHALL retain the previously displayed view without partial detail fields.

### Requirement 9: Search transactions by reference

**User Story:** As a user of the Transaction_Page, I want to search transactions by reference number, so that I can locate a specific payment quickly.

#### Acceptance Criteria

1. WHEN a search term of 1 to 100 characters is applied, THE Transaction_Service SHALL return only transactions whose receipt number or transaction reference identifier contains the search term using case-insensitive substring matching, subject to the requesting user's access scope, within 2 seconds.
2. WHERE the search term is empty or contains only whitespace, THE Transaction_Service SHALL return transactions subject only to the remaining Filter_Set.
3. IF a search term exceeding 100 characters is supplied, THEN THE Transaction_Service SHALL reject the search, return a validation error indicating the search term exceeds the maximum length, and retain the current result set unchanged.
4. WHEN an applied search term matches no transactions, THE Transaction_Service SHALL return an empty result set with a total count of 0.

### Requirement 10: Access control

**User Story:** As the society operator, I want transaction access restricted by role, so that members cannot see other members' payments.

#### Acceptance Criteria

1. IF a request is made to the Transaction_Service without valid authentication credentials, THEN THE Transaction_Service SHALL deny the request, return an authentication error indicating that valid credentials are required, and SHALL NOT return any transaction data.
2. WHILE a user holds a Member role without society-wide access, WHEN the user requests transactions, THE Transaction_Service SHALL return only transactions linked to the units associated with that Member and SHALL exclude all transactions linked to units not associated with that Member.
3. IF a user holding a Member role without society-wide access requests a specific transaction that is not linked to any unit associated with that Member, THEN THE Transaction_Service SHALL deny the request, return an authorization error indicating access is not permitted, and SHALL NOT return the requested transaction data.
4. WHILE a user holds an Administrator role, WHEN the user requests transactions, THE Transaction_Service SHALL return all transactions belonging to the society without unit-based restriction.
5. IF an authenticated user requests transactions but holds no role granting transaction access, THEN THE Transaction_Service SHALL deny the request, return an authorization error indicating access is not permitted, and SHALL NOT return any transaction data.
