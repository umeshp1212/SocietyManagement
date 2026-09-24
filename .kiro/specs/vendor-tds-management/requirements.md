# Requirements Document

## Introduction

The Vendor TDS Management feature gives the society a single, filterable, paginated view of every amount of TDS (Tax Deducted at Source) it has already deducted from vendor payments, and adds a persistent two-stage remittance lifecycle that tracks that money as it moves from the society to its accountant, and then from the accountant to the Income Tax Department on the society's behalf.

TDS is already deducted at voucher time: each `PAYMENT` voucher to a vendor already carries `tdsApplicable`, `tdsSection`, `tdsRate`, and `tdsAmount`. This feature does not change how TDS is deducted. Instead it adds (a) an aggregated read model that lists deducted-TDS lines across all vendors with a remittance status, (b) a batch-based remittance lifecycle recorded in new `TdsRemittance` (batch) and `TdsRemittanceLine` (per-voucher link) entities, (c) Transaction-module-style filtering, (d) summary totals over the filtered set, (e) PDF export of the filtered list and summary, and (f) an Angular screen reachable from the Vendor module.

These requirements are derived from the approved design document (`design.md`) and describe the observable behavior the implementation must satisfy.

## Glossary

- **TDS_System**: The Vendor TDS Management module (backend services, filter specification, and API) that produces the deducted-TDS read model and manages the remittance lifecycle.
- **TDS (Tax Deducted at Source)**: Tax the society withholds from a vendor payment and is obligated to remit to the Income Tax Department.
- **Deducted_TDS_Line**: One row of the read model, projected from a single eligible source voucher, describing the vendor, TDS section, rate, deducted amount, deduction date, financial year, and current remittance status.
- **Source_Voucher**: A `Voucher` of type `PAYMENT` that qualifies as a deducted-TDS line because it is `FINAL`, has `tdsApplicable = true`, has `tdsAmount > 0`, and references a vendor.
- **Remittance_Batch**: A `TdsRemittance` record grouping one or more deducted-TDS voucher lines that are handled together through the remittance lifecycle. It carries a batch reference, status, financial year, total amount, and the recorded stage details.
- **Remittance_Line**: A `TdsRemittanceLine` record linking exactly one Source_Voucher to exactly one Remittance_Batch and snapshotting the voucher's deducted `tdsAmount`.
- **Challan**: The Income Tax Department challan identifier (CIN) recorded when the accountant remits the deducted TDS to the Income Tax Department.
- **DEDUCTED**: The implicit remittance status of a deducted-TDS line that does not yet belong to any Remittance_Batch. It is never persisted on a batch.
- **PAID_TO_ACCOUNTANT**: The remittance status recorded when the society has paid the deducted TDS amount to its accountant (stage 1).
- **PAID_TO_IT_DEPARTMENT**: The remittance status recorded when the accountant has remitted the deducted TDS to the Income Tax Department on the society's behalf (stage 2).
- **Financial_Year**: The Indian financial year label (e.g., "2024-2025") carried by each voucher and by each Remittance_Batch.
- **Remittance_Status**: One of DEDUCTED, PAID_TO_ACCOUNTANT, or PAID_TO_IT_DEPARTMENT, ordered forward in that sequence.

## Requirements

### Requirement 1: Deducted-TDS list and source-voucher eligibility

**User Story:** As a society finance user, I want to see every deducted-TDS line across all vendors, so that I have a single view of all tax I have withheld and its remittance status.

#### Acceptance Criteria

