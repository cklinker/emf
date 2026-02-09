# UI Feature Gap Analysis & Implementation Plan

## Executive Summary

This document compares every control plane API endpoint against the emf-ui implementation
and identifies gaps where backend capabilities exist but the UI does not expose them.

**Backend:** 38 REST controllers, ~200+ endpoints across 22 functional areas
**UI:** 34 page components with 37+ routes, all making real API calls
**SDK:** 35 functional areas with 165+ methods in AdminClient

**Finding:** All UI pages have real API integration (no stubs), but many pages only expose
a subset of the backend's capabilities. The primary gaps are missing sub-features within
existing pages, not missing pages entirely.

---

## Part 1: Feature Gap Analysis

### Legend
- **FULL** — UI fully covers the backend API
- **PARTIAL** — UI covers some endpoints but not all
- **MISSING** — Backend capability exists but UI has no implementation

---

### 1. Collections Management
**Backend:** CollectionController (19 endpoints)
**UI:** CollectionsPage, CollectionDetailPage, CollectionFormPage

| Feature | Backend API | UI Status | Gap |
|---------|------------|-----------|-----|
| List collections | GET `/control/collections` | FULL | — |
| Get collection | GET `/control/collections/{id}` | FULL | — |
| Create collection | POST `/control/collections` | FULL | — |
| Update collection | PUT `/control/collections/{id}` | FULL | — |
| Delete collection | DELETE `/control/collections/{id}` | FULL | — |
| Add field | POST `/control/collections/{id}/fields` | FULL | — |
| Update field | PUT `/control/collections/{id}/fields/{fid}` | FULL | — |
| Delete field | DELETE `/control/collections/{id}/fields/{fid}` | FULL | — |
| Get versions | GET `/control/collections/{id}/versions` | FULL | — |
| Reorder fields | PUT `/control/collections/{id}/fields/reorder` | FULL | — |
| Publish version | POST `/control/collections/{id}/publish` | PARTIAL | UI has versions tab but no publish button |
| Get collection relationships | GET `/control/collections/{id}/relationships` | MISSING | No relationships display in detail page |

**Gaps to fix:**
- [ ] G1.1: Add "Publish Version" action to collection detail page
- [ ] G1.2: Add relationships view to collection detail page (or new tab)

---

### 2. Validation Rules (within Collection Detail)
**Backend:** ValidationRuleController (8 endpoints)
**UI:** CollectionDetailPage "Validation Rules" tab

| Feature | Backend API | UI Status | Gap |
|---------|------------|-----------|-----|
| List rules | GET `.../validation-rules` | FULL | — |
| Create rule | POST `.../validation-rules` | **MISSING** | Tab is read-only list |
| Get rule | GET `.../validation-rules/{id}` | MISSING | No detail view |
| Update rule | PUT `.../validation-rules/{id}` | **MISSING** | No edit capability |
| Delete rule | DELETE `.../validation-rules/{id}` | **MISSING** | No delete capability |
| Activate rule | POST `.../validation-rules/{id}/activate` | **MISSING** | No activate action |
| Deactivate rule | POST `.../validation-rules/{id}/deactivate` | **MISSING** | No deactivate action |
| Test rules | POST `.../validation-rules/test` | **MISSING** | No test functionality |

**Gaps to fix:**
- [ ] G2.1: Add create validation rule form (modal or inline)
- [ ] G2.2: Add edit validation rule capability
- [ ] G2.3: Add delete validation rule with confirmation
- [ ] G2.4: Add activate/deactivate toggle
- [ ] G2.5: Add "Test Rules" feature with sample record input

---

### 3. Record Types (within Collection Detail)
**Backend:** RecordTypeController (8 endpoints)
**UI:** CollectionDetailPage "Record Types" tab

