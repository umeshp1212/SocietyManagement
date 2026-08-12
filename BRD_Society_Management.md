# Business Requirements Document (BRD)
## Society Management Web Application

---

## 1. Executive Summary

This document outlines the business requirements for a Society Management Web Application designed for a residential society comprising **164 flats** and **14 shops** (total 178 units). The application will digitize owner management, tenant/rental management, vendor management, voucher/expense management, and maintenance payment collection to bring transparency, efficiency, and ease of tracking.

---

## 2. Project Scope

| Module | Phase | Description |
|--------|-------|-------------|
| Owner Management | Phase 1 | Add/remove/transfer owners with full history |
| Vendor Management | Phase 1 | Manage society vendors, contracts, and payments |
| Rented Flat Management | Phase 1 | Tenant registration, NOC, police verification, agreement tracking |
| Voucher Management | Phase 1 | Create/update vouchers for all society expenses with audit trail |
| Maintenance Payment | Phase 2 | Online & offline payment collection with QR code and tracking |

---

## 3. Module 1: Owner Management (Phase 1)

### 3.1 Objectives
- Maintain a master registry of all 178 units (164 flats + 14 shops) with current owner details
- Support ownership transfer (flat sale/purchase) with full history
- Keep complete historical record of all past owners linked to each unit
- Track occupancy status of each unit (Self-occupied / Rented / Vacant)
- Serve as the foundation for all other modules (payments, tenants, etc.)

### 3.2 Unit Master Data

| Property | Details |
|----------|---------|
| Flats | 164 units (wing-wise, e.g., A-101 to D-414) |
| Shops | 14 units (e.g., S-01 to S-14) |
| Total | 178 units |

### 3.3 Unit Data Fields

| Field | Description |
|-------|-------------|
| Unit ID | Auto-generated primary key |
| Unit Number | Flat/Shop identifier (e.g., A-101, S-01) |
| Wing | Building wing (A, B, C, D, etc.) |
| Floor | Floor number |
| Type | Flat / Shop |
| Area (sq ft) | Carpet/built-up area |
| Monthly Maintenance Amount | Based on area or flat rate |
| Occupancy Status | Self-Occupied / Rented / Vacant |
| Current Owner ID | FK to Owner table |
| Status | Active / Under Transfer |

### 3.4 Owner Data Fields

| Field | Description |
|-------|-------------|
| Owner ID | Auto-generated unique identifier |
| Full Name | Owner's full legal name |
| Contact Number | Primary phone |
| Alternate Number | Secondary phone (optional) |
| Email | Email address |
| Aadhar Number | Masked/encrypted storage |
| PAN Number | For financial records |
| Permanent Address | If different from society address |
| Occupation | Optional |
| Photo | Owner photograph |
| Family Members Count | Number of people in unit |
| Emergency Contact | Name + phone for emergencies |
| Date of Purchase | When they bought the unit |
| Registration Document | Sale deed / agreement upload |
| Status | Active / Transferred / Deceased |

### 3.5 Ownership Transfer (Flat Sale/Purchase)