1. WHEN a user requests the deducted-TDS list, THE TDS_System SHALL return exactly one Deducted_TDS_Line for each eligible Source_Voucher, and SHALL return an empty list when no Source_Voucher is eligible.
2. THE TDS_System SHALL include a voucher as a Source_Voucher only WHERE the voucher status is FINAL, the voucher has tdsApplicable equal to true, the voucher tdsAmount is greater than 0, and the voucher references a vendor.
3. WHERE a voucher does not satisfy every eligibility condition listed in criterion 2, THE TDS_System SHALL exclude that voucher from the deducted-TDS list.
4. WHEN a Deducted_TDS_Line is returned, THE TDS_System SHALL include for that line: the vendor identifier, the vendor name (maximum 255 characters), the TDS section, the TDS rate as a percentage between 0.00 and 100.00 with two decimal places, the deducted TDS amount as a monetary value between 0.01 and 999,999,999.99 with two decimal places, the deduction date, the financial year, and the Remittance_Status for that line.
5. IF a Source_Voucher does not belong to any Remittance_Line, THEN THE TDS_System SHALL report that line's Remittance_Status as DEDUCTED.
6. WHERE a Source_Voucher belongs to exactly one Remittance_Line, THE TDS_System SHALL report that line's Remittance_Status as the status of the containing Remittance_Batch.
7. IF a Source_Voucher belongs to more than one Remittance_Line, THEN THE TDS_System SHALL report that line's Remittance_Status as the status of the Remittance_Batch containing the most recently created Remittance_Line for that Source_Voucher.

### Requirement 2: Filtering the deducted-TDS list

**User Story:** As a society finance user, I want to filter the deducted-TDS list by date, vendor, status, section, and financial year, so that I can focus on the exact lines I need to act on.

#### Acceptance Criteria

1. WHERE no filter field is provided, THE TDS_System SHALL return the full scoped set of deducted-TDS lines ordered by deduction date descending.
2. WHERE a filter field is blank or absent, THE TDS_System SHALL apply no predicate for that field.
3. WHERE a deduction start date is provided, THE TDS_System SHALL return only lines whose deduction date is on or after the deduction start date.
4. WHERE a deduction end date is provided, THE TDS_System SHALL return only lines whose deduction date is on or before the deduction end date.
5. WHERE a remittance start date is provided, THE TDS_System SHALL return only lines whose paid-to-accountant date or paid-to-IT date is on or after the remittance start date.
6. WHERE a remittance end date is provided, THE TDS_System SHALL return only lines whose paid-to-accountant date or paid-to-IT date is on or before the remittance end date.
7. WHERE a vendor identifier is provided, THE TDS_System SHALL return only lines for that vendor.
8. WHERE a vendor search term of 1 to 100 characters is provided, THE TDS_System SHALL return only lines whose vendor name contains the search term using case-insensitive matching.
9. WHERE one or more remittance statuses are provided, THE TDS_System SHALL return only lines whose Remittance_Status is one of the provided statuses.
10. WHERE a TDS section is provided, THE TDS_System SHALL return only lines whose TDS section equals the provided section.
11. WHERE a financial year is provided, THE TDS_System SHALL return only lines whose financial year equals the provided financial year.
12. WHEN multiple filter fields are provided, THE TDS_System SHALL return only lines that satisfy every provided filter field.
13. WHEN a filter is applied, THE TDS_System SHALL return a result set whose size is less than or equal to the size returned by the same request with any one of those filter fields removed.
14. WHEN a valid filter matches zero lines, THE TDS_System SHALL return an empty result set without reporting an error.
15. IF a filter contains a malformed value (an unparseable date, a vendor search term exceeding 100 characters, an unrecognized status, or an unrecognized section), THEN THE TDS_System SHALL reject the request and return an error indication identifying the offending field, without applying any filtering.
16. IF a deduction date range or a remittance date range has a start date later than its end date, THEN THE TDS_System SHALL reject the request and return an error indication that the date range is inverted.

### Requirement 3: Pagination of the deducted-TDS list

**User Story:** As a society finance user, I want the deducted-TDS list returned in pages, so that large result sets remain manageable.

#### Acceptance Criteria