| Feature | Backend API | UI Status | Gap |
|---------|------------|-----------|-----|
| List record types | GET `.../record-types` | FULL | — |
| Create record type | POST `.../record-types` | **MISSING** | Tab is read-only list |
| Get record type | GET `.../record-types/{id}` | MISSING | No detail view |
| Update record type | PUT `.../record-types/{id}` | **MISSING** | No edit capability |
| Delete record type | DELETE `.../record-types/{id}` | **MISSING** | No delete capability |
| Get picklist overrides | GET `.../record-types/{id}/picklists` | **MISSING** | No picklist override view |
| Set picklist override | PUT `.../record-types/{id}/picklists/{fid}` | **MISSING** | No override management |
| Remove picklist override | DELETE `.../record-types/{id}/picklists/{fid}` | **MISSING** | No override removal |

**Gaps to fix:**
- [ ] G3.1: Add create record type form
- [ ] G3.2: Add edit record type capability
- [ ] G3.3: Add delete record type with confirmation
- [ ] G3.4: Add picklist override management per record type

---

### 4. Field History (within Collection Detail)
**Backend:** FieldHistoryController (4 endpoints)
**UI:** CollectionDetailPage "Field History" tab

| Feature | Backend API | UI Status | Gap |
|---------|------------|-----------|-----|
| Record history | GET `.../records/{rid}/history` | **MISSING** | Tab only shows tracked-field list |
| Field history for record | GET `.../records/{rid}/history/{fname}` | **MISSING** | No per-field history |
| Field history across records | GET `.../field-history/{fname}` | **MISSING** | No cross-record view |
| User field history | GET `/control/users/{uid}/field-history` | **MISSING** | Not in user detail either |

**Gaps to fix:**
- [ ] G4.1: Add field history data viewer in collection detail (paginated, searchable)
- [ ] G4.2: Add field history tab in ResourceDetailPage (per-record history)
- [ ] G4.3: Add field change history in UserDetailPage

---

### 5. Picklists
**Backend:** PicklistController (10 endpoints)
**UI:** PicklistsPage

| Feature | Backend API | UI Status | Gap |
|---------|------------|-----------|-----|
| List global picklists | GET `/control/picklists/global` | FULL | — |
| CRUD global picklists | POST/PUT/DELETE | FULL | — |
| Get/set global values | GET/PUT `.../values` | FULL | — |
| Get field picklist values | GET `/control/picklists/fields/{fid}/values` | **MISSING** | No field-level values |
| Set field picklist values | PUT `/control/picklists/fields/{fid}/values` | **MISSING** | No field-level values |
| Get dependencies | GET `.../fields/{fid}/dependencies` | **MISSING** | No dependency UI |
| Set dependency | PUT `/control/picklists/dependencies` | **MISSING** | No dependency UI |
| Remove dependency | DELETE `.../dependencies/{c}/{d}` | **MISSING** | No dependency UI |

**Gaps to fix:**
- [ ] G5.1: Add field-level picklist value management (likely in collection field editor)
- [ ] G5.2: Add picklist dependency management UI

---

### 6. Profiles
**Backend:** ProfileController (8 endpoints)
**UI:** ProfilesPage

| Feature | Backend API | UI Status | Gap |
|---------|------------|-----------|-----|
| CRUD profiles | GET/POST/PUT/DELETE | FULL | — |
| Set object permissions | PUT `/{id}/object-permissions/{cid}` | PARTIAL | Need to verify detail panel |
| Set field permissions | PUT `/{id}/field-permissions` | PARTIAL | Need to verify detail panel |
| Set system permissions | PUT `/{id}/system-permissions` | PARTIAL | Need to verify detail panel |

**Gaps to fix:**
- [ ] G6.1: Verify and complete object/field/system permission editing in ProfilesPage

---

### 7. Permission Sets
**Backend:** PermissionSetController (10 endpoints)
**UI:** PermissionSetsPage

| Feature | Backend API | UI Status | Gap |
|---------|------------|-----------|-----|
| CRUD permission sets | GET/POST/PUT/DELETE | FULL | — |
| Set object permissions | PUT `/{id}/object-permissions/{cid}` | PARTIAL | Need to verify |
| Set field permissions | PUT `/{id}/field-permissions` | PARTIAL | Need to verify |
| Set system permissions | PUT `/{id}/system-permissions` | PARTIAL | Need to verify |
| Assign to user | POST `/{id}/assign/{uid}` | **MISSING** | No user assignment UI |
| Unassign from user | DELETE `/{id}/assign/{uid}` | **MISSING** | No user unassignment UI |

