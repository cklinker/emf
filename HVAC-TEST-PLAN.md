# Kelta Platform End-to-End Test Plan

## Fictional Customer: **Summit Comfort HVAC, Inc.**

**Test Environment:** https://emf-ui.rzware.com
**Test Approach:** UI-only — no direct API calls or code changes. Every action performed through the browser.
**Goal:** Onboard a new tenant, build a complete HVAC business system, populate it with sample data, configure security, build automation flows, and exercise every major platform capability through realistic business scenarios.

---

## Table of Contents

1. [Tenant Onboarding & Initial Setup](#1-tenant-onboarding--initial-setup)
2. [Identity & Authentication Configuration](#2-identity--authentication-configuration)
3. [Users, Profiles & RBAC](#3-users-profiles--rbac)
4. [Data Model — Collections & Fields](#4-data-model--collections--fields)
5. [Picklists & Reference Data](#5-picklists--reference-data)
6. [Page Layouts & UI Configuration](#6-page-layouts--ui-configuration)
7. [Navigation Menus](#7-navigation-menus)
8. [Sample Data Entry & Record CRUD](#8-sample-data-entry--record-crud)
9. [Relationships & Related Records](#9-relationships--related-records)
10. [List Views & Filtering](#10-list-views--filtering)
11. [Global Search](#11-global-search)
12. [Notes & Attachments](#12-notes--attachments)
13. [Flow Engine — Record-Triggered Flows](#13-flow-engine--record-triggered-flows)
14. [Flow Engine — Scheduled Flows](#14-flow-engine--scheduled-flows)
15. [Flow Engine — Auto-Launched / API Flows](#15-flow-engine--auto-launched--api-flows)
16. [Flow Engine — Advanced Patterns](#16-flow-engine--advanced-patterns)
17. [Email Templates & Notifications](#17-email-templates--notifications)
18. [Approval Processes](#18-approval-processes)
19. [Webhooks & Integration](#19-webhooks--integration)
20. [Field-Level Security & ABAC](#20-field-level-security--abac)
21. [Monitoring & Observability](#21-monitoring--observability)
22. [Audit Trail Verification](#22-audit-trail-verification)
23. [Governor Limits & Rate Limiting](#23-governor-limits--rate-limiting)
24. [Bulk Operations](#24-bulk-operations)
25. [Configuration Packages](#25-configuration-packages)
26. [Negative Testing & Error Handling](#26-negative-testing--error-handling)
27. [Cross-Cutting Concerns](#27-cross-cutting-concerns)
28. [Issue Tracking Template](#28-issue-tracking-template)
29. [Test Completion Checklist](#29-test-completion-checklist)

---

## 1. Tenant Onboarding & Initial Setup

**Objective:** Create the Summit Comfort HVAC tenant and verify the platform bootstraps correctly.

### 1.1 Create the Tenant

1. Log in to the platform at `https://emf-ui.rzware.com` using the **platform administrator** account.
2. Navigate to **Setup** (gear icon or `/setup`).
3. Under the **Platform** category, click **Tenants**.
4. Click the **New Tenant** button (top-right).
5. Fill in the form:
   - **Name:** `Summit Comfort HVAC`
   - **Slug:** `summit-comfort` (this becomes the URL path segment)
   - Leave other fields at defaults unless additional options are shown.
6. Click **Save** / **Create**.
7. **Expected:** The tenant appears in the tenant list with status **Active**.
8. **Expected:** A confirmation toast notification appears.

### 1.2 Verify Tenant Dashboard

1. Navigate to **Tenant Dashboard** (`/tenant-dashboard`).
2. **Expected:** Summit Comfort HVAC appears in the tenant list.
3. **Expected:** Usage metrics show zero or minimal initial values (0 users beyond admin, 0 collections, 0 API calls).

### 1.3 Switch to the New Tenant Context

1. If the platform supports tenant switching in the UI, switch to the `summit-comfort` tenant context.
2. If tenant switching requires logging in with a tenant-specific URL, navigate to `https://emf-ui.rzware.com/summit-comfort` (or whatever the tenant URL pattern is).
3. **Expected:** The UI loads in the Summit Comfort tenant context.
4. **Expected:** The Setup home page shows zero custom collections, default profiles, and default system settings.

### 1.4 Verify Default System State

1. Navigate to **Setup → Collections**. **Expected:** Only system collections exist (e.g., `platform-user`, `role`, `permission`).
2. Navigate to **Setup → Profiles**. **Expected:** 7 default profiles exist (System Administrator, Standard User, Read Only, Marketing User, Contract Manager, Solution Manager, Minimum Access).
3. Navigate to **Setup → Governor Limits**. **Expected:** Default limits are shown (100k API/day, 10GB storage, 100 users, 200 collections).

**🐛 Issue Tracking:** Document any issues with tenant creation, missing defaults, or UI errors.

---

## 2. Identity & Authentication Configuration

**Objective:** Configure authentication settings, password policy, and MFA for the tenant.

### 2.1 Configure Password Policy

1. Navigate to **Setup → Security** (or search for "Password Policy" in the setup search).
2. Locate the **Password Policy** settings.
3. Configure the following:
   - **Minimum Length:** 10
   - **Require Uppercase:** Yes
   - **Require Lowercase:** Yes
   - **Require Number:** Yes
   - **Require Special Character:** Yes
   - **Password History:** 5 (cannot reuse last 5 passwords)
   - **Max Failed Attempts:** 5
   - **Lockout Duration:** 15 minutes
   - **Password Expiration:** 90 days
4. Click **Save**.
5. **Expected:** Success toast. Settings persist on page reload.

### 2.2 Configure MFA Policy

1. Locate the **MFA Policy** settings (may be on the same security page or a sub-section).
2. Configure:
   - **MFA Required:** Yes (for all users)
   - **Allowed Methods:** TOTP, SMS
   - **Grace Period:** 7 days (users must enroll within 7 days)
3. Click **Save**.
4. **Expected:** Success toast. Policy persists on reload.

### 2.3 Configure OIDC Provider (Optional — if testing SSO)

1. Navigate to **Setup → OIDC Providers**.
2. Click **New OIDC Provider**.
3. Fill in a test provider (e.g., a test Keycloak or Auth0 instance):
   - **Provider Name:** `Summit SSO`
   - **Issuer URL:** `https://auth.example.com/realms/summit`
   - **Client ID:** `kelta-summit`
   - **Client Secret:** `test-secret-value`
   - **Scopes:** `openid profile email`
4. Click **Save**.
5. **Expected:** Provider appears in the list. Status shows as configured.
6. *Note: Full SSO testing requires a live IdP. If unavailable, document as deferred.*

### 2.4 Verify Login Page Shows Configured Options

1. Open an incognito/private browser window.
2. Navigate to the tenant login URL.
3. **Expected:** Login form appears with username/password fields.
4. **Expected:** If OIDC was configured, an SSO button appears for "Summit SSO".

**🐛 Issue Tracking:** Document any issues with password policy not saving, MFA configuration errors, or OIDC setup problems.

---

## 3. Users, Profiles & RBAC

**Objective:** Create the user accounts, profiles, and permission structure for Summit Comfort HVAC.

### 3.1 Plan the Organization Structure

Summit Comfort HVAC has these roles:

| Role | Profile | Description |
|------|---------|-------------|
| Admin | System Administrator | Full system access |
| Office Manager | Custom: Office Manager | Manages customers, orders, scheduling |
| Dispatcher | Custom: Dispatcher | Assigns technicians to jobs, manages appointments |
| Technician | Custom: Technician | Views assigned jobs, updates job status, logs parts used |
| Sales Rep | Custom: Sales Rep | Creates quotes, manages customer relationships |
| Warehouse Staff | Custom: Warehouse | Manages inventory, receives shipments |
| Read-Only Auditor | Read Only | Views all data, cannot modify |

### 3.2 Create Custom Profiles

#### 3.2.1 Create "Office Manager" Profile

1. Navigate to **Setup → Profiles**.
2. Click **New Profile** (or **Clone** the "Standard User" profile).
3. Fill in:
   - **Name:** `Office Manager`
   - **Description:** `Manages customers, orders, scheduling, and office operations`
4. Click **Save**.
5. **Expected:** Profile appears in the list.
6. *Note: Object and field permissions will be configured after collections are created (Section 20).*

#### 3.2.2 Create "Dispatcher" Profile

1. Click **New Profile** (or clone "Standard User").
2. **Name:** `Dispatcher`
3. **Description:** `Assigns technicians to jobs, manages appointment scheduling`
4. Click **Save**.

#### 3.2.3 Create "Technician" Profile

1. Click **New Profile** (or clone "Minimum Access").
2. **Name:** `Technician`
3. **Description:** `Field technician — views assigned jobs, updates status, logs parts`
4. Click **Save**.

#### 3.2.4 Create "Sales Rep" Profile

1. Click **New Profile** (or clone "Standard User").
2. **Name:** `Sales Rep`
3. **Description:** `Creates quotes, manages leads and customer relationships`
4. Click **Save**.

#### 3.2.5 Create "Warehouse" Profile

1. Click **New Profile** (or clone "Standard User").
2. **Name:** `Warehouse`
3. **Description:** `Manages inventory, parts, and shipments`
4. Click **Save**.

### 3.3 Create User Accounts

Create the following test users. For each user:

1. Navigate to **Setup → Users**.
2. Click **New User**.
3. Fill in the form fields.
4. Click **Save**.

| # | First Name | Last Name | Email | Username | Profile |
|---|------------|-----------|-------|----------|---------|
| 1 | Sarah | Mitchell | sarah.mitchell@summitcomfort.test | sarah.mitchell | System Administrator |
| 2 | Karen | Lopez | karen.lopez@summitcomfort.test | karen.lopez | Office Manager |
| 3 | Mike | Chen | mike.chen@summitcomfort.test | mike.chen | Dispatcher |
| 4 | Jake | Robinson | jake.robinson@summitcomfort.test | jake.robinson | Technician |
| 5 | Emily | Torres | emily.torres@summitcomfort.test | emily.torres | Technician |
| 6 | David | Park | david.park@summitcomfort.test | david.park | Technician |
| 7 | Rachel | Green | rachel.green@summitcomfort.test | rachel.green | Sales Rep |
| 8 | Tom | Williams | tom.williams@summitcomfort.test | tom.williams | Warehouse |
| 9 | Linda | Auditor | linda.auditor@summitcomfort.test | linda.auditor | Read Only |

**For each user, verify:**
- User appears in the users list with status **ACTIVE** (or PENDING_ACTIVATION).
- Profile assignment is correct.
- Clicking on the user shows their detail page with correct information.

### 3.4 Test User Login

1. Log out of the admin account.
2. Log in as **sarah.mitchell** (admin).
3. **Expected:** Full access to Setup and all admin pages.
4. Log out.
5. Log in as **jake.robinson** (technician).
6. **Expected:** Limited access — cannot see Setup pages that require admin permissions.
7. **Expected:** Can navigate to the app home page.
8. Log out and log back in as the platform admin to continue setup.

### 3.5 Test Account Lockout

1. Open an incognito window.
2. Attempt to log in as **sarah.mitchell** with an **incorrect password** 5 times.
3. **Expected:** After 5 failed attempts, the account is locked.
4. **Expected:** A clear error message indicates the account is locked.
5. As the platform admin, navigate to **Setup → Users → sarah.mitchell**.
6. Click **Unlock** to unlock the account.
7. **Expected:** sarah.mitchell can log in again with the correct password.

### 3.6 Test MFA Enrollment (if MFA was configured)

1. Log in as a user who has not yet enrolled in MFA.
2. **Expected:** The user is prompted to set up MFA (TOTP or SMS).
3. If TOTP: Scan the QR code with an authenticator app (Google Authenticator, Authy, etc.), enter the 6-digit code.
4. **Expected:** MFA enrollment succeeds. User is logged in.
5. Log out and log in again.
6. **Expected:** User is prompted for the MFA code after entering password.
7. Enter the correct code.
8. **Expected:** Login succeeds.
9. Enter an incorrect code 5 times.
10. **Expected:** Rate limiting kicks in — user cannot attempt more verifications for a window.

**🐛 Issue Tracking:** Document any issues with user creation, profile assignment, login failures, lockout behavior, or MFA enrollment.

---

## 4. Data Model — Collections & Fields

**Objective:** Build the complete HVAC data model using the collection and field management UI.

### 4.1 Collections Overview

We will create the following collections:

| # | Collection Name | Display Name | Description |
|---|----------------|--------------|-------------|
| 1 | customers | Customers | Customer accounts (residential & commercial) |
| 2 | contacts | Contacts | Individual contacts at customer accounts |
| 3 | properties | Properties | Service locations / properties |
| 4 | equipment | Equipment | HVAC equipment installed at properties |
| 5 | product-catalog | Product Catalog | Products and parts available for sale/install |
| 6 | inventory | Inventory | Current stock levels by warehouse location |
| 7 | quotes | Quotes | Sales quotes for customers |
| 8 | quote-lines | Quote Lines | Line items on a quote |
| 9 | work-orders | Work Orders | Service/installation work orders |
| 10 | appointments | Appointments | Scheduled service appointments |
| 11 | job-logs | Job Logs | Technician activity logs per appointment |
| 12 | parts-used | Parts Used | Parts consumed during a job |
| 13 | invoices | Invoices | Customer invoices |
| 14 | invoice-lines | Invoice Lines | Line items on an invoice |
| 15 | payments | Payments | Payment records |
| 16 | maintenance-plans | Maintenance Plans | Recurring maintenance contracts |
| 17 | service-areas | Service Areas | Geographic zones for dispatch |
| 18 | technician-skills | Technician Skills | Skills/certifications per technician |

### 4.2 Create Each Collection

For each collection, follow this procedure:

1. Navigate to **Setup → Collections**.
2. Click **New Collection** (or **+ New**).
3. Enter the **Name** (API name), **Display Name**, and **Description** from the table above.
4. Click **Save** / **Create**.
5. **Expected:** The collection appears in the collection list.
6. Click on the newly created collection to open its detail page.

### 4.3 Define Fields for Each Collection

After creating each collection, add the fields listed below. For each field:

1. On the collection detail page, locate the **Fields** section.
2. Click **Add Field** (or **New Field**).
3. Fill in the field properties.
4. Click **Save**.
5. **Expected:** The field appears in the field list with the correct type.

---

#### 4.3.1 Customers Collection

| Field Name | Display Name | Type | Required | Notes |
|------------|-------------|------|----------|-------|
| name | Company Name | string | Yes | Primary display field |
| customer-type | Customer Type | picklist | Yes | Values: Residential, Commercial, Government |
| status | Status | picklist | Yes | Values: Active, Inactive, Prospect, Suspended |
| phone | Phone | string | No | |
| email | Email | string | No | |
| billing-address | Billing Address | string | No | |
| billing-city | Billing City | string | No | |
| billing-state | Billing State | string | No | |
| billing-zip | Billing Zip | string | No | |
| account-balance | Account Balance | number | No | Currency-style field |
| credit-limit | Credit Limit | number | No | |
| payment-terms | Payment Terms | picklist | No | Values: Net 15, Net 30, Net 45, Net 60, Due on Receipt |
| tax-exempt | Tax Exempt | boolean | No | Default: false |
| notes | Notes | string | No | Large text |
| assigned-sales-rep | Assigned Sales Rep | reference | No | References: platform-user |
| source | Lead Source | picklist | No | Values: Website, Referral, Cold Call, Trade Show, Other |

**Verification after all fields are added:**
- Navigate to the collection detail page.
- Count the fields — should be 16 custom fields plus any system fields (id, createdAt, updatedAt).
- Verify each field's type is correct.

---

#### 4.3.2 Contacts Collection

| Field Name | Display Name | Type | Required | Notes |
|------------|-------------|------|----------|-------|
| first-name | First Name | string | Yes | |
| last-name | Last Name | string | Yes | |
| email | Email | string | No | |
| phone | Phone | string | No | |
| mobile | Mobile | string | No | |
| title | Job Title | string | No | |
| customer | Customer | reference | Yes | References: customers |
| is-primary | Primary Contact | boolean | No | Default: false |
| preferred-contact | Preferred Contact Method | picklist | No | Values: Email, Phone, Text, Mail |

---

#### 4.3.3 Properties Collection

| Field Name | Display Name | Type | Required | Notes |
|------------|-------------|------|----------|-------|
| name | Property Name | string | Yes | e.g., "Main Office", "123 Oak St" |
| customer | Customer | reference | Yes | References: customers |
| address | Street Address | string | Yes | |
| city | City | string | Yes | |
| state | State | string | Yes | |
| zip | Zip Code | string | Yes | |
| property-type | Property Type | picklist | Yes | Values: Residential - Single Family, Residential - Multi-Family, Commercial - Office, Commercial - Retail, Commercial - Industrial, Government |
| square-footage | Square Footage | number | No | |
| year-built | Year Built | number | No | |
| access-instructions | Access Instructions | string | No | Gate codes, key locations, etc. |
| service-area | Service Area | reference | No | References: service-areas |

---

#### 4.3.4 Equipment Collection

| Field Name | Display Name | Type | Required | Notes |
|------------|-------------|------|----------|-------|
| name | Equipment Name | string | Yes | |
| property | Property | reference | Yes | References: properties |
| equipment-type | Equipment Type | picklist | Yes | Values: Furnace, Air Conditioner, Heat Pump, Boiler, Ductless Mini-Split, Thermostat, Air Handler, Humidifier, Air Purifier, Ductwork |
| manufacturer | Manufacturer | string | No | |
| model-number | Model Number | string | No | |
| serial-number | Serial Number | string | No | |
| install-date | Install Date | date | No | |
| warranty-expiry | Warranty Expiry Date | date | No | |
| status | Status | picklist | Yes | Values: Operational, Needs Service, Under Repair, Decommissioned |
| last-service-date | Last Service Date | date | No | |
| tonnage | Tonnage / BTU Rating | string | No | |
| energy-rating | Energy Rating (SEER) | number | No | |

---

#### 4.3.5 Product Catalog Collection

| Field Name | Display Name | Type | Required | Notes |
|------------|-------------|------|----------|-------|
| name | Product Name | string | Yes | |
| sku | SKU | string | Yes | Unique product code |
| category | Category | picklist | Yes | Values: HVAC Unit, Part, Filter, Thermostat, Accessory, Refrigerant, Tool, Supply |
| description | Description | string | No | |
| unit-cost | Unit Cost | number | Yes | What we pay |
| unit-price | Unit Price | number | Yes | What we charge |
| manufacturer | Manufacturer | string | No | |
| is-active | Active | boolean | No | Default: true |
| reorder-point | Reorder Point | number | No | Minimum stock before reorder |
| preferred-vendor | Preferred Vendor | string | No | |

---

#### 4.3.6 Inventory Collection

| Field Name | Display Name | Type | Required | Notes |
|------------|-------------|------|----------|-------|
| product | Product | reference | Yes | References: product-catalog |
| warehouse-location | Warehouse Location | picklist | Yes | Values: Main Warehouse, Truck Stock - Jake, Truck Stock - Emily, Truck Stock - David |
| quantity-on-hand | Quantity On Hand | number | Yes | |
| quantity-reserved | Quantity Reserved | number | No | Allocated to work orders |
| last-counted | Last Counted | date | No | |
| bin-location | Bin / Shelf Location | string | No | |

---

#### 4.3.7 Quotes Collection

| Field Name | Display Name | Type | Required | Notes |
|------------|-------------|------|----------|-------|
| name | Quote Number | string | Yes | e.g., Q-2026-001 |
| customer | Customer | reference | Yes | References: customers |
| property | Property | reference | No | References: properties |
| status | Status | picklist | Yes | Values: Draft, Sent, Accepted, Rejected, Expired |
| quote-date | Quote Date | date | Yes | |
| expiration-date | Expiration Date | date | No | |
| subtotal | Subtotal | number | No | |
| tax-rate | Tax Rate | number | No | |
| tax-amount | Tax Amount | number | No | |
| total | Total | number | No | |
| sales-rep | Sales Rep | reference | No | References: platform-user |
| notes | Notes | string | No | |
| terms | Terms & Conditions | string | No | |

---

#### 4.3.8 Quote Lines Collection

| Field Name | Display Name | Type | Required | Notes |
|------------|-------------|------|----------|-------|
| quote | Quote | reference | Yes | References: quotes |
| product | Product | reference | No | References: product-catalog |
| description | Description | string | Yes | |
| quantity | Quantity | number | Yes | |
| unit-price | Unit Price | number | Yes | |
| line-total | Line Total | number | No | Calculated: quantity × unit-price |
| line-type | Line Type | picklist | Yes | Values: Product, Labor, Service Fee, Discount |

---

#### 4.3.9 Work Orders Collection

| Field Name | Display Name | Type | Required | Notes |
|------------|-------------|------|----------|-------|
| name | Work Order Number | string | Yes | e.g., WO-2026-001 |
| customer | Customer | reference | Yes | References: customers |
| property | Property | reference | Yes | References: properties |
| equipment | Equipment | reference | No | References: equipment |
| quote | Quote | reference | No | References: quotes (if originated from a quote) |
| work-type | Work Type | picklist | Yes | Values: Installation, Repair, Maintenance, Inspection, Emergency, Warranty |
| priority | Priority | picklist | Yes | Values: Low, Medium, High, Emergency |
| status | Status | picklist | Yes | Values: New, Scheduled, In Progress, On Hold, Completed, Cancelled, Invoiced |
| description | Description | string | Yes | |
| requested-date | Requested Date | date | No | |
| scheduled-date | Scheduled Date | date | No | |
| completed-date | Completed Date | date | No | |
| assigned-technician | Assigned Technician | reference | No | References: platform-user |
| estimated-hours | Estimated Hours | number | No | |
| actual-hours | Actual Hours | number | No | |
| parts-total | Parts Total | number | No | |
| labor-total | Labor Total | number | No | |
| total-cost | Total Cost | number | No | |

---

#### 4.3.10 Appointments Collection

| Field Name | Display Name | Type | Required | Notes |
|------------|-------------|------|----------|-------|
| name | Appointment ID | string | Yes | e.g., APT-2026-001 |
| work-order | Work Order | reference | Yes | References: work-orders |
| technician | Technician | reference | Yes | References: platform-user |
| appointment-date | Appointment Date | date | Yes | |
| start-time | Start Time | string | Yes | e.g., "09:00" |
| end-time | End Time | string | No | e.g., "11:00" |
| status | Status | picklist | Yes | Values: Scheduled, En Route, On Site, Completed, No Show, Rescheduled, Cancelled |
| customer-notified | Customer Notified | boolean | No | Default: false |
| arrival-time | Actual Arrival Time | string | No | |
| departure-time | Actual Departure Time | string | No | |
| notes | Dispatch Notes | string | No | |

---

#### 4.3.11 Job Logs Collection

| Field Name | Display Name | Type | Required | Notes |
|------------|-------------|------|----------|-------|
| appointment | Appointment | reference | Yes | References: appointments |
| technician | Technician | reference | Yes | References: platform-user |
| log-type | Log Type | picklist | Yes | Values: Diagnosis, Repair, Installation, Note, Photo, Customer Signature |
| description | Description | string | Yes | |
| hours-logged | Hours Logged | number | No | |
| timestamp | Timestamp | datetime | No | |

---

#### 4.3.12 Parts Used Collection

| Field Name | Display Name | Type | Required | Notes |
|------------|-------------|------|----------|-------|
| work-order | Work Order | reference | Yes | References: work-orders |
| appointment | Appointment | reference | No | References: appointments |
| product | Product | reference | Yes | References: product-catalog |
| quantity | Quantity | number | Yes | |
| unit-cost | Unit Cost | number | No | Snapshot from product catalog |
| unit-price | Unit Price | number | No | Price charged to customer |
| line-total | Line Total | number | No | |
| from-warehouse | From Warehouse | picklist | No | Values: Main Warehouse, Truck Stock - Jake, Truck Stock - Emily, Truck Stock - David |
| technician | Technician | reference | No | References: platform-user |

---

#### 4.3.13 Invoices Collection

| Field Name | Display Name | Type | Required | Notes |
|------------|-------------|------|----------|-------|
| name | Invoice Number | string | Yes | e.g., INV-2026-001 |
| customer | Customer | reference | Yes | References: customers |
| work-order | Work Order | reference | No | References: work-orders |
| quote | Quote | reference | No | References: quotes |
| status | Status | picklist | Yes | Values: Draft, Sent, Partially Paid, Paid, Overdue, Void, Write-Off |
| invoice-date | Invoice Date | date | Yes | |
| due-date | Due Date | date | Yes | |
| subtotal | Subtotal | number | No | |
| tax-rate | Tax Rate | number | No | |
| tax-amount | Tax Amount | number | No | |
| total | Total | number | No | |
| amount-paid | Amount Paid | number | No | |
| balance-due | Balance Due | number | No | |
| notes | Notes | string | No | |

---

#### 4.3.14 Invoice Lines Collection

| Field Name | Display Name | Type | Required | Notes |
|------------|-------------|------|----------|-------|
| invoice | Invoice | reference | Yes | References: invoices |
| description | Description | string | Yes | |
| quantity | Quantity | number | Yes | |
| unit-price | Unit Price | number | Yes | |
| line-total | Line Total | number | No | |
| line-type | Line Type | picklist | Yes | Values: Parts, Labor, Service Fee, Discount, Tax |

---

#### 4.3.15 Payments Collection

| Field Name | Display Name | Type | Required | Notes |
|------------|-------------|------|----------|-------|
| invoice | Invoice | reference | Yes | References: invoices |
| customer | Customer | reference | Yes | References: customers |
| payment-date | Payment Date | date | Yes | |
| amount | Amount | number | Yes | |
| payment-method | Payment Method | picklist | Yes | Values: Credit Card, Check, ACH, Cash, Financing |
| reference-number | Reference / Check Number | string | No | |
| status | Status | picklist | Yes | Values: Pending, Completed, Failed, Refunded |
| notes | Notes | string | No | |

---

#### 4.3.16 Maintenance Plans Collection

| Field Name | Display Name | Type | Required | Notes |
|------------|-------------|------|----------|-------|
| name | Plan Name | string | Yes | e.g., "Annual Comfort Plan" |
| customer | Customer | reference | Yes | References: customers |
| property | Property | reference | Yes | References: properties |
| plan-type | Plan Type | picklist | Yes | Values: Basic, Standard, Premium |
| status | Status | picklist | Yes | Values: Active, Expired, Cancelled, Pending Renewal |
| start-date | Start Date | date | Yes | |
| end-date | End Date | date | Yes | |
| annual-cost | Annual Cost | number | Yes | |
| visits-per-year | Visits Per Year | number | Yes | |
| visits-completed | Visits Completed | number | No | |
| includes-parts | Includes Parts | boolean | No | |
| includes-priority | Includes Priority Scheduling | boolean | No | |
| discount-percentage | Labor Discount % | number | No | |
| next-visit-date | Next Visit Date | date | No | |

---

#### 4.3.17 Service Areas Collection

| Field Name | Display Name | Type | Required | Notes |
|------------|-------------|------|----------|-------|
| name | Area Name | string | Yes | e.g., "North Metro" |
| zip-codes | Zip Codes | string | No | Comma-separated list |
| primary-technician | Primary Technician | reference | No | References: platform-user |
| backup-technician | Backup Technician | reference | No | References: platform-user |
| drive-time-estimate | Avg Drive Time (min) | number | No | |

---

#### 4.3.18 Technician Skills Collection

| Field Name | Display Name | Type | Required | Notes |
|------------|-------------|------|----------|-------|
| technician | Technician | reference | Yes | References: platform-user |
| skill-name | Skill / Certification | picklist | Yes | Values: EPA 608 Universal, NATE Certified, Electrical License, Gas License, Boiler Certified, Ductless Install, Commercial HVAC, Residential HVAC, Controls/BAS |
| certification-number | Certification Number | string | No | |
| expiration-date | Expiration Date | date | No | |
| status | Status | picklist | Yes | Values: Active, Expired, Pending Renewal |

---

### 4.4 Verify All Collections

1. Navigate to **Setup → Collections**.
2. **Expected:** All 18 custom collections are listed.
3. Click into each collection and verify the field count and types match the specifications above.
4. **Expected:** Reference fields show the correct target collection.

**🐛 Issue Tracking:** Document any issues with collection creation, field type options, reference field configuration, or validation.

---

## 5. Picklists & Reference Data

**Objective:** Create global picklists and verify they render correctly in forms.

### 5.1 Create Global Picklists

Many of the picklist fields above can use global picklists for reuse. Create these global picklists:

1. Navigate to **Setup → Picklists**.
2. For each picklist below, click **New Picklist**, enter the name and values, then save.

| Picklist Name | Values | Sorted? | Restricted? |
|---------------|--------|---------|-------------|
| Customer Type | Residential, Commercial, Government | No | Yes |
| Customer Status | Active, Inactive, Prospect, Suspended | No | Yes |
| Payment Terms | Due on Receipt, Net 15, Net 30, Net 45, Net 60 | No | Yes |
| Lead Source | Website, Referral, Cold Call, Trade Show, Other | No | No |
| Property Type | Residential - Single Family, Residential - Multi-Family, Commercial - Office, Commercial - Retail, Commercial - Industrial, Government | No | Yes |
| Equipment Type | Furnace, Air Conditioner, Heat Pump, Boiler, Ductless Mini-Split, Thermostat, Air Handler, Humidifier, Air Purifier, Ductwork | Yes | No |
| Equipment Status | Operational, Needs Service, Under Repair, Decommissioned | No | Yes |
| Product Category | HVAC Unit, Part, Filter, Thermostat, Accessory, Refrigerant, Tool, Supply | Yes | No |
| Work Order Type | Installation, Repair, Maintenance, Inspection, Emergency, Warranty | No | Yes |
| Priority | Low, Medium, High, Emergency | No | Yes |
| Work Order Status | New, Scheduled, In Progress, On Hold, Completed, Cancelled, Invoiced | No | Yes |
| Appointment Status | Scheduled, En Route, On Site, Completed, No Show, Rescheduled, Cancelled | No | Yes |
| Invoice Status | Draft, Sent, Partially Paid, Paid, Overdue, Void, Write-Off | No | Yes |
| Payment Method | Credit Card, Check, ACH, Cash, Financing | No | Yes |
| Payment Status | Pending, Completed, Failed, Refunded | No | Yes |
| Quote Status | Draft, Sent, Accepted, Rejected, Expired | No | Yes |
| Maintenance Plan Type | Basic, Standard, Premium | No | Yes |
| Maintenance Plan Status | Active, Expired, Cancelled, Pending Renewal | No | Yes |
| Warehouse Location | Main Warehouse, Truck Stock - Jake, Truck Stock - Emily, Truck Stock - David | No | Yes |
| Contact Method | Email, Phone, Text, Mail | No | No |
| Skill / Certification | EPA 608 Universal, NATE Certified, Electrical License, Gas License, Boiler Certified, Ductless Install, Commercial HVAC, Residential HVAC, Controls/BAS | Yes | No |
| Job Log Type | Diagnosis, Repair, Installation, Note, Photo, Customer Signature | No | Yes |
| Line Type | Product, Labor, Service Fee, Discount | No | Yes |
| Invoice Line Type | Parts, Labor, Service Fee, Discount, Tax | No | Yes |
| Certification Status | Active, Expired, Pending Renewal | No | Yes |

### 5.2 Verify Picklists in Forms

1. Navigate to the app view and create a new **Customer** record.
2. Click the **Customer Type** dropdown.
3. **Expected:** The picklist values (Residential, Commercial, Government) appear.
4. **Expected:** Values appear in the defined order (not alphabetical, since Sorted = No).
5. Repeat spot-checks for 2–3 other picklist fields on different collections.
6. Cancel the record creation (no need to save yet).

**🐛 Issue Tracking:** Document any issues with picklist creation, value ordering, or rendering in forms.

---

## 6. Page Layouts & UI Configuration

**Objective:** Configure page layouts so that record detail and edit views show fields in a logical, user-friendly arrangement.

### 6.1 Create a Customer Detail Layout

1. Navigate to **Setup → Page Layouts**.
2. Click **New Layout**.
3. Select the **Customers** collection.
4. **Name:** `Customer Detail Layout`
5. In the layout editor, organize fields into sections:

**Section 1: Customer Information** (2 columns)
- Column 1: Company Name, Customer Type, Status
- Column 2: Phone, Email, Lead Source

**Section 2: Billing** (2 columns)
- Column 1: Billing Address, Billing City
- Column 2: Billing State, Billing Zip

**Section 3: Financial** (2 columns)
- Column 1: Account Balance, Credit Limit
- Column 2: Payment Terms, Tax Exempt

**Section 4: Details** (1 column)
- Assigned Sales Rep, Notes

**Related Lists Section:**
- Contacts
- Properties
- Work Orders
- Quotes
- Invoices
- Maintenance Plans

6. Click **Save**.
7. Set as default layout for the Customers collection.
8. **Expected:** When viewing a customer record, fields appear in the configured sections.

### 6.2 Create Work Order Detail Layout

1. Create a new layout for **Work Orders**.
2. **Name:** `Work Order Detail Layout`
3. Organize:

**Section 1: Work Order Info** (2 columns)
- Column 1: Work Order Number, Work Type, Priority, Status
- Column 2: Customer, Property, Equipment, Quote

**Section 2: Schedule** (2 columns)
- Column 1: Requested Date, Scheduled Date
- Column 2: Assigned Technician, Estimated Hours

**Section 3: Completion** (2 columns)
- Column 1: Completed Date, Actual Hours
- Column 2: Parts Total, Labor Total, Total Cost

**Section 4: Description** (1 column)
- Description

**Related Lists:**
- Appointments
- Parts Used
- Job Logs

4. Click **Save** and set as default.

### 6.3 Create Layouts for Remaining Key Collections

Repeat the layout creation process for at least these collections:
- **Appointments** — show work order, technician, schedule, status
- **Invoices** — show customer, work order, amounts, status
- **Quotes** — show customer, property, amounts, status
- **Equipment** — show property, type, manufacturer, warranty info

*For collections not explicitly laid out, the default auto-generated layout is acceptable.*

**🐛 Issue Tracking:** Document any issues with the layout editor (drag-drop, section creation, field placement, related lists).

---

## 7. Navigation Menus

**Objective:** Build a navigation menu that organizes the HVAC application logically.

### 7.1 Create the Main Navigation Menu

1. Navigate to **Setup → Menus**.
2. Click **New Menu**.
3. **Name:** `Summit Comfort Main Menu`
4. Add menu items in this order:

| Label | Path/Target | Icon (if supported) |
|-------|------------|---------------------|
| Home | /app/home | Home |
| **Customers** | | |
| → Customers | /app/o/customers | Building |
| → Contacts | /app/o/contacts | Users |
| → Properties | /app/o/properties | Map Pin |
| **Service** | | |
| → Work Orders | /app/o/work-orders | Wrench |
| → Appointments | /app/o/appointments | Calendar |
| → Equipment | /app/o/equipment | Cog |
| **Sales** | | |
| → Quotes | /app/o/quotes | FileText |
| → Product Catalog | /app/o/product-catalog | Package |
| **Inventory** | | |
| → Inventory | /app/o/inventory | Warehouse |
| → Parts Used | /app/o/parts-used | Tool |
| **Finance** | | |
| → Invoices | /app/o/invoices | DollarSign |
| → Payments | /app/o/payments | CreditCard |
| **Admin** | | |
| → Maintenance Plans | /app/o/maintenance-plans | Shield |
| → Service Areas | /app/o/service-areas | MapIcon |
| → Technician Skills | /app/o/technician-skills | Award |

5. Save the menu.
6. Navigate to the app home (`/app/home`).
7. **Expected:** The left-side navigation shows the menu with correct grouping and links.
8. Click each menu item.
9. **Expected:** Each link navigates to the correct collection list view.

**🐛 Issue Tracking:** Document any issues with menu creation, ordering, nesting, or navigation behavior.

---

## 8. Sample Data Entry & Record CRUD

**Objective:** Populate the system with realistic sample data and verify create, read, update, and delete operations work correctly.

### 8.1 Create Service Areas

Navigate to **Service Areas** and create:

| Area Name | Zip Codes | Primary Tech | Backup Tech | Avg Drive Time |
|-----------|-----------|-------------|-------------|----------------|
| North Metro | 55401, 55402, 55403, 55404 | Jake Robinson | Emily Torres | 20 |
| South Metro | 55410, 55411, 55412, 55413 | Emily Torres | David Park | 25 |
| West Suburbs | 55305, 55316, 55317, 55318 | David Park | Jake Robinson | 30 |
| East Suburbs | 55106, 55109, 55110, 55119 | Jake Robinson | David Park | 25 |

### 8.2 Create Technician Skills

Navigate to **Technician Skills** and create:

| Technician | Skill | Cert Number | Expiration | Status |
|-----------|-------|-------------|------------|--------|
| Jake Robinson | EPA 608 Universal | EPA-2024-JR-001 | 2027-06-15 | Active |
| Jake Robinson | NATE Certified | NATE-JR-5521 | 2026-12-01 | Active |
| Jake Robinson | Residential HVAC | RES-JR-441 | 2027-03-01 | Active |
| Jake Robinson | Gas License | GAS-JR-882 | 2026-08-15 | Active |
| Emily Torres | EPA 608 Universal | EPA-2024-ET-002 | 2027-06-15 | Active |
| Emily Torres | NATE Certified | NATE-ET-5522 | 2026-11-01 | Active |
| Emily Torres | Commercial HVAC | COM-ET-220 | 2027-01-15 | Active |
| Emily Torres | Boiler Certified | BLR-ET-110 | 2026-09-30 | Active |
| David Park | EPA 608 Universal | EPA-2024-DP-003 | 2027-06-15 | Active |
| David Park | Ductless Install | DLS-DP-055 | 2027-04-01 | Active |
| David Park | Electrical License | ELEC-DP-7731 | 2026-10-15 | Active |
| David Park | Controls/BAS | BAS-DP-220 | 2027-02-28 | Active |

### 8.3 Create Customers

Navigate to **Customers** and create:

| Company Name | Type | Status | Phone | Email | Payment Terms |
|-------------|------|--------|-------|-------|---------------|
| Johnson Family | Residential | Active | 555-0101 | johnson@email.test | Due on Receipt |
| Martinez Residence | Residential | Active | 555-0102 | martinez@email.test | Due on Receipt |
| Oakwood Office Park | Commercial | Active | 555-0201 | facilities@oakwood.test | Net 30 |
| Fresh Start Bakery | Commercial | Active | 555-0202 | info@freshstart.test | Net 15 |
| City Hall Complex | Government | Active | 555-0301 | maintenance@cityhall.test | Net 45 |
| Thompson House | Residential | Prospect | 555-0103 | thompson@email.test | Due on Receipt |
| Riverside Apartments | Commercial | Active | 555-0203 | manager@riverside.test | Net 30 |
| Lakeside Medical Center | Commercial | Active | 555-0204 | ops@lakesidemedical.test | Net 30 |

### 8.4 Create Contacts

For each customer, create at least one contact:

| First Name | Last Name | Email | Phone | Customer | Primary? |
|-----------|-----------|-------|-------|----------|----------|
| Mark | Johnson | mark.johnson@email.test | 555-0101 | Johnson Family | Yes |
| Lisa | Johnson | lisa.johnson@email.test | 555-0111 | Johnson Family | No |
| Carlos | Martinez | carlos.martinez@email.test | 555-0102 | Martinez Residence | Yes |
| Jim | Fletcher | jim.fletcher@oakwood.test | 555-0201 | Oakwood Office Park | Yes |
| Susan | Blake | susan.blake@oakwood.test | 555-0211 | Oakwood Office Park | No |
| Anna | Nguyen | anna@freshstart.test | 555-0202 | Fresh Start Bakery | Yes |
| Robert | Graves | robert.graves@cityhall.test | 555-0301 | City Hall Complex | Yes |
| Nancy | Thompson | nancy@email.test | 555-0103 | Thompson House | Yes |
| Dan | Wells | dan.wells@riverside.test | 555-0203 | Riverside Apartments | Yes |
| Dr. Karen | Patel | karen.patel@lakesidemedical.test | 555-0204 | Lakeside Medical Center | Yes |

### 8.5 Create Properties

| Property Name | Customer | Address | City | State | Zip | Type | Sq Ft | Service Area |
|--------------|---------|---------|------|-------|-----|------|-------|-------------|
| Johnson Home | Johnson Family | 123 Elm Street | Minneapolis | MN | 55401 | Residential - Single Family | 2200 | North Metro |
| Martinez Home | Martinez Residence | 456 Oak Avenue | Minneapolis | MN | 55410 | Residential - Single Family | 1800 | South Metro |
| Oakwood Building A | Oakwood Office Park | 789 Commerce Blvd | Minnetonka | MN | 55305 | Commercial - Office | 45000 | West Suburbs |
| Oakwood Building B | Oakwood Office Park | 791 Commerce Blvd | Minnetonka | MN | 55305 | Commercial - Office | 38000 | West Suburbs |
| Fresh Start Bakery | Fresh Start Bakery | 321 Main Street | St. Paul | MN | 55106 | Commercial - Retail | 3500 | East Suburbs |
| City Hall Main | City Hall Complex | 100 Government Way | Minneapolis | MN | 55401 | Government | 85000 | North Metro |
| Riverside Units 1-10 | Riverside Apartments | 500 River Road | Minneapolis | MN | 55411 | Residential - Multi-Family | 12000 | South Metro |
| Lakeside Main Building | Lakeside Medical Center | 200 Health Parkway | Woodbury | MN | 55110 | Commercial - Office | 55000 | East Suburbs |
| Thompson House | Thompson House | 789 Pine Lane | Plymouth | MN | 55317 | Residential - Single Family | 2800 | West Suburbs |

### 8.6 Create Equipment

| Name | Property | Type | Manufacturer | Model | Install Date | Warranty Expiry | Status |
|------|----------|------|-------------|-------|-------------|-----------------|--------|
| Main Furnace | Johnson Home | Furnace | Carrier | 59TP6 | 2021-10-15 | 2031-10-15 | Operational |
| Central AC | Johnson Home | Air Conditioner | Carrier | 24ACC6 | 2021-10-15 | 2031-10-15 | Operational |
| Thermostat | Johnson Home | Thermostat | Ecobee | SmartThermostat | 2022-03-01 | 2025-03-01 | Operational |
| Furnace | Martinez Home | Furnace | Lennox | SL280V | 2018-11-20 | 2028-11-20 | Needs Service |
| Heat Pump | Martinez Home | Heat Pump | Lennox | XP25 | 2018-11-20 | 2028-11-20 | Operational |
| RTU-A1 | Oakwood Building A | Air Handler | Trane | XR15 | 2019-06-01 | 2029-06-01 | Operational |
| RTU-A2 | Oakwood Building A | Air Handler | Trane | XR15 | 2019-06-01 | 2029-06-01 | Operational |
| Boiler-A | Oakwood Building A | Boiler | Weil-McLain | GV90+ | 2017-09-15 | 2027-09-15 | Operational |
| Walk-in Cooler Compressor | Fresh Start Bakery | Air Conditioner | Bard | W24AC | 2020-04-10 | 2025-04-10 | Needs Service |
| HVAC Main | City Hall Main | Air Handler | York | YC2F | 2016-03-01 | 2026-03-01 | Operational |
| Chiller | City Hall Main | Air Conditioner | York | YLAA | 2016-03-01 | 2026-03-01 | Under Repair |
| Mini-Splits (x10) | Riverside Units 1-10 | Ductless Mini-Split | Mitsubishi | MSZ-GL09 | 2022-08-01 | 2032-08-01 | Operational |

### 8.7 Create Product Catalog Items

| Product Name | SKU | Category | Unit Cost | Unit Price | Reorder Point |
|-------------|-----|----------|-----------|------------|---------------|
| Carrier 59TP6 Furnace | CARRIER-59TP6 | HVAC Unit | 2400.00 | 3600.00 | 2 |
| Carrier 24ACC6 AC Unit | CARRIER-24ACC6 | HVAC Unit | 3200.00 | 4800.00 | 2 |
| Lennox SL280V Furnace | LENNOX-SL280V | HVAC Unit | 2100.00 | 3200.00 | 1 |
| Honeywell T6 Thermostat | HW-T6-PRO | Thermostat | 85.00 | 175.00 | 10 |
| Ecobee SmartThermostat | ECO-SMART-5 | Thermostat | 180.00 | 299.00 | 5 |
| 16x25x1 Air Filter (6pk) | FILT-16251-6 | Filter | 18.00 | 35.99 | 50 |
| 20x25x1 Air Filter (6pk) | FILT-20251-6 | Filter | 20.00 | 39.99 | 50 |
| R-410A Refrigerant (25lb) | REF-410A-25 | Refrigerant | 125.00 | 225.00 | 10 |
| Capacitor 45/5 MFD | CAP-455-370 | Part | 12.00 | 45.00 | 20 |
| Contactor 2-Pole 30A | CONT-2P-30A | Part | 15.00 | 55.00 | 15 |
| Blower Motor 1/2 HP | MOTOR-BLW-05 | Part | 95.00 | 225.00 | 5 |
| Igniter - Hot Surface | IGN-HSI-UNIV | Part | 22.00 | 85.00 | 10 |
| Condensate Pump | PUMP-COND-01 | Part | 35.00 | 95.00 | 5 |
| Copper Tubing 50ft 3/8 | TUBE-CU-3850 | Supply | 65.00 | 95.00 | 10 |
| Brazing Alloy Kit | BRAZE-KIT-01 | Supply | 28.00 | 28.00 | 5 |

### 8.8 Create Inventory Records

| Product | Warehouse | Qty On Hand | Qty Reserved |
|---------|-----------|------------|--------------|
| Honeywell T6 Thermostat | Main Warehouse | 15 | 2 |
| Ecobee SmartThermostat | Main Warehouse | 8 | 0 |
| 16x25x1 Air Filter (6pk) | Main Warehouse | 120 | 0 |
| 20x25x1 Air Filter (6pk) | Main Warehouse | 95 | 0 |
| R-410A Refrigerant (25lb) | Main Warehouse | 18 | 3 |
| Capacitor 45/5 MFD | Main Warehouse | 32 | 0 |
| Contactor 2-Pole 30A | Main Warehouse | 20 | 0 |
| Blower Motor 1/2 HP | Main Warehouse | 8 | 1 |
| Igniter - Hot Surface | Main Warehouse | 14 | 0 |
| Capacitor 45/5 MFD | Truck Stock - Jake | 3 | 0 |
| Contactor 2-Pole 30A | Truck Stock - Jake | 2 | 0 |
| Igniter - Hot Surface | Truck Stock - Jake | 2 | 0 |
| 16x25x1 Air Filter (6pk) | Truck Stock - Jake | 5 | 0 |
| R-410A Refrigerant (25lb) | Truck Stock - Emily | 2 | 0 |
| Capacitor 45/5 MFD | Truck Stock - Emily | 3 | 0 |
| Blower Motor 1/2 HP | Truck Stock - Emily | 1 | 0 |

### 8.9 Test CRUD Operations

For each type of operation, pick one collection and execute thoroughly:

#### 8.9.1 Create (already done above — verify all records exist)
1. Navigate to each collection list view.
2. **Expected:** Record count matches the data entered above.

#### 8.9.2 Read
1. Click on any customer record (e.g., "Oakwood Office Park").
2. **Expected:** Detail page loads with all field values displayed correctly.
3. **Expected:** Related lists show contacts, properties, work orders, etc.
4. Verify reference fields display the related record's name (not just the ID).

#### 8.9.3 Update
1. Open the "Martinez Residence" customer record.
2. Click **Edit**.
3. Change **Phone** from `555-0102` to `555-0122`.
4. Change **Status** from `Active` to `Inactive`.
5. Click **Save**.
6. **Expected:** Record updates. Detail page shows new values.
7. **Expected:** `updatedAt` timestamp is refreshed.

#### 8.9.4 Delete
1. Navigate to the **Contacts** list.
2. Find a non-primary contact (e.g., "Lisa Johnson").
3. Click the contact to open it, then click **Delete** (or use the row action menu).
4. **Expected:** A confirmation dialog appears.
5. Confirm the deletion.
6. **Expected:** The contact is removed from the list.
7. Navigate to the Johnson Family customer record.
8. **Expected:** The contacts related list no longer shows Lisa Johnson.
9. *Re-create the contact afterward so data is complete for later tests.*

**🐛 Issue Tracking:** Document any issues with record creation, rendering, updates, deletions, or related record display.

---

## 9. Relationships & Related Records

**Objective:** Verify that reference fields and related records work correctly across the data model.

### 9.1 Verify Customer → Contacts Relationship

1. Open the **Johnson Family** customer record.
2. Scroll to the **Contacts** related list.
3. **Expected:** Mark Johnson (and Lisa Johnson if re-created) appear.
4. Click on Mark Johnson from the related list.
5. **Expected:** The contact detail page opens. The **Customer** field shows "Johnson Family" as a clickable link.
6. Click "Johnson Family" in the Customer field.
7. **Expected:** Navigates back to the Johnson Family customer record.

### 9.2 Verify Customer → Properties → Equipment Chain

1. Open the **Oakwood Office Park** customer.
2. **Expected:** Properties related list shows "Oakwood Building A" and "Oakwood Building B".
3. Click on "Oakwood Building A".
4. **Expected:** Property detail opens. **Equipment** related list shows RTU-A1, RTU-A2, Boiler-A.
5. Click on "RTU-A1".
6. **Expected:** Equipment detail opens with Property field showing "Oakwood Building A".

### 9.3 Verify Work Order → Appointments → Job Logs Chain

*(This will be tested after work orders and appointments are created in Section 8.10)*

### 9.4 Verify Invoice → Payments Relationship

*(This will be tested after invoices are created in Section 13)*

**🐛 Issue Tracking:** Document any issues with reference field display, related list rendering, or navigation between related records.

---

## 10. List Views & Filtering

**Objective:** Create custom list views and verify filtering, sorting, and column selection.

### 10.1 Create "Active Customers" List View

1. Navigate to **Setup → List Views** (or use the list view selector on the Customers list page).
2. Click **New List View**.
3. **Name:** `Active Customers`
4. **Collection:** customers
5. **Columns:** Company Name, Customer Type, Phone, Email, Payment Terms
6. **Filter:** status equals "Active"
7. **Sort:** Company Name ascending
8. Click **Save**.
9. Navigate to the Customers collection and select the "Active Customers" list view.
10. **Expected:** Only customers with status "Active" appear (should exclude Thompson House — Prospect, and Martinez Residence — Inactive).
11. **Expected:** Columns show the configured fields in order.

### 10.2 Create "Emergency Work Orders" List View

1. Create a new list view for **Work Orders**.
2. **Name:** `Emergency & High Priority`
3. **Filter:** priority IN ("High", "Emergency")
4. **Columns:** Work Order Number, Customer, Work Type, Priority, Status, Assigned Technician, Scheduled Date
5. **Sort:** Priority descending (Emergency first)
6. Click **Save**.

### 10.3 Create "Equipment Needing Service" List View

1. Create a new list view for **Equipment**.
2. **Name:** `Needs Service`
3. **Filter:** status equals "Needs Service" OR status equals "Under Repair"
4. **Columns:** Equipment Name, Property, Equipment Type, Manufacturer, Status, Last Service Date
5. Click **Save**.
6. **Expected:** Shows Martinez Furnace (Needs Service), Walk-in Cooler Compressor (Needs Service), and City Hall Chiller (Under Repair).

### 10.4 Test Sorting on List Views

1. Open any collection list view (e.g., Customers).
2. Click the **Company Name** column header.
3. **Expected:** Records sort alphabetically A→Z.
4. Click again.
5. **Expected:** Records sort Z→A.
6. Click the **createdAt** column header (if visible).
7. **Expected:** Records sort by creation date.

### 10.5 Test Pagination

1. Navigate to a collection with enough records (Product Catalog has 15).
2. Set page size to 5 (if configurable).
3. **Expected:** Only 5 records shown per page with page navigation controls.
4. Click **Next Page**.
5. **Expected:** Next set of records loads.

**🐛 Issue Tracking:** Document any issues with list view creation, filters not working, sort behavior, or pagination.

---

## 11. Global Search

**Objective:** Verify full-text search works across collections.

### 11.1 Search for a Customer by Name

1. Navigate to the global search (`/app/search` or use the search bar).
2. Type `Oakwood` and press Enter.
3. **Expected:** Results include the "Oakwood Office Park" customer record.
4. **Expected:** Results may also include "Oakwood Building A" and "Oakwood Building B" properties.

### 11.2 Search for Equipment by Model

1. Search for `XR15`.
2. **Expected:** Trane XR15 equipment records appear in results.

### 11.3 Search for a Product by SKU

1. Search for `CARRIER-59TP6`.
2. **Expected:** The Carrier furnace product appears.

### 11.4 Search Across Multiple Collections

1. Search for `Johnson`.
2. **Expected:** Results include the Johnson Family customer AND Mark Johnson contact (and Lisa Johnson if re-created).

### 11.5 Verify Search Reindexing

1. Create a new customer: **Name:** `ZZZ Test Search Customer`, **Type:** Residential, **Status:** Active.
2. Search for `ZZZ Test Search`.
3. **Expected:** The new customer appears in search results (may take a moment for indexing).
4. If not found immediately, wait 30 seconds and search again.
5. Delete the test customer afterward.

**🐛 Issue Tracking:** Document any issues with search not returning expected results, slow indexing, or result relevance.

---

## 12. Notes & Attachments

**Objective:** Verify notes and file attachments work on records.

### 12.1 Add a Note to a Customer Record

1. Open the **Oakwood Office Park** customer record.
2. Scroll to the **Notes** section (or Activity section).
3. Click **New Note** (or the add note button).
4. Enter: `Spoke with Jim Fletcher about upgrading Building B HVAC system. He's interested in getting a quote for new RTUs. Follow up next week.`
5. Click **Save** / **Post**.
6. **Expected:** The note appears in the notes section with the current user's name and timestamp.

### 12.2 Add Another Note

1. Add a second note: `Jim confirmed budget approval for Building B project. Proceed with site survey.`
2. **Expected:** Both notes are visible, with the newest first (reverse chronological).

### 12.3 Edit a Note

1. Click the edit button on the first note.
2. Change the text to add: `(Budget: $150,000 - $200,000 range)`
3. Save.
4. **Expected:** Note text is updated.

### 12.4 Delete a Note

1. Click the delete button on one of the notes.
2. Confirm deletion.
3. **Expected:** Note is removed.

### 12.5 Upload an Attachment (if supported)

1. On the Oakwood Office Park record, find the **Attachments** section.
2. Click **Upload** / **Attach File**.
3. Select a test file (e.g., a small PDF or image).
4. **Expected:** File uploads and appears in the attachments list.
5. Click the attachment.
6. **Expected:** File downloads or opens in a new tab.

**🐛 Issue Tracking:** Document any issues with notes CRUD, attachment upload/download, or file size limits.

---

## 13. Flow Engine — Record-Triggered Flows

**Objective:** Build and test flows that fire automatically when records are created, updated, or deleted.

### 13.1 Flow #1: Auto-Generate Work Order Number

**Business Rule:** When a new Work Order is created, automatically assign a sequential work order number in the format `WO-YYYY-NNN`.

1. Navigate to **Setup → Flows**.
2. Click **New Flow**.
3. Select trigger type: **Record-Triggered**.
4. Configure trigger:
   - **Collection:** work-orders
   - **Event:** On Create (Before Save or After Create, depending on what's available)
5. **Name:** `Auto-Generate Work Order Number`
6. Click **Create** / **Next** to open the flow designer.

**In the Flow Designer:**

7. The trigger node should already be configured.
8. Add a **Task** node:
   - **Action:** Field Update (or equivalent)
   - **Target Record:** Triggering record
   - **Field:** name (Work Order Number)
   - **Value:** Use an expression or formula to generate the number. If a merge field or expression is available, use something like `WO-2026-{sequence}`. If not, set a static value as a placeholder and document that auto-numbering needs a custom approach.
9. Connect the trigger → task → Succeed.
10. Click **Save**.
11. Click **Publish** (or **Activate**).
12. **Expected:** Flow status changes to Published/Active.

**Test the Flow:**

13. Navigate to **Work Orders** and click **New**.
14. Fill in:
    - **Customer:** Johnson Family
    - **Property:** Johnson Home
    - **Work Type:** Maintenance
    - **Priority:** Medium
    - **Status:** New
    - **Description:** `Annual furnace maintenance and filter replacement`
15. Leave the **Work Order Number** field empty (or enter a placeholder).
16. Click **Save**.
17. **Expected:** The work order is created. If the flow ran, the Work Order Number field should be populated automatically.
18. Navigate to **Setup → Flows** and click on the flow.
19. Check the **Execution History** tab.
20. **Expected:** An execution log shows with status "Completed" (or "Succeeded").
21. Click the execution to see step details.
22. **Expected:** Each step shows its status, input, output, and duration.

### 13.2 Flow #2: Update Customer Account Balance on Payment

**Business Rule:** When a payment is created, update the customer's account balance.

1. Create a new **Record-Triggered** flow.
2. Trigger: **payments** collection, **On Create** (After Create).
3. **Name:** `Update Customer Balance on Payment`

**Flow Steps:**

4. **Step 1: Query** — Query the invoice related to the payment (using the invoice reference field).
5. **Step 2: Query** — Query the customer from the invoice.
6. **Step 3: Update Record** — Update the customer's `account-balance` field (subtract the payment amount).
7. Connect: Trigger → Query Invoice → Query Customer → Update Customer → Succeed.
8. Save and Publish.

**Test the Flow:**

9. First, create a test invoice for "Johnson Family":
    - **Invoice Number:** INV-2026-001
    - **Customer:** Johnson Family
    - **Status:** Sent
    - **Invoice Date:** today
    - **Due Date:** 30 days from now
    - **Total:** 350.00
    - **Balance Due:** 350.00
10. Set the Johnson Family customer's **Account Balance** to `350.00`.
11. Create a new **Payment**:
    - **Invoice:** INV-2026-001
    - **Customer:** Johnson Family
    - **Payment Date:** today
    - **Amount:** 350.00
    - **Payment Method:** Credit Card
    - **Status:** Completed
12. After saving, navigate to the Johnson Family customer record.
13. **Expected:** Account Balance is now `0.00` (or reduced by 350.00).
14. Check the flow execution history.
15. **Expected:** Execution completed successfully with all steps passing.

### 13.3 Flow #3: Notify Dispatcher When Emergency Work Order Created

**Business Rule:** When a work order is created with Priority = "Emergency", send a notification.

1. Create a new **Record-Triggered** flow.
2. Trigger: **work-orders** collection, **On Create**.
3. **Name:** `Emergency Work Order Notification`

**Flow Steps:**

4. **Step 1: Decision/Choice** — Check if `priority` equals "Emergency".
   - **True branch:** Continue to notification.
   - **False branch:** End (Succeed with no action).
5. **Step 2 (True branch): Email Alert** (or Send Notification) —
   - **To:** mike.chen@summitcomfort.test (dispatcher)
   - **Subject:** `EMERGENCY: New Work Order - {work-order-number}`
   - **Body:** Include customer name, property address, work type, and description.
6. **Step 3: Succeed.**

7. Save and Publish.

**Test the Flow:**

8. Create a new work order:
   - **Customer:** Fresh Start Bakery
   - **Property:** Fresh Start Bakery
   - **Equipment:** Walk-in Cooler Compressor
   - **Work Type:** Emergency
   - **Priority:** Emergency
   - **Status:** New
   - **Description:** `Walk-in cooler not cooling. All inventory at risk. Customer reports temperature rising above 45°F.`
9. **Expected:** Flow executes. Check execution history — decision node should take the "True" branch.
10. If email delivery is configured, verify the email was queued/sent.
11. Create another work order with **Priority: Low**.
12. **Expected:** Flow executes but the decision takes the "False" branch — no notification sent.

### 13.4 Flow #4: Update Equipment Last Service Date on Work Order Completion

**Business Rule:** When a work order status changes to "Completed", update the associated equipment's `last-service-date` to today.

1. Create a new **Record-Triggered** flow.
2. Trigger: **work-orders** collection, **On Update**.
3. **Condition/Filter:** Only fire when `status` changes to "Completed".
4. **Name:** `Update Equipment Last Service Date`

**Flow Steps:**

5. **Step 1: Decision** — Check if `status` equals "Completed" AND `equipment` is not null.
6. **Step 2 (True): Update Record** — Update the equipment record's `last-service-date` to today's date and `status` to "Operational".
7. Connect and add terminal nodes.
8. Save and Publish.

**Test the Flow:**

9. Open the emergency work order created in 13.3.
10. Edit it: Change **Status** to "Completed", set **Completed Date** to today.
11. Save.
12. Navigate to the **Walk-in Cooler Compressor** equipment record.
13. **Expected:** `last-service-date` is set to today. Status is "Operational".

### 13.5 Flow #5: Cascade Work Order Status to Appointments

**Business Rule:** When a work order is cancelled, cancel all its scheduled appointments.

1. Create a new **Record-Triggered** flow.
2. Trigger: **work-orders**, **On Update**, filter on `status` change to "Cancelled".
3. **Name:** `Cancel Appointments on WO Cancel`

**Flow Steps:**

4. **Step 1: Query Records** — Query all appointments where `work-order` = triggering record ID AND `status` IN ("Scheduled", "En Route").
5. **Step 2: Map** (or Loop) — For each appointment found, update `status` to "Cancelled".
6. Terminal.
7. Save and Publish.

**Test the Flow:**

8. Create a new work order (WO for Martinez Residence, Repair, Medium priority).
9. Create two appointments linked to that work order (both with status "Scheduled").
10. Edit the work order: Change status to "Cancelled".
11. Save.
12. Check both appointment records.
13. **Expected:** Both appointments now show status "Cancelled".

**🐛 Issue Tracking:** Document any issues with flow designer, trigger configuration, action execution, expression evaluation, or unexpected flow behavior.

---

## 14. Flow Engine — Scheduled Flows

**Objective:** Build and test flows that run on a schedule (cron).

### 14.1 Flow #6: Daily Overdue Invoice Check

**Business Rule:** Every day at 8:00 AM, find all invoices where `due-date` is in the past and `status` is "Sent", and update their status to "Overdue".

1. Navigate to **Setup → Flows**.
2. Create a new flow with trigger type: **Scheduled**.
3. Configure schedule:
   - **Cron Expression:** `0 8 * * *` (daily at 8 AM)
   - **Timezone:** America/Chicago
4. **Name:** `Daily Overdue Invoice Check`

**Flow Steps:**

5. **Step 1: Query Records** — Query invoices where `status` = "Sent" AND `due-date` < today.
6. **Step 2: Decision** — Check if any records were returned.
7. **Step 3 (True): Map** — For each overdue invoice, update `status` to "Overdue".
8. **Step 4: Succeed** with a result message indicating how many invoices were updated.

9. Save and Publish.

**Test the Flow:**

10. Create a test invoice:
    - **Customer:** Oakwood Office Park
    - **Invoice Number:** INV-2026-002
    - **Status:** Sent
    - **Invoice Date:** 60 days ago
    - **Due Date:** 30 days ago (in the past)
    - **Total:** 5000.00
    - **Balance Due:** 5000.00
11. If the flow supports manual execution (a "Run Now" or "Test" button), trigger it manually.
12. **Expected:** The invoice status changes from "Sent" to "Overdue".
13. Check the execution history for the flow.
14. **Expected:** Execution completed, step logs show the query found 1 invoice and updated it.

### 14.2 Flow #7: Weekly Maintenance Plan Visit Reminder

**Business Rule:** Every Monday at 7:00 AM, find maintenance plans where `next-visit-date` is within the next 7 days and create work orders for them.

1. Create a new **Scheduled** flow.
2. Schedule: `0 7 * * 1` (every Monday at 7 AM).
3. **Name:** `Weekly Maintenance Reminder`

**Flow Steps:**

4. **Step 1: Query** — Find maintenance plans where `status` = "Active" AND `next-visit-date` is within 7 days from now.
5. **Step 2: Map** — For each plan, create a new work order:
   - Work Type: Maintenance
   - Priority: Low
   - Status: New
   - Customer: from the plan
   - Property: from the plan
   - Description: "Scheduled maintenance visit per maintenance plan: {plan name}"
6. **Step 3: Succeed.**

7. Save and Publish.

**Test the Flow:**

8. Create a maintenance plan:
    - **Name:** Annual Comfort Plan - Johnson
    - **Customer:** Johnson Family
    - **Property:** Johnson Home
    - **Plan Type:** Standard
    - **Status:** Active
    - **Start Date:** 6 months ago
    - **End Date:** 6 months from now
    - **Annual Cost:** 199.00
    - **Visits Per Year:** 2
    - **Visits Completed:** 0
    - **Next Visit Date:** 3 days from now
9. Manually trigger the flow.
10. **Expected:** A new work order is created for Johnson Family at Johnson Home with type "Maintenance".
11. Verify the work order appears in the Work Orders list.

**🐛 Issue Tracking:** Document any issues with scheduled trigger configuration, cron parsing, manual execution, or flow step failures.

---

## 15. Flow Engine — Auto-Launched / API Flows

**Objective:** Build flows that can be triggered manually or via API/webhook.

### 15.1 Flow #8: Generate Invoice from Work Order

**Business Rule:** An "Auto-Launched" flow that takes a work order ID as input and creates an invoice with all parts used as line items.

1. Create a new flow with trigger type: **Auto-Launched** (or **API-Invoked**).
2. **Name:** `Generate Invoice from Work Order`

**Flow Steps:**

3. **Step 1: Query** — Get the work order record by ID (from input).
4. **Step 2: Query** — Get all parts-used records linked to the work order.
5. **Step 3: Create Record** — Create an invoice:
   - Customer: from work order
   - Work Order: the input work order
   - Status: Draft
   - Invoice Date: today
   - Due Date: based on customer's payment terms
6. **Step 4: Map** — For each parts-used record, create an invoice line:
   - Invoice: the newly created invoice
   - Description: product name
   - Quantity: from parts-used
   - Unit Price: from parts-used
   - Line Total: calculated
   - Line Type: Parts
7. **Step 5: Create Record** — Create a labor invoice line:
   - Description: "Labor - {work-type}"
   - Quantity: actual-hours from work order
   - Unit Price: 125.00 (standard labor rate)
   - Line Type: Labor
8. **Step 6: Update Record** — Update the invoice subtotal and total.
9. **Step 7: Update Record** — Update the work order status to "Invoiced".
10. Terminal: Succeed.

11. Save and Publish.

**Test the Flow:**

12. First, create a complete work order scenario:
    - Work order for Johnson Family, Maintenance, Status: Completed.
    - Add 2 parts-used records (e.g., 1x Air Filter, 1x Capacitor).
    - Set actual-hours to 2.
13. Trigger the flow manually with the work order ID as input.
14. **Expected:** An invoice is created with line items for the parts and labor.
15. **Expected:** The work order status changes to "Invoiced".
16. Open the invoice and verify all line items are correct.

### 15.2 Flow #9: Webhook — Receive External Service Request

**Business Rule:** An auto-launched flow that receives webhook payloads from an external system (e.g., a customer portal) to create work orders.

1. Create a new **Auto-Launched** flow.
2. **Name:** `External Service Request Webhook`
3. Check if the flow has a webhook URL (look for a webhook URL option).

**Flow Steps:**

4. **Step 1: Pass** — Transform/map the incoming payload to work order fields.
5. **Step 2: Query** — Look up the customer by email or account number from the payload.
6. **Step 3: Decision** — If customer found, proceed. If not, fail with an error.
7. **Step 4: Create Record** — Create a work order from the mapped data.
8. **Step 5: Succeed** — Return the new work order ID.

9. Save and Publish.
10. Note the webhook URL for testing.

**Test:** The flow can be tested via the UI test dialog by providing a sample JSON payload.

**🐛 Issue Tracking:** Document any issues with auto-launched flows, webhook URL generation, input parameter handling, or complex multi-step execution.

---

## 16. Flow Engine — Advanced Patterns

**Objective:** Test advanced flow engine capabilities — parallel execution, error handling, wait states, and data transformations.

### 16.1 Flow #10: Parallel Notification on Work Order Assignment

**Business Rule:** When a work order is assigned to a technician (assigned-technician field is updated), simultaneously notify the technician AND update the work order status to "Scheduled".

1. Create a **Record-Triggered** flow on **work-orders**, on update (when assigned-technician changes).
2. **Name:** `Work Order Assignment Notifications`

**Flow Steps:**

3. **Step 1: Parallel** — Execute two branches simultaneously:
   - **Branch A: Email Alert** — Send email to the assigned technician with work order details and customer address.
   - **Branch B: Update Record** — Update work order status to "Scheduled" and set scheduled-date to today.
4. After parallel completes: **Succeed**.

5. Save and Publish.

**Test:**

6. Open a work order with status "New".
7. Edit: Set **Assigned Technician** to Jake Robinson.
8. Save.
9. Check execution history.
10. **Expected:** Parallel node shows both branches executed. Status is "Scheduled".

### 16.2 Flow #11: Error Handling — HTTP Callout with Retry

**Business Rule:** When a quote is accepted, call an external accounting system API. If it fails, retry up to 3 times. If still failing, log the error and create a task for manual follow-up.

1. Create a **Record-Triggered** flow on **quotes**, on update (status changes to "Accepted").
2. **Name:** `Sync Accepted Quote to Accounting`

**Flow Steps:**

3. **Step 1: HTTP Callout** — POST to `https://httpbin.org/post` (test endpoint) with quote data.
   - Configure **Retry**: 3 attempts, exponential backoff.
   - Configure **Catch**: On failure, redirect to error handling branch.
4. **Step 2 (Success path): Update Record** — Update quote with a "synced" note.
5. **Step 2 (Error path): Task** — Log the error message.
6. **Step 3 (Error path): Create Record** — Create a job log or note documenting the sync failure.
7. Terminal.

8. Save and Publish.

**Test:**

9. Update a quote status to "Accepted".
10. Check execution history.
11. **Expected:** HTTP callout step shows the request was made.
12. **Expected:** If successful, the success path is taken. If the URL is unreachable, the catch path fires.

### 16.3 Flow #12: Wait State — Quote Follow-Up Reminder

**Business Rule:** When a quote is sent, wait 7 days. If the quote is still in "Sent" status after 7 days, send a follow-up reminder to the sales rep.

1. Create a **Record-Triggered** flow on **quotes**, on update (status changes to "Sent").
2. **Name:** `Quote Follow-Up Reminder`

**Flow Steps:**

3. **Step 1: Wait** — Wait for 7 days (or a shorter duration for testing, e.g., 1 minute).
4. **Step 2: Query** — Re-query the quote to check its current status.
5. **Step 3: Decision** — If status is still "Sent":
   - **True:** Send email to the sales rep: "Quote {quote-number} has been pending for 7 days. Follow up with {customer-name}."
   - **False:** Succeed (quote was already accepted/rejected, no action needed).
6. Terminal.

7. Save and Publish.

**Test (if using short wait):**

8. Create a new quote with status "Draft", then update to "Sent".
9. Wait for the configured duration.
10. Check execution history.
11. **Expected:** The wait step shows as "Waiting" initially, then completes. The decision checks the current status.

### 16.4 Flow #13: Data Transformation with Pass State

**Business Rule:** Transform work order data into a summary format before sending a completion report.

1. Create an **Auto-Launched** flow.
2. **Name:** `Work Order Completion Summary`

**Flow Steps:**

3. **Step 1: Query** — Get the work order by ID.
4. **Step 2: Query** — Get all parts used for the work order.
5. **Step 3: Pass** — Transform the data:
   - Calculate total parts cost (sum of all parts-used line totals).
   - Calculate total labor cost (actual-hours × $125).
   - Build a summary string.
6. **Step 4: Email Alert** — Send the summary to the customer and office manager.
7. Succeed.

8. Save and Publish.

**🐛 Issue Tracking:** Document any issues with parallel execution, error handling/catch/retry, wait states, HTTP callouts, or data transformations.

---

## 17. Email Templates & Notifications

**Objective:** Create email templates and verify they integrate with flows.

### 17.1 Create Email Templates

Navigate to **Setup → Email Templates** and create:

#### 17.1.1 Appointment Confirmation

- **Name:** `Appointment Confirmation`
- **Subject:** `Your HVAC Appointment is Confirmed - {{appointment-date}}`
- **Body:**
```
Dear {{customer-name}},

Your service appointment has been confirmed:

Date: {{appointment-date}}
Time: {{start-time}}
Technician: {{technician-name}}
Service: {{work-order-description}}
Location: {{property-address}}

Please ensure someone is available to provide access. If you need to reschedule, please call us at (555) 555-HVAC.

Thank you for choosing Summit Comfort HVAC!
```

#### 17.1.2 Invoice Notification

- **Name:** `Invoice Notification`
- **Subject:** `Invoice {{invoice-number}} from Summit Comfort HVAC`
- **Body:**
```
Dear {{customer-name}},

Please find below your invoice details:

Invoice #: {{invoice-number}}
Date: {{invoice-date}}
Due Date: {{due-date}}
Amount Due: ${{balance-due}}

Service performed: {{work-order-description}}

Payment can be made by credit card, check, or ACH transfer.

Thank you for your business!

Summit Comfort HVAC
```

#### 17.1.3 Work Order Completion

- **Name:** `Work Order Completion`
- **Subject:** `Service Complete - Work Order {{work-order-number}}`
- **Body:**
```
Dear {{customer-name}},

We're pleased to confirm that your service has been completed:

Work Order: {{work-order-number}}
Service Type: {{work-type}}
Technician: {{technician-name}}
Date Completed: {{completed-date}}

{{description}}

If you have any questions about the service performed, please don't hesitate to contact us.

Thank you for choosing Summit Comfort HVAC!
```

### 17.2 Verify Templates Save Correctly

1. After creating each template, navigate away and then back.
2. **Expected:** Templates persist with all fields intact.
3. Verify merge field syntax is supported (e.g., `{{field-name}}` or `{!field-name}`).

**🐛 Issue Tracking:** Document any issues with template creation, merge field syntax, or template rendering.

---

## 18. Approval Processes

**Objective:** Configure and test an approval workflow.

### 18.1 Create a Quote Approval Process

**Business Rule:** Quotes over $10,000 require manager approval before they can be sent to the customer.

1. Navigate to **Setup → Approval Processes**.
2. Click **New Approval Process**.
3. Configure:
   - **Name:** `High-Value Quote Approval`
   - **Collection:** quotes
   - **Entry Criteria:** `total` > 10000
   - **Step 1:**
     - **Approver:** sarah.mitchell (admin/manager)
     - **Action on Approve:** Update quote status to "Sent"
     - **Action on Reject:** Update quote status to "Rejected"
   - **Allow Recall:** Yes
   - **Record Editability During Approval:** Read-Only
4. Activate the approval process.
5. Click **Save**.

### 18.2 Test the Approval Process

1. Log in as **rachel.green** (Sales Rep).
2. Create a new quote:
   - **Customer:** Oakwood Office Park
   - **Property:** Oakwood Building B
   - **Quote Number:** Q-2026-001
   - **Quote Date:** today
   - **Expiration Date:** 30 days from now
   - **Total:** 15000.00
   - **Status:** Draft
3. Submit the quote for approval (look for a "Submit for Approval" button or action).
4. **Expected:** The quote enters the approval queue. Status changes to indicate pending approval.
5. **Expected:** The record becomes read-only for the submitter.

6. Log out and log in as **sarah.mitchell** (approver).
7. Navigate to the approval queue or the quote record.
8. **Expected:** The quote shows as pending approval with Approve/Reject buttons.
9. Click **Approve**.
10. **Expected:** Quote status changes to "Sent".

11. Repeat with a quote under $10,000.
12. **Expected:** The approval process does NOT trigger (entry criteria not met).

**🐛 Issue Tracking:** Document any issues with approval process configuration, submission, approval/rejection actions, or entry criteria evaluation.

---

## 19. Webhooks & Integration

**Objective:** Configure outbound webhooks and verify event delivery.

### 19.1 Configure Outbound Webhooks

1. Navigate to **Setup → Webhooks**.
2. **Expected:** The Svix webhook management portal loads.
3. Click **New Endpoint** (or **Add Endpoint**).
4. Configure:
   - **URL:** `https://webhook.site/unique-id` (use webhook.site to get a test URL, or use a similar service)
   - **Event Types:** Select events related to work orders (e.g., `record.created`, `record.updated` for the work-orders collection).
5. Click **Save**.

### 19.2 Test Webhook Delivery

1. Create a new work order.
2. Navigate to the webhook endpoint in the UI and check **Message History**.
3. **Expected:** A webhook message was sent with the work order creation event.
4. **Expected:** Message payload includes the record data in JSON format.
5. Update the work order.
6. **Expected:** Another webhook message is sent for the update event.

### 19.3 Configure a Connected App

1. Navigate to **Setup → Connected Apps**.
2. Click **New Connected App**.
3. Configure:
   - **Name:** `Summit Customer Portal`
   - **Scopes:** Select appropriate scopes (e.g., read/write for work-orders, customers).
   - **Redirect URI:** `https://portal.summitcomfort.test/callback`
   - **Rate Limit:** 1000 requests per hour
4. Click **Save**.
5. **Expected:** A Client ID and Client Secret are displayed (one-time only).
6. Copy the credentials.
7. **Expected:** The connected app appears in the list.

### 19.4 Create a Personal Access Token

1. Log in as **sarah.mitchell**.
2. Navigate to **API Tokens** (usually under the user menu or `/app/api-tokens`).
3. Click **New Token**.
4. Configure:
   - **Name:** `Sarah's Integration Token`
   - **Scopes:** Full access (or select specific scopes)
   - **Expiration:** 90 days from now
5. Click **Create**.
6. **Expected:** Token is displayed (starts with `klt_`). Copy it.
7. **Expected:** Token appears in the token list (value is hidden).
8. **Expected:** Cannot view the token value again after navigating away.

### 19.5 Revoke a Token

1. In the token list, click **Revoke** on the token.
2. Confirm.
3. **Expected:** Token is removed from the list.

**🐛 Issue Tracking:** Document any issues with webhook configuration, event delivery, connected app creation, or token management.

---

## 20. Field-Level Security & ABAC

**Objective:** Configure granular permissions and verify enforcement.

### 20.1 Configure Object Permissions on Profiles

Now that collections exist, configure permissions for each profile.

1. Navigate to **Setup → Profiles**.
2. Click on the **Technician** profile.
3. Navigate to the **Object Permissions** section.
4. Configure permissions:

| Collection | Read | Create | Update | Delete |
|-----------|------|--------|--------|--------|
| customers | ✅ | ❌ | ❌ | ❌ |
| contacts | ✅ | ❌ | ❌ | ❌ |
| properties | ✅ | ❌ | ❌ | ❌ |
| equipment | ✅ | ❌ | ✅ (status only) | ❌ |
| work-orders | ✅ | ❌ | ✅ (status, actual-hours, completed-date) | ❌ |
| appointments | ✅ | ❌ | ✅ (status, arrival-time, departure-time) | ❌ |
| job-logs | ✅ | ✅ | ✅ | ❌ |
| parts-used | ✅ | ✅ | ✅ | ✅ |
| product-catalog | ✅ | ❌ | ❌ | ❌ |
| inventory | ✅ | ❌ | ❌ | ❌ |
| invoices | ❌ | ❌ | ❌ | ❌ |
| payments | ❌ | ❌ | ❌ | ❌ |
| quotes | ❌ | ❌ | ❌ | ❌ |

5. Click **Save**.

### 20.2 Configure Field-Level Security

1. On the **Technician** profile, navigate to **Field Permissions**.
2. For the **Customers** collection, set the following fields to **hidden**:
   - account-balance
   - credit-limit
   - payment-terms
   - tax-exempt
3. For the **Work Orders** collection, set these fields to **read-only** for technicians:
   - customer, property, equipment (can see but not change)
   - parts-total, labor-total, total-cost (can see but not change)
4. Click **Save**.

### 20.3 Test Technician Permissions

1. Log in as **jake.robinson** (Technician).
2. Navigate to **Customers**.
3. **Expected:** Can see customer list (read access).
4. Click on "Johnson Family".
5. **Expected:** Can see customer details, but financial fields (account-balance, credit-limit, payment-terms, tax-exempt) are NOT visible.
6. **Expected:** No "Edit" or "Delete" buttons on the customer record.
7. Navigate to **Work Orders**.
8. Open a work order assigned to Jake.
9. **Expected:** Can see all fields. Can edit status, actual-hours, and completed-date. Cannot edit customer, property, or cost fields.
10. Navigate to **Job Logs**.
11. **Expected:** Can create new job log entries.
12. Navigate to **Invoices**.
13. **Expected:** No access — page shows unauthorized or empty, depending on platform behavior.

### 20.4 Configure Dispatcher Permissions

1. Switch back to admin.
2. Edit the **Dispatcher** profile with appropriate permissions:
   - Full CRUD on work-orders and appointments.
   - Read on customers, contacts, properties, equipment.
   - Read on technician-skills (for assignment decisions).
   - No access to invoices, payments, or quotes.

### 20.5 Configure Sales Rep Permissions

1. Edit the **Sales Rep** profile:
   - Full CRUD on quotes, quote-lines.
   - Read/Create on customers, contacts.
   - Read on product-catalog.
   - Read on work-orders (no edit).
   - No access to inventory, invoices, payments.

### 20.6 Configure Office Manager Permissions

1. Edit the **Office Manager** profile:
   - Full CRUD on all customer-facing collections (customers, contacts, properties).
   - Full CRUD on work-orders, appointments, invoices, payments.
   - Read on inventory, product-catalog.
   - No access to system settings.

### 20.7 Configure Warehouse Permissions

1. Edit the **Warehouse** profile:
   - Full CRUD on inventory, product-catalog.
   - Read on parts-used (to see what's being consumed).
   - Read on work-orders (to see upcoming needs).
   - No access to customers, contacts, invoices, payments, quotes.

### 20.8 Test Custom ABAC Rule

1. Navigate to the **Technician** profile.
2. Go to the **Custom Rules** section (or Policy editor).
3. Create a custom rule:
   - **Rule Name:** `Technician Can Only See Assigned Work Orders`
   - **Collection:** work-orders
   - **Condition:** `resource.assigned-technician == request.principal.id`
   - **Effect:** Allow READ only when the above condition is true.
4. Save.

5. Log in as **jake.robinson**.
6. Navigate to **Work Orders**.
7. **Expected:** Only work orders assigned to Jake are visible.
8. Work orders assigned to Emily or David should NOT appear.

**🐛 Issue Tracking:** Document any issues with permission configuration, field-level security enforcement, ABAC rule syntax, or unauthorized access.

---

## 21. Monitoring & Observability

**Objective:** Verify monitoring tools capture platform activity correctly.

### 21.1 Check Request Logs

1. Navigate to **Monitoring → Requests** (`/monitoring/requests`).
2. **Expected:** Recent API requests are listed with method, path, status code, and response time.
3. Filter by **Method: POST**.
4. **Expected:** Only POST requests shown (record creations from data entry).
5. Filter by **Status: 4xx**.
6. **Expected:** Any authorization failures or bad requests are shown.
7. Click on a specific request trace.
8. **Expected:** Detailed trace view shows request/response headers, body, and timing.

### 21.2 Check Application Logs

1. Navigate to **Monitoring → Logs** (`/monitoring/logs`).
2. Filter by **Level: ERROR**.
3. **Expected:** Any errors encountered during testing are visible.
4. Filter by **Level: INFO** and search for "flow".
5. **Expected:** Flow execution log entries appear.

### 21.3 Check Error Dashboard

1. Navigate to **Monitoring → Errors** (`/monitoring/errors`).
2. **Expected:** Error frequency chart shows any errors.
3. If there were authorization failures in Section 20, they should appear here.

### 21.4 Check Endpoint Performance

1. Navigate to **Monitoring → Performance** (`/monitoring/performance`).
2. **Expected:** Endpoint performance metrics are listed.
3. Verify that the most-called endpoints (record CRUD) have reasonable response times.
4. **Expected:** Average latency under 500ms for most endpoints.

### 21.5 Check User Activity

1. Navigate to **Monitoring → Activity** (`/monitoring/activity`).
2. **Expected:** Login events for all users tested are logged.
3. **Expected:** Record create/update/delete events are logged.
4. Filter by user **jake.robinson**.
5. **Expected:** Only Jake's actions are shown.

### 21.6 Check System Health

1. Navigate to **System Health** (`/system-health`).
2. **Expected:** All services show green/healthy status:
   - Control Plane: ✅
   - Database: ✅
   - Kafka: ✅
   - Redis: ✅
3. **Expected:** API metrics charts show request rate, error rate, and latency.

**🐛 Issue Tracking:** Document any issues with monitoring pages not loading, missing data, or incorrect metrics.

---

## 22. Audit Trail Verification

**Objective:** Verify that all configuration and data changes are tracked.

### 22.1 Setup Audit Trail

1. Navigate to **Setup → Audit Trail** (`/audit-trail`).
2. **Expected:** All setup changes made during this test plan are logged:
   - Collection creations
   - Field additions
   - Profile creations and permission changes
   - Flow creations
   - User creations
   - Picklist creations
   - Email template creations
3. Filter by **Change Type: CREATED**.
4. **Expected:** All creation events are listed.
5. Click on a specific audit entry (e.g., a collection creation).
6. **Expected:** Details show who made the change, when, and what was changed.

### 22.2 Login History

1. Navigate to **Setup → Login History** (`/login-history`).
2. **Expected:** All login attempts during testing are logged:
   - Successful logins for each user.
   - Failed login attempts from Section 3.5 (lockout test).
3. **Expected:** Each entry shows: user, timestamp, login type, status, IP address.

### 22.3 Security Audit

1. Navigate to **Setup → Security Audit** (`/security-audit`).
2. **Expected:** Security events are logged:
   - Permission changes (profile updates).
   - Failed authorization attempts (technician accessing invoices).
   - MFA enrollment events.
   - Account lockout events.

**🐛 Issue Tracking:** Document any missing audit entries or incorrect data.

---

## 23. Governor Limits & Rate Limiting

**Objective:** Verify governor limits are tracked and enforced.

### 23.1 Check Current Usage

1. Navigate to **Setup → Governor Limits** (`/governor-limits`).
2. **Expected:** Usage metrics reflect all the activity from this test plan:
   - API Requests: Should be in the hundreds/thousands.
   - Records: Count of all records created.
   - Collections: 18 custom collections.
   - Users: 9 users.
3. **Expected:** Visual indicators show usage as a percentage of limits.

### 23.2 Verify Rate Limit Headers (Observation Only)

1. While performing any action, check the browser's network developer tools.
2. Look at response headers for rate limit headers (e.g., `X-RateLimit-Limit`, `X-RateLimit-Remaining`).
3. **Expected:** Rate limit headers are present in API responses.

**🐛 Issue Tracking:** Document any issues with governor limit tracking or display.

---

## 24. Bulk Operations

**Objective:** Test bulk data import/export capabilities.

### 24.1 Export Customer Data

1. Navigate to the **Customers** list view.
2. Look for an **Export** option (CSV or JSON).
3. Click **Export**.
4. **Expected:** A file downloads containing all customer records.
5. Open the file and verify data integrity.

### 24.2 Bulk Import Test (if available)

1. Navigate to **Setup → Bulk Jobs** (`/bulk-jobs`).
2. Click **New Bulk Job**.
3. Configure:
   - **Collection:** product-catalog
   - **Operation:** INSERT
   - **Batch Size:** 10
4. Prepare a CSV file with 5 new products.
5. Upload the CSV.
6. **Expected:** Bulk job starts and shows progress.
7. **Expected:** All 5 products are created successfully.
8. Check the Product Catalog list view.
9. **Expected:** New products appear in the list.

**🐛 Issue Tracking:** Document any issues with export/import, file format problems, or bulk operation failures.

---

## 25. Configuration Packages

**Objective:** Test the ability to export and import platform configuration.

### 25.1 Export Configuration Package

1. Navigate to **Setup → Packages** (`/packages`).
2. Click **Export** (or **New Export**).
3. Configure the export:
   - **Package Name:** `Summit Comfort HVAC Config`
   - **Version:** `1.0.0`
   - **Include:** Select all collections, profiles, flows, picklists, email templates, page layouts, menus.
4. Click **Export** / **Create Package**.
5. **Expected:** A package file is generated and available for download.

### 25.2 Verify Package Contents

1. Download the package.
2. **Expected:** Package contains JSON/ZIP with all selected configuration items.
3. Verify that collections, fields, picklists, and flows are all represented.

**🐛 Issue Tracking:** Document any issues with package export, missing items, or import errors.

---

## 26. Negative Testing & Error Handling

**Objective:** Verify the platform handles errors, invalid inputs, and edge cases gracefully.

### 26.1 Required Field Validation

1. Navigate to **Customers** → **New**.
2. Leave all required fields blank.
3. Click **Save**.
4. **Expected:** Validation errors appear for each required field.
5. **Expected:** The record is NOT created.
6. Fill in only the name but leave other required fields blank.
7. Click **Save**.
8. **Expected:** Validation errors for remaining required fields.

### 26.2 Invalid Data Type Entry

1. Navigate to **Equipment** → **New**.
2. In the **Energy Rating** (number) field, enter `abc`.
3. **Expected:** The field rejects non-numeric input or shows a validation error.

### 26.3 Duplicate Prevention

1. Try to create two collections with the same API name.
2. **Expected:** The platform prevents duplicates with a clear error message.

### 26.4 Delete Cascade / Prevention

1. Try to delete a customer that has related contacts, properties, and work orders.
2. **Expected:** Either cascade deletion (with warning) or prevention with an error explaining related records exist.

### 26.5 Unauthorized Access Attempts

1. Log in as **jake.robinson** (Technician).
2. Manually navigate to `/setup/collections`.
3. **Expected:** Access denied / unauthorized page.
4. Try to navigate directly to an invoice record URL.
5. **Expected:** Access denied.

### 26.6 Session Expiry

1. Log in and note the session.
2. Wait for the session to expire (or manually clear the session cookie).
3. Attempt to perform an action.
4. **Expected:** Redirected to login page. No data loss or error.

### 26.7 Concurrent Edit Handling

1. Open the same customer record in two browser tabs.
2. In Tab A, change the phone number and save.
3. In Tab B, change the email and save.
4. **Expected:** Either optimistic locking prevents the second save (with an error about stale data), or both changes are merged correctly.

**🐛 Issue Tracking:** Document all error handling behaviors, especially any unhandled exceptions, blank screens, or confusing error messages.

---

## 27. Cross-Cutting Concerns

**Objective:** Verify platform behaviors that span multiple features.

### 27.1 Pagination Across All Collections

1. For each collection with more than 10 records, verify pagination works:
   - Page navigation (next, previous, specific page)
   - Page size selection (if available)
   - Record count is accurate

### 27.2 Breadcrumb Navigation

1. Navigate deep into the app: Home → Customers → Oakwood Office Park → Properties → Oakwood Building A → Equipment → RTU-A1.
2. **Expected:** Breadcrumbs show the full navigation path.
3. Click a breadcrumb.
4. **Expected:** Navigates back to that level correctly.

### 27.3 Browser Back/Forward

1. Navigate through several pages.
2. Click the browser **Back** button.
3. **Expected:** Returns to the previous page without errors.
4. Click **Forward**.
5. **Expected:** Returns to the next page without errors.

### 27.4 Responsive / Mobile Layout

1. Resize the browser window to a narrow width (mobile simulation).
2. **Expected:** The layout adjusts — navigation may collapse into a hamburger menu, forms stack vertically.
3. Verify key functions still work at mobile width:
   - Can navigate menus
   - Can view records
   - Can create/edit records

### 27.5 Loading States and Feedback

1. During any data load (list views, record detail, form submission):
2. **Expected:** A loading spinner or skeleton is visible while data loads.
3. After successful operations:
4. **Expected:** A success toast/notification appears.
5. After failed operations:
6. **Expected:** An error message with actionable information appears.

### 27.6 Multiple Flows on Same Collection

1. Verify that when a work order is created, multiple flows can fire:
   - Auto-generate work order number (Section 13.1)
   - Emergency notification (Section 13.3) — if priority is Emergency
2. **Expected:** Both flows execute without conflict.

**🐛 Issue Tracking:** Document any issues with cross-cutting behaviors.

---

## 28. Issue Tracking Template

Use this template to document every issue found during testing:

```
### ISSUE-XXX: [Short Description]

**Section:** [Test plan section number, e.g., 13.2]
**Severity:** Critical / High / Medium / Low
**Steps to Reproduce:**
1. ...
2. ...
3. ...

**Expected Result:** [What should happen]
**Actual Result:** [What actually happened]
**Screenshot:** [If applicable]
**Browser / OS:** [e.g., Chrome 120 / macOS]
**Notes:** [Additional context, workarounds, etc.]
**Status:** Open / In Progress / Fixed / Won't Fix
**Fix Task:** [Link to branch/PR if created]
```

---

## 29. Test Completion Checklist

Use this checklist to track overall progress:

### Tenant & Auth
- [ ] Tenant created and accessible
- [ ] Password policy configured and enforced
- [ ] MFA configured (if applicable)
- [ ] OIDC provider configured (if applicable)

### Users & Security
- [ ] All 9 users created
- [ ] 5 custom profiles created
- [ ] Account lockout tested
- [ ] MFA enrollment tested

### Data Model
- [ ] All 18 collections created
- [ ] All fields defined for each collection
- [ ] Reference fields working correctly
- [ ] All global picklists created

### UI Configuration
- [ ] Key page layouts configured
- [ ] Navigation menu created and working
- [ ] List views created and filtering correctly

### Sample Data
- [ ] Service areas populated
- [ ] Technician skills populated
- [ ] Customers populated (8)
- [ ] Contacts populated (10)
- [ ] Properties populated (9)
- [ ] Equipment populated (12)
- [ ] Product catalog populated (15)
- [ ] Inventory records populated (16)

### Flow Engine
- [ ] Flow #1: Auto-generate work order number — working
- [ ] Flow #2: Update customer balance on payment — working
- [ ] Flow #3: Emergency work order notification — working
- [ ] Flow #4: Update equipment last service date — working
- [ ] Flow #5: Cancel appointments on WO cancel — working
- [ ] Flow #6: Daily overdue invoice check — working
- [ ] Flow #7: Weekly maintenance plan reminder — working
- [ ] Flow #8: Generate invoice from work order — working
- [ ] Flow #9: External webhook receiver — working
- [ ] Flow #10: Parallel notification on assignment — working
- [ ] Flow #11: HTTP callout with retry/catch — working
- [ ] Flow #12: Wait state quote follow-up — working
- [ ] Flow #13: Data transformation summary — working

### Integration
- [ ] Email templates created (3)
- [ ] Outbound webhooks configured and tested
- [ ] Connected app created
- [ ] Personal access token created and revoked

### Approval Processes
- [ ] High-value quote approval created
- [ ] Approval submission tested
- [ ] Approve action tested
- [ ] Reject action tested

### Permissions & ABAC
- [ ] Technician permissions configured and tested
- [ ] Dispatcher permissions configured and tested
- [ ] Sales rep permissions configured and tested
- [ ] Office manager permissions configured and tested
- [ ] Warehouse permissions configured and tested
- [ ] Field-level security enforced
- [ ] Custom ABAC rule created and enforced

### Monitoring
- [ ] Request logs visible
- [ ] Application logs visible
- [ ] Error dashboard working
- [ ] Endpoint performance metrics visible
- [ ] User activity tracked
- [ ] System health green

### Audit
- [ ] Setup audit trail complete
- [ ] Login history complete
- [ ] Security audit events logged

### Other
- [ ] Governor limits tracked
- [ ] Bulk export tested
- [ ] Bulk import tested
- [ ] Configuration package exported
- [ ] Negative testing completed (7 scenarios)
- [ ] Cross-cutting concerns verified (6 scenarios)

### Issues Summary
- **Critical:** ___ issues
- **High:** ___ issues
- **Medium:** ___ issues
- **Low:** ___ issues
- **Total:** ___ issues

---

## End-to-End Business Scenario Summary

When this test plan is complete, the Summit Comfort HVAC tenant will have:

1. **A fully configured tenant** with authentication, MFA, and password policies.
2. **9 users** across 7 roles with granular permissions.
3. **18 collections** forming a complete HVAC business data model.
4. **25 global picklists** for standardized data entry.
5. **100+ sample records** across all collections representing realistic business data.
6. **13 automated flows** covering record triggers, scheduled jobs, API invocations, parallel execution, error handling, wait states, and data transformations.
7. **3 email templates** for customer communications.
8. **1 approval process** for high-value quotes.
9. **Webhook integration** for external system connectivity.
10. **Complete RBAC configuration** with field-level security and ABAC rules.
11. **Full monitoring and audit coverage** of all platform activity.

This represents a real-world HVAC business operation and exercises the vast majority of the Kelta platform's capabilities through the UI alone.