1. WHEN a user requests the deducted-TDS list with a page number and page size, THE TDS_System SHALL return the requested page of lines together with the total element count and total page count for the applied filter.
2. IF no page number is provided, THEN THE TDS_System SHALL use page number 0.
3. IF no page size is provided, THEN THE TDS_System SHALL use a page size of 20.
4. THE TDS_System SHALL accept a page size between 1 and 200 inclusive.
5. IF a request provides a negative page number or a page size outside the range 1 to 200, THEN THE TDS_System SHALL reject the request and return an error indication identifying the invalid pagination parameter.
6. WHEN a requested page number is beyond the last available page for the applied filter, THE TDS_System SHALL return an empty page together with the correct total element count and total page count.

### Requirement 4: Summary totals over the filtered set

**User Story:** As a society finance user, I want summary totals for the lines I am viewing, so that I can see how much TDS is deducted, remitted, and still pending.

#### Acceptance Criteria

1. WHEN a user requests the summary for a filter, THE TDS_System SHALL compute the totals over exactly the same filtered set of Deducted_TDS_Line records that the list endpoint returns for that filter.
2. WHEN the summary is computed, THE TDS_System SHALL report the total deducted amount as the sum of the tdsAmount of every line in the filtered set.
3. WHEN the summary is computed, THE TDS_System SHALL report the total paid-to-accountant amount as the sum of the tdsAmount of lines whose Remittance_Status is PAID_TO_ACCOUNTANT or PAID_TO_IT_DEPARTMENT.
4. WHEN the summary is computed, THE TDS_System SHALL report the total paid-to-IT-department amount as the sum of the tdsAmount of lines whose Remittance_Status is PAID_TO_IT_DEPARTMENT.
5. WHEN the summary is computed, THE TDS_System SHALL report the total pending amount as the total deducted amount minus the total paid-to-IT-department amount.
6. WHEN the summary is computed, THE TDS_System SHALL report the line count as the number of lines in the filtered set.
7. WHEN the summary reports the line count, THE line count SHALL equal the total element count returned by the list endpoint under the same filter.
8. WHEN the summary computes any monetary total, THE TDS_System SHALL use rounding mode HALF_UP at scale 2.
9. WHEN the filtered set contains zero lines, THE TDS_System SHALL report all monetary totals as 0.00 and the line count as 0.
10. WHEN the summary is computed, THE total pending amount SHALL be greater than or equal to 0.00.

### Requirement 5: Batch creation (stage 1, society to accountant)

**User Story:** As a society finance user, I want to group deducted-TDS vouchers into a batch when I pay my accountant, so that I record that the society has handed the TDS to the accountant.

#### Acceptance Criteria

1. WHEN a user submits a batch-creation request with a list of 1 to 500 voucher identifiers, an accountant name of 1 to 100 characters, and a paid-to-accountant date, THE TDS_System SHALL create a Remittance_Batch with status PAID_TO_ACCOUNTANT.
2. WHEN a Remittance_Batch is created, THE TDS_System SHALL create one Remittance_Line for each supplied voucher, linking the voucher to the batch and snapshotting the voucher tdsAmount on the line.
3. IF the batch-creation request contains an empty list of voucher identifiers, THEN THE TDS_System SHALL reject the request without creating a Remittance_Batch or any Remittance_Line and return an error indication that at least one voucher is required.
4. IF any supplied voucher is not an eligible Source_Voucher, THEN THE TDS_System SHALL reject the request without creating a Remittance_Batch or any Remittance_Line and return an error indication that identifies the offending voucher identifiers.
5. IF any supplied voucher already belongs to a Remittance_Line, THEN THE TDS_System SHALL reject the request without creating a Remittance_Batch or any Remittance_Line and return an error indication that identifies the already-linked voucher identifiers.
6. IF the supplied vouchers span more than one financial year, THEN THE TDS_System SHALL reject the request without creating a Remittance_Batch or any Remittance_Line and return an error indication that the vouchers span multiple financial years.
7. IF the accountant name is empty or exceeds 100 characters, or the supplied voucher list exceeds 500 identifiers, THEN THE TDS_System SHALL reject the request without creating a Remittance_Batch or any Remittance_Line and return an error indication that identifies the invalid input.
8. IF the paid-to-accountant date is later than the current system date, THEN THE TDS_System SHALL reject the request without creating a Remittance_Batch or any Remittance_Line and return an error indication that the paid-to-accountant date must not be in the future.
9. WHEN a Remittance_Batch is created, THE TDS_System SHALL set the batch total amount to the sum of the snapshot tdsAmount of its lines using rounding mode HALF_UP at scale 2.
10. WHEN a Remittance_Batch is created, THE TDS_System SHALL set the batch financial year to the financial year of the linked vouchers.
11. WHEN a Remittance_Batch is created, THE TDS_System SHALL record the accountant name, the paid-to-accountant date, the accountant reference when provided, and the actor who recorded stage 1.
12. WHEN a Remittance_Batch is created, THE TDS_System SHALL assign the batch a unique batch reference.