**Gaps to fix:**
- [ ] G7.1: Add user assignment/unassignment to permission sets
- [ ] G7.2: Verify and complete object/field/system permission editing

---

### 8. Sharing Settings
**Backend:** SharingController (12 endpoints)
**UI:** SharingSettingsPage

| Feature | Backend API | UI Status | Gap |
|---------|------------|-----------|-----|
| List/Get/Set OWD | GET/PUT `.../owd` | FULL | — |
| CRUD sharing rules | GET/POST/PUT/DELETE `.../rules` | FULL | — |
| CRUD user groups | GET/POST/PUT/DELETE `.../groups` | FULL | — |
| Role hierarchy | GET/PUT `.../roles/hierarchy` | FULL (separate page) | — |
| List record shares | GET `.../records/{cid}/{rid}` | **MISSING** | No record-level sharing |
| Create record share | POST `.../records/{cid}` | **MISSING** | No record-level sharing |
| Delete record share | DELETE `.../records/shares/{sid}` | **MISSING** | No record-level sharing |

**Gaps to fix:**
- [ ] G8.1: Add record-level sharing to ResourceDetailPage (share with user/group)

---

### 9. Approval Processes
**Backend:** ApprovalController (9 endpoints)
**UI:** ApprovalProcessesPage

| Feature | Backend API | UI Status | Gap |
|---------|------------|-----------|-----|
| CRUD approval processes | GET/POST/PUT/DELETE `.../processes` | FULL | — |
| List instances | GET `.../instances` | **MISSING** | No instance tracking UI |
| Get instance | GET `.../instances/{id}` | **MISSING** | No instance detail |
| Pending approvals | GET `.../instances/pending` | **MISSING** | No approval inbox |
| Submit for approval | POST `.../instances/submit` | **MISSING** | No submit action |
| Approve step | POST `.../instances/steps/{sid}/approve` | **MISSING** | No approve action |
| Reject step | POST `.../instances/steps/{sid}/reject` | **MISSING** | No reject action |
| Recall approval | POST `.../instances/{id}/recall` | **MISSING** | No recall action |

**Gaps to fix:**
- [ ] G9.1: Add approval instances list/detail view
- [ ] G9.2: Add "My Pending Approvals" inbox page
- [ ] G9.3: Add submit-for-approval action on resource detail page
- [ ] G9.4: Add approve/reject/recall actions in approval instance view

---

### 10. Flows
**Backend:** FlowController (10 endpoints)
**UI:** FlowsPage

| Feature | Backend API | UI Status | Gap |
|---------|------------|-----------|-----|
| CRUD flows | GET/POST/PUT/DELETE | FULL | — |
| List all executions | GET `.../executions` | **MISSING** | No execution history |
| List flow executions | GET `/{id}/executions` | **MISSING** | No per-flow history |
| Get execution detail | GET `.../executions/{eid}` | **MISSING** | No execution detail |
| Execute flow | POST `/{id}/execute` | **MISSING** | No manual trigger |
| Cancel execution | POST `.../executions/{eid}/cancel` | **MISSING** | No cancel action |

**Gaps to fix:**
- [ ] G10.1: Add flow execution history tab/panel
- [ ] G10.2: Add "Execute Flow" action button
- [ ] G10.3: Add execution detail view with step status
- [ ] G10.4: Add cancel execution action

---

### 11. Workflow Rules
**Backend:** WorkflowRuleController (7 endpoints)
**UI:** WorkflowRulesPage

| Feature | Backend API | UI Status | Gap |
|---------|------------|-----------|-----|
| CRUD workflow rules | GET/POST/PUT/DELETE | FULL | — |
| List all execution logs | GET `.../logs` | **MISSING** | No execution log view |
| List rule execution logs | GET `/{id}/logs` | **MISSING** | No per-rule log view |