When a flat is sold, the system must:
1. **Retain the old owner record** (never delete)
2. **Link old owner to the unit** in history table with date range
3. **Register new owner** with all details
4. **Update current owner** on the unit record
5. **Transfer/close any tenant records** (new owner decides on existing tenants)
6. **Settle outstanding balance** (old owner's dues must be cleared or transferred)

#### Transfer Workflow:

```
Current Owner initiates transfer request
    --> Admin verifies documents (sale deed, NOC)
        --> Outstanding dues check (must be cleared)
            --> Committee acknowledgment
                --> New owner registered
                    --> Old owner marked as "Transferred"
                        --> Unit linked to new owner
```

### 3.6 Ownership History Table

| Field | Description |
|-------|-------------|
| History ID | Auto-generated |
| Unit ID | Which unit |
| Owner ID | Which owner |
| Ownership Start Date | Date of purchase/registration |
| Ownership End Date | Date of transfer (NULL if current) |
| Transfer Type | Purchase / Inheritance / Gift / Court Order |
| Transfer Document | Uploaded sale deed / legal document |
| Remarks | Any notes about the transfer |
| Recorded By | Admin who processed the transfer |
| Recorded On | Timestamp |

**Example:**
```
history_id | unit_id | owner_id | start_date | end_date   | transfer_type | status
-----------|---------|----------|------------|------------|---------------|--------
1          | 1       | 101      | 2015-03-01 | 2023-06-15 | Purchase      | Transferred
2          | 1       | 205      | 2023-06-15 | NULL       | Purchase      | Current
```
This shows Unit A-101 was owned by Owner #101 from 2015 to 2023, then transferred to Owner #205 who is the current owner.

### 3.7 Owner Management Features

| Feature | Description |
|---------|-------------|
| Add New Owner | Register owner with unit allocation |
| Edit Owner Details | Update phone, email, family members, etc. |
| Transfer Ownership | Full workflow with document upload and history preservation |
| View Ownership History | See all past and current owners for any unit |
| Owner Directory | Searchable list of all current owners |
| Occupancy Dashboard | Self-occupied vs Rented vs Vacant breakdown |
| Outstanding Dues on Transfer | Block transfer if dues pending (configurable) |
| Bulk Import | Initial data load via Excel/CSV for all 178 units |
| Owner Documents | Store sale deed, ID proofs, nomination forms |
| Deactivate Owner | Mark as inactive on transfer (never delete) |

### 3.8 Business Rules (Owner Management)

1. **Owner cannot be removed/deleted** - Owner records are permanent in the Owner Master. An owner can only be **updated** (edit details) or **transferred** (ownership moves to new owner). No delete operation exists.
2. **Owner Master is permanent** - All owners (past and current) remain in the master table forever with status indicating Active/Transferred
3. **One current owner per unit** - Unit can have only one active owner at a time
4. **Dues clearance on transfer** - System warns if outstanding balance exists during transfer
5. **Document mandatory for transfer** - Sale deed or legal document required
6. **Transfer date recorded** - Exact date of ownership change
7. **Historical audit** - Full chain of ownership viewable for any unit via Ownership History table
8. **Owner can own multiple units** - Support for owners with more than one flat/shop
9. **Nomination record** - Store nominee details for the unit (optional)
10. **Update allowed anytime** - Phone, email, family members, photo can be updated for any owner (Active or Transferred)

---

## 4. Module 2: Vendor Management (Phase 1)

### 4.1 Objectives
- Maintain a database of all society vendors/service providers
- Track contracts, agreements, and payment schedules
- Record vendor payments with proper documentation
- Rate and review vendor performance

### 4.2 Vendor Master Data

| Field | Description |
|-------|-------------|
| Vendor ID | Auto-generated unique ID |
| Vendor Name | Business/individual name |
| Category | Security, Housekeeping, Plumbing, Electrical, Gardening, Lift Maintenance, Pest Control, etc. |
| Contact Person | Name of contact |
| Phone | Primary contact number |
| Email | Vendor email |
| Address | Business address |
| PAN/GST | Tax identification |
| Bank Details | Account number, IFSC, bank name (for payments) |
| Agreement Start Date | Contract start |
| Agreement End Date | Contract end |
| Monthly/Annual Amount | Contracted payment amount |
| Payment Frequency | Monthly/Quarterly/Annual/One-time |
| Documents | Uploaded contracts, agreements, ID proofs |
| Status | Active / Inactive / Blacklisted |

### 4.3 Vendor Categories (Typical for Society)

| Category | Examples |
|----------|----------|
| Security | Guard agency |
| Housekeeping | Cleaning staff agency |
| Gardening | Landscaping vendor |
| Lift Maintenance | Elevator service company |
| Plumbing | Plumber (regular/on-call) |
| Electrical | Electrician (regular/on-call) |
| Pest Control | Quarterly pest control service |
| Fire Safety | AMC provider |
| CCTV/Intercom | Maintenance vendor |
| Water Tank Cleaning | Periodic service |
| Painting/Civil | As needed |
| Legal/Audit | CA firm, Legal advisor |
| Software/IT | Website, app maintenance |

### 4.4 Features

| Feature | Description |
|---------|-------------|
| Add/Edit/Deactivate Vendor | Full CRUD operations |
| Contract Tracking | Alert before contract expiry (30/15/7 days) |
| Payment Schedule | Auto-remind when vendor payment is due |
| Payment History | All payments made to a vendor with references |
| Document Storage | Upload and view contracts, invoices |
| Vendor Rating | Rate vendors on quality, punctuality, cost |
| Vendor Comparison | Compare vendors in same category |
| Renewal Alerts | Auto-notification for contract renewals |

---

## 5. Module 3: Rented Flat Management (Phase 1)

### 5.1 Objectives
- Maintain a registry of all rented units in the society
- Track tenant details with proper documentation (police verification, agreement)
- Map tenants to unit owners for accountability
- Track rental agreement validity and send renewal alerts
- Ensure society NOC process is followed for all rentals
- Provide move-in/move-out tracking for society records

### 5.2 Tenant Master Data

| Field | Description |
|-------|-------------|
| Tenant ID | Auto-generated unique identifier |
| Tenant Name | Full name of tenant |
| Contact Number | Primary phone number |
| Email | Email address (optional) |
| Aadhar Number | For identification (masked storage) |
| PAN Number | Optional |
| Permanent Address | Tenant's native/permanent address |
| No. of Family Members | Count of people staying |
| Family Members Details | Name, age, relation of each member |
| Vehicle Details | Vehicle number, type (for parking allocation) |
| Unit Number | Flat/Shop being rented |
| Owner Name | Auto-populated from unit master |
| Rent Start Date | When tenant moved in |
| Rent End Date | Agreement end date |
| Monthly Rent Amount | Rent amount (for owner's record, optional) |
| Security Deposit | Deposit amount (optional) |
| Agreement Document | Uploaded rent agreement (PDF/image) |
| Police Verification Status | Pending / Submitted / Verified |
| Police Verification Document | Uploaded verification receipt |
| Society NOC Status | Pending / Approved / Rejected |
| NOC Document | Uploaded NOC copy |
| Tenant Photo | ID photo of tenant |
| Status | Active / Notice Period / Vacated |
| Move-out Date | Actual date when tenant vacated |
| Move-out Reason | Transfer, personal, dispute, etc. |

### 5.3 Society NOC Process (Workflow)

```
Owner submits rental request
    --> Secretary reviews documents
        --> Committee approval (if required)
            --> NOC issued
                --> Tenant registered in system
```

| Step | Action | Responsible |
|------|--------|-------------|
| 1 | Owner fills "Rent Out" form with tenant details | Owner |
| 2 | Uploads: Agreement copy, Tenant ID proof, Police verification | Owner |
| 3 | Secretary reviews documents for completeness | Secretary |
| 4 | Committee approves/rejects (for new tenants) | Committee |
| 5 | NOC is generated and issued | Secretary/System |
| 6 | Tenant is registered in system with move-in date | Admin |
| 7 | Tenant gets login access (optional - Phase 2) | System |

### 5.4 Owner-Tenant Mapping

| Rule | Description |
|------|-------------|
| One unit = One active tenant | Only one tenant record can be active per unit at a time |
| Owner remains responsible | Maintenance liability stays with owner even if rented |
| Historical records | All past tenants are preserved for audit/history |
| Owner visibility | Owner can view tenant details for their unit |

### 5.5 Police Verification Tracking

| Status | Meaning |
|--------|---------|
| Not Initiated | Owner hasn't started the process |
| Submitted | Application submitted to police station |
| Verified | Police verification completed successfully |
| Rejected | Verification failed/issues found |
| Expired | Verification older than 1 year (needs renewal) |

**Alert:** System sends reminder if police verification is pending beyond 15 days of move-in.

### 5.6 Agreement & Document Management

| Document | Required | Purpose |
|----------|----------|---------|
| Rent Agreement (Registered) | Mandatory | Legal proof of rental |
| Tenant Aadhar Card | Mandatory | Identity verification |
| Tenant Photo | Mandatory | Identification |
| Police Verification Receipt | Mandatory | Legal compliance |
| Society NOC | Auto-generated | Society approval record |
| Owner's Authorization Letter | Optional | If tenant handles maintenance |
| Previous Address Proof | Optional | Background reference |

### 5.7 Move-in / Move-out Workflow

#### Move-in Process:
1. Owner submits tenant registration request
2. Uploads all required documents
3. Admin/Secretary reviews and approves
4. NOC is generated
5. Tenant is marked as "Active"
6. Unit occupancy status changes to "Rented"
7. Security team is notified of new tenant

#### Move-out Process:
1. Owner/Tenant submits vacating notice (with expected date)
2. System marks tenant as "Notice Period"
3. On actual move-out date, admin updates status to "Vacated"
4. All pending dues are checked
5. Security team is notified
6. Unit status changes back to "Vacant" or "Self-occupied"
7. Tenant record preserved in history

### 5.8 Rental Reports

| Report | Description |
|--------|-------------|
| Rented Units List | All currently rented flats/shops with tenant details |
| Self-Occupied vs Rented vs Vacant | Occupancy breakdown |
| Police Verification Status | Units with pending/expired verification |
| Agreement Expiry Report | Agreements expiring in next 30/60/90 days |
| Tenant History (per unit) | All tenants who have lived in a unit |
| Move-in/Move-out Log | Monthly movement register |
| NOC Pending List | Rental requests awaiting approval |
| Family Members Count | Total society population (owners + tenants) |
| Vehicle Register | All tenant vehicles for parking management |

### 5.9 Alerts & Notifications

| Alert | Recipient | Timing |
|-------|-----------|--------|
| Agreement expiring | Owner + Admin | 60, 30, 15 days before expiry |
| Police verification pending | Owner + Admin | 15 days after move-in |
| Police verification expiring | Owner + Admin | 30 days before 1-year mark |
| NOC approval pending | Secretary | 3 days after submission |
| Tenant move-out notice | Committee | On notice submission |
| New tenant registered | Security + Committee | On activation |

---

## 6. Module 4: Voucher Management (Phase 1)

### 6.1 Objectives
- Record every society expense/income as a voucher
- Support creation and update of vouchers with complete audit trail
- Every modification is tracked (who changed what, when, and why)
- Link expenses to vendors where applicable
- Generate financial reports for society meetings and auditors

### 6.2 Voucher Types

| Type | Use Case |
|------|----------|
| Payment Voucher | Regular vendor payments, utility bills, any outgoing payment |
| Receipt Voucher | Recording money received (maintenance, interest, penalty, event fees) |
| Journal Voucher | Internal adjustments, corrections |
| Contra Voucher | Bank to cash or vice versa |

### 6.3 Voucher Data Fields

| Field | Description |
|-------|-------------|
| Voucher Number | Auto-generated (e.g., PV-2026-001, RV-2026-001) |
| Voucher Date | Date of transaction |
| Voucher Type | Payment/Receipt/Journal/Contra |
| Category | Maintenance, Repair, Salary, Utility, Event, Legal, etc. |
| Sub-Category | More specific classification (optional) |
| Vendor (if applicable) | Linked to vendor master |
| Description | Purpose/narration of the entry |
| Amount | Transaction amount |
| Payment Mode | Cheque/UPI/NEFT/Cash/Bank Transfer |
| Reference Number | Cheque no./Transaction ID/UTR |
| Bill/Invoice Number | Vendor's bill reference |
| Bill Date | Date on vendor's bill |
| Created By | Person who created the voucher |
| Created On | Timestamp of creation |
| Last Modified By | Person who last updated |
| Last Modified On | Timestamp of last update |
| Attachments | Bill photo, invoice PDF, supporting documents |
| Status | Draft / Final / Cancelled |
| Cancellation Reason | If cancelled, why |
| Financial Year | Auto-set (April to March) |

### 6.4 Voucher Creation Rules

| Rule | Description |
|------|-------------|
| Mandatory fields | Date, Type, Category, Amount, Description, Payment Mode |
| Voucher numbering | Auto-sequential per type per financial year (PV-2026-001, PV-2026-002...) |
| Duplicate check | Warn if same vendor + same amount + same date exists |
| Attachment recommended | System prompts to attach bill/invoice (not mandatory) |
| Financial year lock | Cannot create vouchers in a closed/locked financial year |
| Backdated entries | Allowed with reason (captured in audit trail) |

### 6.5 Voucher Update & Audit Trail

**Core Principle:** Vouchers are never truly "edited" - every change creates an audit record.

#### What can be updated:
| Field | Updatable | Condition |
|-------|-----------|-----------|
| Description/Narration | Yes | Always |
| Amount | Yes | Only if status is "Draft" |
| Category | Yes | Always |
| Vendor | Yes | Only if status is "Draft" |
| Payment Mode/Reference | Yes | Always (corrections allowed) |
| Attachments | Yes | Can add more, cannot delete uploaded ones |
| Status | Yes | Draft -> Final, Final -> Cancelled (with reason) |

#### What cannot be changed:
- Voucher Number (immutable)
- Created By / Created On (immutable)
- Financial Year (immutable)

#### Audit Trail Table:

| Field | Description |
|-------|-------------|
| Audit ID | Auto-generated |
| Voucher ID | Which voucher was modified |
| Field Changed | Which field was updated |
| Old Value | Previous value |
| New Value | Updated value |
| Changed By | User who made the change |
| Changed On | Timestamp |
| Reason | Why the change was made (mandatory for amount/status changes) |
| IP Address | For security audit |

**Example Audit Trail:**
```
audit_id | voucher_id | field_changed | old_value | new_value | changed_by | changed_on          | reason
---------|------------|---------------|-----------|-----------|------------|---------------------|---------------------------
1        | PV-2026-005| amount        | 45000     | 47000     | Treasurer  | 2026-08-10 14:30:00 | Revised bill received
2        | PV-2026-005| status        | Draft     | Final     | Treasurer  | 2026-08-10 14:35:00 | Verified and finalized
3        | PV-2026-003| status        | Final     | Cancelled | Secretary  | 2026-08-10 15:00:00 | Duplicate entry, actual is PV-2026-004
```

### 6.6 Expense Categories

| Category | Examples |
|----------|----------|
| Security | Monthly guard service payment |
| Housekeeping | Cleaning staff salary |
| Electricity (Common) | Common area electricity bill |
| Water | Water tanker, supply charges |
| Lift Maintenance | AMC, repair |
| Garden | Gardener salary, plants, fertilizer |
| Repairs & Maintenance | Plumbing, electrical, civil work |
| Pest Control | Quarterly service |
| Legal & Professional | CA fees, legal fees |
| Stationery & Printing | Office supplies |
| Events & Celebrations | Festival, annual day expenses |
| Insurance | Society insurance premium |
| Sinking Fund Expense | Major repairs from reserve |
| Bank Charges | Account maintenance, transaction fees |
| Miscellaneous | Other uncategorized expenses |

### 6.7 Voucher Reports

| Report | Description |
|--------|-------------|
| Monthly Expense Report | Category-wise expenses for the month |
| Vendor-wise Payment Report | Total paid to each vendor in a period |
| Voucher Register | List of all vouchers with filters (date, type, category, status) |
| Cash Flow Statement | Income vs Expenses over time |
| Outstanding Payments | Pending vendor payments |
| Cancelled Vouchers Report | All cancelled entries with reasons |
| Audit Trail Report | All modifications made to vouchers |
| Category-wise Annual Summary | Year-end category breakdown for AGM |
| Day Book | All transactions on a given date |

### 6.8 Business Rules (Voucher Management)

1. **Auto-numbering** - System generates voucher numbers, no manual entry
2. **No deletion** - Vouchers can only be cancelled, never deleted
3. **Cancellation requires reason** - Mandatory field when cancelling
4. **Audit every change** - Every field modification is logged
5. **Amount change only in Draft** - Once finalized, amount cannot change (cancel and recreate)
6. **Attachment preservation** - Uploaded documents cannot be removed (only add more)
7. **Financial year boundary** - Vouchers belong to a financial year, locked after year-end closing
8. **Backdated entry flag** - If voucher date < creation date, system flags it in audit
9. **Sequential numbering** - No gaps in voucher numbers (cancelled ones retain their number)
10. **Vendor linkage** - If vendor selected, validate vendor is active

---

## 7. Module 5: Maintenance Payment (Phase 2)

### 7.1 Objectives
- Enable flat/shop owners to pay maintenance online via multiple modes
- Track all payments (online and offline) against the correct unit (flat/shop number)
- Generate receipts and payment history
- Provide society admin with a dashboard for payment status across all 178 units
- Support QR code-based payment on society website

### 7.2 Payment Modes Supported

#### A. Online Payments (via Society Website/App)

| Mode | How it works |
|------|--------------|
| **QR Code Payment** | A dynamic or static QR code displayed on the society website. When scanned, it opens a payment page that **mandatorily asks for flat/shop number** before completing payment. |
| **Payment Gateway** | Integrated payment gateway (Razorpay/PayU/Cashfree) supporting UPI, Net Banking, Credit/Debit Card. Flat number is captured in the payment form. |

**Flow for Online Payment:**
1. Owner/Tenant visits society website or app
2. Clicks "Pay Maintenance"
3. Selects/enters Flat/Shop number
4. System shows outstanding amount
5. Owner selects payment mode (QR/UPI/Card/NetBanking)
6. Payment is processed
7. Receipt is auto-generated and sent via SMS/Email
8. Payment is auto-recorded against the unit
9. Receipt voucher auto-created in Voucher module

#### B. Offline Payments (Recorded by Admin/Treasurer)

For payments made outside the system (directly to society bank account or in person):

| Mode | Recording Method |
|------|-----------------|
| **Cheque** | Admin enters cheque number, bank name, date, amount, flat number |
| **Direct UPI (GPay/PhonePe/Paytm)** | Admin enters UPI transaction ID, date, amount, flat number |
| **Bank Transfer (NEFT/RTGS/IMPS)** | Admin enters transaction reference, bank, date, amount, flat number |
| **Cash** | Admin enters amount, date, flat number, receipt number |

**Flow for Offline Payment Recording:**
1. Owner/Tenant pays via their preferred method (GPay, cheque, bank transfer, etc.)
2. Owner informs society admin/treasurer (via WhatsApp/in-person)
3. Admin logs into the system
4. Goes to "Record Offline Payment"
5. Selects flat/shop number
6. Enters payment details (mode, reference ID, amount, date)
7. Selects payer: Owner or Tenant (if rented)
8. Optionally uploads proof (screenshot/cheque photo)
9. Payment is recorded against the unit
10. Receipt is generated
11. Receipt voucher auto-created in Voucher module

### 7.3 QR Code Specification

| Aspect | Requirement |
|--------|-------------|
| Type | UPI QR Code linked to society bank account |
| Display | On society website, notice board (printable) |
| Identification | Before payment, system must capture flat/shop number |
| Dynamic vs Static | **Recommended: Semi-dynamic** - Static QR with a pre-payment form that captures unit details |
| Fallback | If direct UPI QR is scanned (without form), admin records it as offline payment |

### 7.4 Payment Tracking & Reports

| Feature | Description |
|---------|-------------|
| Unit-wise Payment History | View all payments for a specific flat/shop |
| Monthly Collection Report | Total collected vs pending for a given month |
| Defaulter List | Units with overdue payments |
| Payment Mode Analysis | Breakdown by QR, UPI, Cheque, Cash, etc. |
| Receipt Generation | Auto-generate and download/email receipts |
| Outstanding Balance | Real-time balance for each unit |
| Year-wise Summary | Annual payment summary per unit |
| Payer-wise Tracking | Track whether owner or tenant paid |

### 7.5 Maintenance Payment Responsibility (Rented Units)

| Scenario | Who Pays | System Handling |
|----------|----------|-----------------|
| Owner pays maintenance | Owner | Normal payment flow, recorded against unit |
| Tenant pays on behalf of owner | Tenant | System records payer as "Tenant", credited to unit |
| Owner authorizes tenant | Owner sets flag | Tenant gets payment access, receipts go to both |

**Business Rule:** Regardless of who pays, the **maintenance liability is always on the owner**. If tenant doesn't pay, outstanding shows against the unit (owner's responsibility).

### 7.6 Notifications

- Payment confirmation SMS/Email to owner (and tenant if applicable)
- Monthly reminder to defaulters (auto/manual)
- Receipt download link via WhatsApp/Email
- Admin alert for new payments received

### 7.7 Integration with Voucher Module

Every maintenance payment (online or offline) automatically creates a **Receipt Voucher** in the Voucher Management module:
- Voucher Type: Receipt Voucher
- Category: Maintenance
- Amount: Payment amount
- Reference: Payment transaction ID
- Unit Number: In description
- Status: Final (auto-created, no draft stage)

This ensures all income is captured in the society's books without duplicate data entry.

---

## 8. User Roles & Permissions

| Role | Owner Mgmt | Vendor | Rented Flat | Voucher | Maintenance (Ph2) |
|------|-----------|--------|-------------|---------|-------------------|
| **Super Admin** | Full access | Full access | Full access | Full access | Full access |
| **Chairperson** | Approve transfers | Approve contracts | Approve NOC | View all, approve cancellations | View reports |
| **Secretary** | Manage owners, process transfers | Manage vendors | Manage tenants, issue NOC | Create/update vouchers | Record payments |
| **Treasurer** | View only | View payments | View only | Create/update vouchers, reports | Record payments, reports |
| **Committee Member** | View | View | Approve NOC (if assigned) | View | View reports |
| **Unit Owner** | View own details, initiate transfer | No access | Register tenant | No access | Pay online, view history |
| **Tenant** | No access | No access | View own details, upload docs | No access | Pay (if authorized) |
| **Auditor** | Read-only | Read-only | Read-only | Read-only (including audit trail) | Read-only |

---

## 9. Technical Recommendations

### 9.1 Tech Stack (Finalized)

| Layer | Technology |
|-------|-----------|
| Frontend | Angular 17+ (standalone components, Angular Material) |
| Backend | Spring Boot 3.x (Java 17+) |
| Database | MySQL 8.x |
| ORM | Spring Data JPA / Hibernate |
| API Style | REST API (JSON) |
| Build Tool | Maven (backend), Angular CLI (frontend) |
| Payment Gateway (Phase 2) | Razorpay / Cashfree (supports UPI, QR, Cards) |
| File Storage | Local filesystem (Phase 1) / AWS S3 (Phase 2+) |
| Notifications (Phase 2) | Twilio (SMS) / SendGrid (Email) |
| Authentication | Spring Security + JWT |
| API Documentation | Swagger / SpringDoc OpenAPI |

### 9.2 QR Code Payment Solution (Phase 2)

**Option A: Payment Gateway QR (Recommended for Phase 2)**
- Use Razorpay/Cashfree QR code generation API
- Generate unit-specific payment links
- Flat number is embedded in the payment link
- Auto-reconciliation when payment is received
- Best tracking, zero manual effort

**Option B: Static UPI QR + Pre-form (MVP for Phase 2)**
- Display society UPI QR on website
- Before showing QR, force user to enter flat number
- After payment, user submits transaction ID
- Admin verifies and confirms
- Lower cost, moderate manual effort

**Recommendation:** Start with **Option B** when Phase 2 begins, upgrade to **Option A** as budget allows.

---

## 10. Non-Functional Requirements

| Requirement | Specification |
|-------------|--------------|
| Availability | 99.5% uptime |
| Security | HTTPS, encrypted data at rest, role-based access |
| Data Privacy | Aadhar/PAN encrypted, masked display |
| Data Backup | Daily automated backups |
| Performance | Page load < 3 seconds |
| Mobile Responsive | Must work on mobile browsers |
| Data Retention | Minimum 7 years financial data, permanent owner history |
| Audit Logging | All actions logged with timestamp, user, and IP |
| Multi-language | English + Hindi (optional regional language) |
| Document Security | Uploaded documents accessible only to authorized roles |
| Scalability | Support up to 500 units for future society growth |

---

## 11. Implementation Phases

### Phase 1: Foundation (8-10 weeks)

| Week | Deliverable |
|------|-------------|
| 1-2 | Project setup, database design, authentication, unit master with bulk import |
| 3-4 | Owner Management - registration, edit, transfer workflow, ownership history |
| 5-6 | Vendor Management - CRUD, contract tracking, document upload, alerts |
| 7-8 | Rented Flat Management - tenant registration, NOC workflow, police verification tracking |
| 9-10 | Voucher Management - create/update vouchers, audit trail, reports |

**Phase 1 Deliverables:**
- Admin portal with login (Secretary, Treasurer, Chairperson, Committee)
- Owner login (view own details, register tenant)
- Unit master with all 178 units loaded
- Owner registration with transfer history
- Vendor CRUD with contract management
- Tenant registration with full NOC workflow
- Voucher creation/update with complete audit trail
- Basic reports for all modules
- Document upload and storage

### Phase 2: Online Payments (6 weeks after Phase 1)

| Week | Deliverable |
|------|-------------|
| 1-2 | Payment module UI, offline payment recording, receipt generation |
| 3-4 | QR code integration, online payment flow with flat number capture |
| 5-6 | Payment reports, defaulter tracking, auto-receipt voucher creation, notifications |

**Phase 2 Deliverables:**
- QR code payment on society website
- Online payment with mandatory flat number
- Offline payment recording (cheque, UPI, bank transfer, cash)
- Auto-receipt generation
- Integration with Voucher module (auto-create receipt vouchers)
- Payment dashboard and reports
- SMS/Email notifications
- Defaulter list and reminders

### Phase 3: Enhancements (Future)

- Payment gateway auto-reconciliation (Razorpay/Cashfree)
- WhatsApp notifications and reminders
- Bank statement import for auto-matching
- Budget planning and variance analysis
- Mobile app (Android/iOS)
- Security staff portal (tenant verification, visitor management)
- Meeting minutes and circular management
- Complaint/ticket management

---

## 12. Data Model Summary

### 12.1 Units Table
```
unit_id | unit_number | wing | floor | type | area_sqft | monthly_amount | current_owner_id | occupancy_status
--------|-------------|------|-------|------|-----------|----------------|------------------|-----------------
1       | A-101       | A    | 1     | Flat | 650       | 3500           | 101              | Rented
2       | A-102       | A    | 1     | Flat | 650       | 3500           | 102              | Self-Occupied
165     | S-01        | -    | G     | Shop | 200       | 5000           | 201              | Self-Occupied
```

### 12.2 Owners Table
```
owner_id | name       | phone      | email          | aadhar_hash | pan      | purchase_date | status
---------|------------|------------|----------------|-------------|----------|---------------|------------
101      | Rajesh K   | 9876543210 | raj@email.com  | ****5678    | ABCPK1234| 2015-03-01   | Active
102      | Suresh M   | 9876543212 | sur@email.com  | ****9012    | DEFPM5678| 2018-07-15   | Active
50       | Old Owner  | 9876543299 | old@email.com  | ****3456    | GHIPN9012| 2010-01-01   | Transferred
```

### 12.3 Ownership History Table
```
history_id | unit_id | owner_id | start_date | end_date   | transfer_type | document      | status
-----------|---------|----------|------------|------------|---------------|---------------|--------
1          | 1       | 50       | 2010-01-01 | 2015-03-01 | Purchase      | deed_old.pdf  | Transferred
2          | 1       | 101      | 2015-03-01 | NULL       | Purchase      | deed_new.pdf  | Current
```

### 12.4 Tenants Table
```
tenant_id | unit_id | tenant_name  | phone      | aadhar_hash | rent_start | rent_end   | police_status | noc_status | status
----------|---------|--------------|------------|-------------|------------|------------|---------------|------------|-------
1         | 1       | Amit Sharma  | 9898989898 | ****5678    | 2026-01-01 | 2027-01-01 | Verified      | Approved   | Active
2         | 5       | Priya Singh  | 9797979797 | ****1234    | 2026-06-01 | 2027-06-01 | Submitted     | Approved   | Active
```

### 12.5 Vendors Table
```
vendor_id | name          | category     | contact_person | phone      | gst_no      | monthly_amount | contract_end | status
----------|---------------|------------- |----------------|------------|-------------|----------------|--------------|-------
1         | SecureGuard   | Security     | Ramesh         | 9988776655 | 27AABCU9603 | 85000          | 2027-03-31   | Active
2         | CleanSweep    | Housekeeping | Sunil          | 9988776656 | 27AABCU9604 | 45000          | 2026-12-31   | Active
```

### 12.6 Vouchers Table
```
voucher_id | voucher_no   | date       | type    | vendor_id | category     | description          | amount | mode   | ref_no     | status | created_by | created_on
-----------|--------------|------------|---------|-----------|--------------|----------------------|--------|--------|------------|--------|------------|-------------------
1          | PV-2026-001  | 2026-08-01 | Payment | 1         | Security     | Aug security payment | 85000  | NEFT   | NEFT123456 | Final  | Treasurer  | 2026-08-01 10:00
2          | PV-2026-002  | 2026-08-05 | Payment | 2         | Housekeeping | Aug cleaning staff   | 45000  | Cheque | CHQ789012  | Draft  | Treasurer  | 2026-08-05 11:00
```

### 12.7 Voucher Audit Trail Table
```
audit_id | voucher_id | field_changed | old_value | new_value | changed_by | changed_on          | reason
---------|------------|---------------|-----------|-----------|------------|---------------------|---------------------------
1        | 2          | amount        | 43000     | 45000     | Treasurer  | 2026-08-06 09:00:00 | Corrected as per revised bill
2        | 2          | status        | Draft     | Final     | Treasurer  | 2026-08-06 09:05:00 | Verified and finalized
```

### 12.8 Payments Table (Phase 2)
```
payment_id | unit_id | amount | payment_date | mode    | reference_id    | payer_type | payer_name  | recorded_by | status   | receipt_no | voucher_id
-----------|---------|--------|--------------|---------|-----------------|------------|-------------|-------------|----------|------------|----------
1          | 1       | 3500   | 2026-08-01   | UPI_QR  | razorpay_xyz123 | Tenant     | Amit Sharma | SYSTEM      | Verified | RCP-001    | RV-2026-001
2          | 2       | 3500   | 2026-08-02   | CHEQUE  | CHQ-456789      | Owner      | Suresh M    | Admin       | Verified | RCP-002    | RV-2026-002
```

---

## 13. Key Business Rules Summary

### Owner Management
1. **Owner cannot be removed** - No delete operation; only update details or transfer ownership
2. **Owner Master is permanent** - All owners remain in master forever (Active or Transferred status)
3. **One current owner per unit** - Unit can have only one active owner
4. **Dues clearance on transfer** - Warn/block if outstanding balance exists
5. **Document mandatory for transfer** - Sale deed or legal document required
6. **Full ownership chain** - Historical audit viewable for any unit
7. **Owner can own multiple units** - System supports multi-unit owners

### Vendor Management
7. **Active contract required** - Warn if making payment to vendor with expired contract
8. **Contract expiry alerts** - 30, 15, 7 days before expiry
9. **No hard delete** - Deactivate vendors, never delete
10. **Category mandatory** - Every vendor must be categorized

### Rented Flat Management
11. **One active tenant per unit** - Cannot register new until previous vacated
12. **Police verification mandatory** - System flags non-compliant units
13. **NOC before occupancy** - Tenant cannot be active without NOC approval
14. **Owner liability** - Maintenance outstanding always mapped to owner
15. **Agreement validity enforced** - Alert on expiry, block if overdue
16. **Document mandatory** - Registration incomplete without required docs

### Voucher Management
17. **No deletion** - Vouchers cancelled, never deleted (number preserved)
18. **Full audit trail** - Every change logged with user, timestamp, reason
19. **Amount locked after Final** - Cannot change amount once finalized
20. **Sequential numbering** - No gaps, cancelled vouchers keep their number
21. **Financial year boundary** - Vouchers locked after year-end closing
22. **Backdated entry flagged** - If date < creation date, system highlights it

### Maintenance Payment (Phase 2)
23. **Flat number mandatory** - Every payment must be linked to a unit
24. **Duplicate check** - Alert if same unit/month/amount already exists
25. **Auto-receipt** - Generated on every confirmed payment
26. **Auto-voucher** - Receipt voucher created in Voucher module automatically
27. **Payer tracking** - Record whether owner or tenant paid

---

## 14. Dashboard Requirements

### Admin Dashboard (Phase 1)
- Total units: 178 (with occupancy breakdown)
- Owner transfer requests pending
- Rented units count with NOC/verification status
- Vendor contracts expiring soon
- Recent vouchers created/modified
- Pending actions (NOC approvals, document reviews)

### Financial Dashboard (Phase 1)
- Total expenses this month (from vouchers)
- Category-wise expense breakdown
- Vendor payment schedule (upcoming)
- Voucher status summary (Draft / Final / Cancelled)

### Owner Dashboard (Phase 1)
- My unit details
- My tenant details (if rented out)
- NOC status
- Agreement expiry countdown

### Collection Dashboard (Phase 2 - added later)
- Total collection this month vs target
- Number of defaulters
- Payment mode breakdown
- Outstanding balance summary

---

## 15. Success Metrics

| Metric | Target |
|--------|--------|
| Owner data accuracy | 100% units with current owner mapped |
| Ownership history completeness | Every transfer recorded with documents |
| Tenant compliance | 100% rented units with valid police verification & NOC |
| NOC processing time | < 5 working days from submission |
| Voucher accuracy | 100% expenses recorded with proper audit trail |
| Vendor contract coverage | Zero expired contracts for active vendors |
| Online payment adoption (Phase 2) | >60% units paying online within 6 months |
| Payment tracking (Phase 2) | 100% payments recorded with unit mapping |
| Admin time saved | 70% reduction in manual record-keeping |

---

## 16. Assumptions & Constraints

### Assumptions
- Society has a registered bank account
- Committee members have smartphones with internet
- Society has approved budget for software development
- Society bye-laws mandate NOC for rentals
- Police verification is required as per state law
- Historical owner data is available (at least current owners)
- Society has an existing vendor list to import

### Constraints
- Initial data entry effort (178 units + owners + vendors)
- Internet dependency for online operations
- Owner/tenant adoption may need training sessions
- State-specific rental laws may vary
- Tenant data privacy (Aadhar encryption mandatory)
- Payment gateway charges applicable in Phase 2

---

## 17. Glossary

| Term | Meaning |
|------|---------|
| Unit | A flat or shop in the society |
| Owner | Legal owner of a unit (from sale deed/registry) |
| Tenant | Person renting a unit from the owner |
| Transfer | Change of ownership due to sale/inheritance/gift |
| Maintenance | Monthly charges collected from owners |
| Defaulter | Owner who hasn't paid maintenance by due date |
| NOC | No Objection Certificate (society approval for renting) |
| Police Verification | Mandatory background check for tenants |
| Voucher | Documented financial transaction record |
| Audit Trail | Log of all changes made to a record |
| AMC | Annual Maintenance Contract |
| Sinking Fund | Reserve fund for major repairs |
| Corpus Fund | One-time contribution by owners |
| Financial Year | April to March (Indian standard) |
| Leave & License | Formal rental agreement registered with sub-registrar |

---

*Document Version: 4.0*  
*Created: August 10, 2026*  
*Last Updated: August 10, 2026*  
*Changes: Finalized tech stack (Angular + Spring Boot + MySQL), clarified Owner cannot be removed - only update/transfer*  
*Status: Approved for Development*