### Requirement 6: IT-department remittance (stage 2, accountant to IT Department)

**User Story:** As a society finance user, I want to record when the accountant remits a batch to the Income Tax Department, so that I have a complete compliance record of the remittance.

#### Acceptance Criteria

1. WHEN a user records an IT-department remittance on a Remittance_Batch whose status is PAID_TO_ACCOUNTANT, THE TDS_System SHALL advance the batch status to PAID_TO_IT_DEPARTMENT.
2. WHEN an IT-department remittance is recorded, THE TDS_System SHALL store the challan number of 1 to 50 characters, the paid-to-IT date, and the identifier of the actor who recorded stage 2.
3. IF an IT-department remittance is requested with a challan number that is blank or exceeds 50 characters, or without a paid-to-IT date, THEN THE TDS_System SHALL reject the request, return an error indication identifying the invalid field, and leave the batch status and recorded data unchanged.
4. IF an IT-department remittance is requested with a paid-to-IT date later than the current system date, THEN THE TDS_System SHALL reject the request, return an error indication that the paid-to-IT date cannot be in the future, and leave the batch status and recorded data unchanged.
5. IF an IT-department remittance is requested for a Remittance_Batch whose status is not PAID_TO_ACCOUNTANT, THEN THE TDS_System SHALL reject the request, return an error indication that the batch is not in the PAID_TO_ACCOUNTANT status, and leave the batch status and recorded data unchanged.
6. IF an IT-department remittance is requested for an unknown batch identifier, THEN THE TDS_System SHALL reject the request and return an error indication that the batch was not found.

### Requirement 7: Forward-only remittance transitions

**User Story:** As a compliance owner, I want remittance status to move only forward through its stages, so that recorded remittance history cannot be reversed or skipped.

#### Acceptance Criteria

1. WHEN a status transition is requested and the target status is exactly one stage ahead of the current status in the order DEDUCTED, PAID_TO_ACCOUNTANT, PAID_TO_IT_DEPARTMENT, THE TDS_System SHALL apply the transition and persist the new status.
2. IF a status transition would move to the same or an earlier status, THEN THE TDS_System SHALL reject the transition, return an error indication, and leave the batch status unchanged.
3. IF a status transition would skip a stage, THEN THE TDS_System SHALL reject the transition, return an error indication, and leave the batch status unchanged.
4. IF a status transition is requested for a Remittance_Batch already in PAID_TO_IT_DEPARTMENT, THEN THE TDS_System SHALL reject the transition, return an error indication, and leave the batch status unchanged.
5. THE TDS_System SHALL allow a Source_Voucher to be associated with a Remittance_Line only WHERE that Source_Voucher is not already associated with a Remittance_Line.
6. IF a request would associate a Source_Voucher that already belongs to a Remittance_Line, THEN THE TDS_System SHALL reject the request and return an error indication that the voucher is already remitted.

### Requirement 8: Remittance batch retrieval