**Gaps to fix:**
- [ ] G11.1: Add execution log viewer (global and per-rule)

---

### 12. Scheduled Jobs
**Backend:** ScheduledJobController (6 endpoints)
**UI:** ScheduledJobsPage

| Feature | Backend API | UI Status | Gap |
|---------|------------|-----------|-----|
| CRUD scheduled jobs | GET/POST/PUT/DELETE | FULL | — |
| List job execution logs | GET `/{id}/logs` | **MISSING** | No execution log view |

**Gaps to fix:**
- [ ] G12.1: Add execution log viewer per scheduled job

---

### 13. Scripts
**Backend:** ScriptController (7 endpoints)
**UI:** ScriptsPage

| Feature | Backend API | UI Status | Gap |
|---------|------------|-----------|-----|
| CRUD scripts | GET/POST/PUT/DELETE | FULL | — |
| List all execution logs | GET `.../logs` | **MISSING** | No execution log view |
| List script execution logs | GET `/{id}/logs` | **MISSING** | No per-script log view |

**Gaps to fix:**
- [ ] G13.1: Add execution log viewer (global and per-script)

---

### 14. Webhooks
**Backend:** WebhookController (6 endpoints)
**UI:** WebhooksPage

| Feature | Backend API | UI Status | Gap |
|---------|------------|-----------|-----|
| CRUD webhooks | GET/POST/PUT/DELETE | FULL | — |
| List deliveries | GET `/{id}/deliveries` | **MISSING** | No delivery history |

**Gaps to fix:**
- [ ] G14.1: Add webhook delivery history viewer

---

### 15. Connected Apps
**Backend:** ConnectedAppController (7 endpoints)
**UI:** ConnectedAppsPage

| Feature | Backend API | UI Status | Gap |
|---------|------------|-----------|-----|
| CRUD connected apps | GET/POST/PUT/DELETE | FULL | — |
| Rotate secret | POST `/{id}/rotate-secret` | PARTIAL | Need to verify |
| List tokens | GET `/{id}/tokens` | **MISSING** | No token listing |

**Gaps to fix:**
- [ ] G15.1: Add token listing for connected apps
- [ ] G15.2: Verify rotate-secret functionality

---

### 16. Email Templates
**Backend:** EmailTemplateController (6 endpoints)
**UI:** EmailTemplatesPage

| Feature | Backend API | UI Status | Gap |
|---------|------------|-----------|-----|
| CRUD email templates | GET/POST/PUT/DELETE | FULL | — |
| List email logs | GET `.../logs` | **MISSING** | No email delivery log |

**Gaps to fix:**
- [ ] G16.1: Add email delivery log viewer

---

### 17. Reports
**Backend:** ReportController (8 endpoints)
**UI:** ReportsPage

| Feature | Backend API | UI Status | Gap |
|---------|------------|-----------|-----|
| CRUD reports | GET/POST/PUT/DELETE | FULL | — |
| List report folders | GET `.../folders` | **MISSING** | No folder management |
| Create report folder | POST `.../folders` | **MISSING** | No folder creation |
| Delete report folder | DELETE `.../folders/{id}` | **MISSING** | No folder deletion |

**Gaps to fix:**
- [ ] G17.1: Add report folder management

---

### 18. Page Layouts
**Backend:** PageLayoutController (8 endpoints)
**UI:** PageLayoutsPage

| Feature | Backend API | UI Status | Gap |
|---------|------------|-----------|-----|
| CRUD layouts | GET/POST/PUT/DELETE | FULL | — |
| List assignments | GET `.../assignments` | **MISSING** | No assignment view |
| Assign layout | PUT `.../assignments` | **MISSING** | No assignment creation |
| Resolve layout | GET `.../resolve` | **MISSING** | No resolution preview |

**Gaps to fix:**
- [ ] G18.1: Add layout assignment management (profile + record type → layout)
- [ ] G18.2: Add layout resolution preview

---