**User Story:** As a society finance user, I want to look up remittance batches and their lines, so that I can review what each batch contains and its recorded details.

#### Acceptance Criteria

1. WHEN a user requests a Remittance_Batch by identifier that matches an existing batch, THE TDS_System SHALL return that batch including its batch reference, Remittance_Status, financial year, total amount, recorded stage details, and all of its Remittance_Lines.
2. WHEN a matching Remittance_Batch is returned, THE TDS_System SHALL include for each Remittance_Line the linked Source_Voucher identifier and the snapshotted deducted TDS amount.
3. WHERE a matching Remittance_Batch has no Remittance_Lines, THE TDS_System SHALL return the batch with an empty line collection rather than reporting an error.
4. IF a user requests a Remittance_Batch with an identifier that matches no existing batch, THEN THE TDS_System SHALL return no batch and respond with an error indication that the batch was not found, without creating or modifying any Remittance_Batch.
5. WHEN a user requests a paginated list of Remittance_Batches with a page number, a page size, and a filter, THE TDS_System SHALL return the matching batches for the requested page together with the total element count and total page count over the applied filter.
6. IF no page number is provided, THEN THE TDS_System SHALL use page number 0, and IF no page size is provided, THEN THE TDS_System SHALL use a page size of 20.
7. WHERE a filter field is blank or absent, THE TDS_System SHALL apply no predicate for that field, and WHERE no filter field is provided, THE TDS_System SHALL return the full set of Remittance_Batches.

### Requirement 9: PDF export of the filtered list and summary

**User Story:** As a society finance user, I want to export the filtered deducted-TDS list and summary as a PDF, so that I can share or archive a compliance report.

#### Acceptance Criteria

1. WHEN a user requests a PDF export for a filter, THE TDS_System SHALL generate a PDF document containing every Deducted_TDS_Line that matches that filter, in the same order as the list endpoint returns them for that same filter.
2. WHEN the PDF export is generated, THE TDS_System SHALL include summary totals computed from exactly the same set of Deducted_TDS_Line records included in the PDF for that filter.
3. WHEN a user requests a PDF export for a filter, THE TDS_System SHALL complete generation and return the PDF document within 10 seconds.
4. IF the filter matches zero Deducted_TDS_Line records, THEN THE TDS_System SHALL generate a PDF document containing zero lines and summary totals of 0, and SHALL indicate that no records matched the filter.
5. IF PDF generation fails, THEN THE TDS_System SHALL NOT return a partial or corrupted PDF, and SHALL return an error indication that the export could not be generated.

### Requirement 10: Vendor-module access to the TDS screen

**User Story:** As a society finance user, I want to reach the TDS screen from the Vendor module, so that I can manage vendor TDS from the same area where I manage vendors.

#### Acceptance Criteria

1. WHERE the user is in the Vendor module, THE TDS_System SHALL provide a navigation entry that routes to the TDS screen.
2. WHEN the TDS screen is displayed, THE TDS_System SHALL present the filterable deducted-TDS table area, the summary cards, per-line status badges, a create-batch action, and a record-IT-remittance action, and WHEN zero Deducted_TDS_Line records exist, THE TDS_System SHALL still render the table area, the summary cards showing all monetary totals as 0.00 and all count totals as 0, and the create-batch and record-IT-remittance actions.
3. IF the user is not authorized to view TDS data, THEN THE TDS_System SHALL prevent access to the TDS navigation entry and the TDS screen and SHALL display an indication that the user is not authorized to view TDS data.
4. IF the TDS screen fails to load its Deducted_TDS_Line data within 10 seconds, THEN THE TDS_System SHALL display an error state indicating that TDS data could not be loaded and SHALL NOT display a blank or partially rendered screen.
5. WHEN an applied filter matches zero Deducted_TDS_Line records, THE TDS_System SHALL display an empty-result indication in the table area and SHALL display the summary cards with all monetary totals as 0.00 and all count totals as 0.