### 19. Users
**Backend:** UserController (7 endpoints)
**UI:** UsersPage, UserDetailPage

| Feature | Backend API | UI Status | Gap |
|---------|------------|-----------|-----|
| List users (paginated) | GET `/control/users` | FULL | — |
| CRUD users | POST/GET/PUT | FULL | — |
| Activate/Deactivate | POST `/{id}/activate`, `/{id}/deactivate` | FULL | — |
| Login history | GET `/{id}/login-history` | **PARTIAL** | Need to verify UserDetailPage |

**Gaps to fix:**
- [ ] G19.1: Verify login history display in UserDetailPage

---

### 20. Data Export
**Backend:** ExportController (2 endpoints)
**UI:** ResourceListPage (client-side CSV only)

| Feature | Backend API | UI Status | Gap |
|---------|------------|-----------|-----|
| Export CSV (server-side) | POST `/control/export/csv` | **MISSING** | Only client-side CSV |
| Export XLSX | POST `/control/export/xlsx` | **MISSING** | No XLSX support at all |

**Gaps to fix:**
- [ ] G20.1: Switch to server-side CSV export for large datasets
- [ ] G20.2: Add XLSX export option

---

### 21. Bulk Jobs
**Backend:** BulkJobController (6 endpoints)
**UI:** BulkJobsPage

| Feature | Backend API | UI Status | Gap |
|---------|------------|-----------|-----|
| List/Get/Create/Abort | Full | FULL | — |
| Get results | GET `/{id}/results` | **PARTIAL** | Need to verify |
| Get errors | GET `/{id}/errors` | **PARTIAL** | Need to verify |

**Gaps to fix:**
- [ ] G21.1: Verify results/errors display in BulkJobsPage

---

### 22. Fully Covered Features (No Gaps)

These features have full UI coverage matching all backend endpoints:

| Feature | Pages | Status |
|---------|-------|--------|
| Services | ServicesPage | FULL |
| Roles | RolesPage | FULL |
| Policies | PoliciesPage | FULL |
| OIDC Providers | OIDCProvidersPage | FULL |
| UI Pages & Menus | PageBuilderPage, MenuBuilderPage | FULL |
| Packages | PackagesPage | FULL |
| Migrations | MigrationsPage | FULL |
| Setup Audit Trail | SetupAuditTrailPage | FULL |
| Governor Limits | GovernorLimitsPage | FULL |
| Tenants | TenantsPage | FULL |
| Dashboards (user) | DashboardsPage | FULL |
| List Views | ListViewsPage | FULL |
| Admin Dashboard | DashboardPage | FULL |
| Role Hierarchy | RoleHierarchyPage | FULL |
| Bootstrap / Discovery | (internal) | N/A (not user-facing) |
| Composite API | (internal) | N/A (SDK optimization) |
| Internal APIs | (internal) | N/A (gateway-only) |

---

## Part 2: Prioritized Implementation Plan

### Priority Tiers

**P0 — Critical (Core data management gaps)**
These gaps prevent managing fundamental data model features:

| ID | Gap | Page | Effort | Description |
|----|-----|------|--------|-------------|
| G2.1-G2.5 | Validation Rules CRUD | CollectionDetailPage | Large | Full create/edit/delete/activate/deactivate/test for validation rules in the collection detail tab. Currently read-only. |
| G3.1-G3.4 | Record Types CRUD | CollectionDetailPage | Large | Full create/edit/delete + picklist override management for record types. Currently read-only. |
| G5.1 | Field picklist values | FieldEditor / PicklistsPage | Medium | Manage picklist values at the field level (not just global). |
| G5.2 | Picklist dependencies | PicklistsPage | Medium | Define controlling/dependent field relationships for cascading picklists. |

**P1 — High (Workflow & automation completeness)**
These gaps prevent using automation features end-to-end:

| ID | Gap | Page | Effort | Description |
|----|-----|------|--------|-------------|
| G9.1-G9.4 | Approval instances | ApprovalProcessesPage + new inbox | Large | Instance tracking, pending approvals inbox, approve/reject/recall actions, submit-for-approval on resource detail. |
| G10.1-G10.4 | Flow executions | FlowsPage | Medium | Execution history, manual trigger, execution detail, cancel. |
| G11.1 | Workflow execution logs | WorkflowRulesPage | Small | Add execution log tab/panel. |
| G7.1 | Permission set user assignment | PermissionSetsPage | Small | Assign/unassign users to permission sets. |
| G8.1 | Record-level sharing | ResourceDetailPage | Medium | Share records with users/groups from the resource detail page. |

**P2 — Medium (Operational visibility)**
These gaps reduce operational monitoring capabilities:

| ID | Gap | Page | Effort | Description |
|----|-----|------|--------|-------------|
| G4.1-G4.3 | Field history viewer | CollectionDetailPage, ResourceDetailPage, UserDetailPage | Medium | Show actual field change history (currently only shows which fields are tracked). |
| G12.1 | Scheduled job logs | ScheduledJobsPage | Small | Show execution log per job. |
| G13.1 | Script execution logs | ScriptsPage | Small | Show execution logs (global and per-script). |
| G14.1 | Webhook deliveries | WebhooksPage | Small | Show delivery history per webhook. |
| G16.1 | Email delivery logs | EmailTemplatesPage | Small | Show email send logs with status. |
| G15.1 | Connected app tokens | ConnectedAppsPage | Small | List active tokens for connected apps. |

**P3 — Low (Enhancement & polish)**
These gaps are nice-to-have improvements:

| ID | Gap | Page | Effort | Description |
|----|-----|------|--------|-------------|
| G1.1 | Publish version | CollectionDetailPage | Small | Add publish action to versions tab. |
| G1.2 | Relationships view | CollectionDetailPage | Small | Display collection relationships. |
| G17.1 | Report folders | ReportsPage | Small | Folder management for organizing reports. |
| G18.1-G18.2 | Layout assignments | PageLayoutsPage | Medium | Assign layouts to profiles/record types + resolution preview. |
| G20.1-G20.2 | Server-side export | ResourceListPage | Medium | Server-side CSV/XLSX export for large datasets. |
| G6.1 | Profile permissions | ProfilesPage | Small | Verify/complete object, field, system permission editing. |

---

## Part 3: Implementation Details

### Stream 1: Collection Detail Enhancement (P0)

**G2: Validation Rules CRUD**
- Add a validation rule form modal with fields: name, formula (CEL expression), errorMessage, evaluateOn (CREATE/UPDATE/BOTH)
- Add create button in validation rules tab header
- Add edit/delete action buttons per rule row
- Add activate/deactivate toggle per rule
- Add "Test Rules" button that opens a dialog for entering sample record JSON and showing validation results
- API calls: POST/PUT/DELETE/POST activate/POST deactivate/POST test
- Reuse existing modal patterns from FieldEditor

**G3: Record Types CRUD**
- Add a record type form modal with fields: name, description, isDefault
- Add create button in record types tab header
- Add edit/delete action buttons per record type row
- Add picklist overrides panel: for each PICKLIST field, allow selecting which values are available for this record type
- API calls: POST/PUT/DELETE record types + GET/PUT/DELETE picklist overrides

**G5: Picklist Enhancement**
- G5.1: In FieldEditor, when field type is PICKLIST/MULTI_PICKLIST, add a "Values" section that calls GET/PUT `/control/picklists/fields/{fieldId}/values`
- G5.2: In PicklistsPage, add a "Dependencies" tab/section. Show controlling → dependent field mappings. Allow adding/removing dependencies.

### Stream 2: Approval & Flow Execution (P1)

**G9: Approval Instances**
- Add "Instances" tab to ApprovalProcessesPage showing all approval instances with status
- Create new `/approvals/inbox` route with "My Pending Approvals" page
- Add approve/reject buttons with comments dialog on instance detail
- Add "Submit for Approval" button on ResourceDetailPage (when collection has approval processes)
- Add recall action for submitted approvals

**G10: Flow Executions**
- Add "Executions" tab/panel to FlowsPage
- Add "Run Flow" button per flow that triggers POST `/{id}/execute`
- Show execution detail with step status and timestamps
- Add cancel button for running executions

### Stream 3: Execution Logs (P2)

**G11, G12, G13: Log Viewers**
All three follow the same pattern — add an expandable "Execution Logs" section or tab:
- WorkflowRulesPage: global logs tab + per-rule expandable section
- ScheduledJobsPage: per-job expandable log section
- ScriptsPage: global logs tab + per-script expandable section

Each log viewer shows: timestamp, status (success/failure), duration, error message (if failed).

**G14: Webhook Deliveries**
- Add expandable delivery history per webhook
- Show: timestamp, HTTP status, response time, payload preview

**G16: Email Logs**
- Add "Delivery Log" tab to EmailTemplatesPage
- Show: timestamp, recipient, subject, status (sent/failed/bounced)

### Stream 4: Security & Sharing (P1-P2)

**G7: Permission Set User Assignment**
- Add "Assigned Users" section in PermissionSetsPage detail panel
- Add "Assign User" button with user search/select
- Add unassign action per user row

**G8: Record-Level Sharing**
- Add "Sharing" section to ResourceDetailPage
- Show current shares (users and groups with access level)
- Add "Share" button to share with user/group
- Add remove share action

**G4: Field History Viewer**
- CollectionDetailPage: Enhance field history tab to query actual history data
- ResourceDetailPage: Add "History" tab showing per-record field changes
- UserDetailPage: Add "Change History" tab showing user's field changes

### Stream 5: Enhancements (P3)

**G1: Collection Enhancements**
- G1.1: Add "Publish" button in versions tab header
- G1.2: Add "Relationships" tab showing LOOKUP/MASTER_DETAIL fields and their targets

**G17: Report Folders**
- Add folder sidebar/dropdown in ReportsPage
- Add create/delete folder actions

**G18: Layout Assignments**
- Add "Assignments" tab in PageLayoutsPage
- Show profile × record type → layout matrix
- Add assignment creation/editing

**G20: Server-Side Export**
- Replace client-side CSV generation with POST `/control/export/csv`
- Add "Export to Excel" option using POST `/control/export/xlsx`
- Show download progress for large exports

---

## Part 4: Effort Estimates

| Stream | Priority | Items | Complexity | Est. Tasks |
|--------|----------|-------|------------|------------|
| Stream 1: Collection Detail | P0 | G2, G3, G5 | Large | 8-10 |
| Stream 2: Approval & Flow | P1 | G9, G10 | Large | 6-8 |
| Stream 3: Execution Logs | P2 | G11-G14, G16 | Small each | 5-6 |
| Stream 4: Security & Sharing | P1-P2 | G4, G7, G8 | Medium | 5-6 |
| Stream 5: Enhancements | P3 | G1, G15, G17-G20 | Mixed | 6-8 |
| **Total** | | **~45 gaps** | | **~30-38 tasks** |

---

## Part 5: Summary Statistics

| Metric | Count |
|--------|-------|
| Total backend endpoints | ~200+ |
| Endpoints with full UI coverage | ~140 |
| Endpoints with partial UI coverage | ~15 |
| Endpoints with no UI coverage | ~50 |
| UI coverage percentage (full) | ~70% |
| UI coverage percentage (full + partial) | ~77% |
| Number of gaps identified | 45 |
| Number of implementation streams | 5 |
| Estimated tasks to reach 100% | ~30-38 |

### Gap Breakdown by Type
| Gap Type | Count | Examples |
|----------|-------|---------|
| Missing CRUD in existing tab | 13 | Validation rules, record types |
| Missing execution/log views | 10 | Workflow logs, script logs, webhook deliveries |
| Missing instance management | 8 | Approval instances, flow executions |
| Missing sub-feature | 8 | Picklist dependencies, layout assignments, user assignment |
| Missing data view | 4 | Field history, relationships, report folders |
| Missing export capability | 2 | Server-side CSV, XLSX |
