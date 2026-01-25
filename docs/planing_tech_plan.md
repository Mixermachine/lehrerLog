# Lehrerlog Technical Plan

This document translates planing_draft.md into a structured technical plan. It is built in phases to keep scope and risk manageable.

## Phase 0: Foundations (Architecture + Constraints)

- Targets: Android, iOS, Desktop (JVM), Web (Wasm), Server (Ktor).
- Data layer: PostgreSQL (ACID) with Flyway migrations.
- Auth: JWT + refresh tokens; role-based access control.
- Files: S3-compatible object storage (MinIO) hosted alongside server.
- PDF viewing: use system viewer on mobile/desktop; browser native PDF display on Web.

Deliverables
- Tech decisions documented per feature area (data model, API, storage, UI, tests).
- Security baseline: authz checks per route and per file.

## Phase 1: Core Assignment + Submission Tracking (Server + Client)

### 1.1 Data Model (PostgreSQL)

New tables (proposal):
- assignments
  - id (UUID, PK)
  - school_id (UUID, FK)
  - created_by (UUID, FK -> users)
  - title, description
  - due_at (timestamp)
  - created_at, updated_at, version

- assignment_targets (many-to-many assignment -> students)
  - assignment_id (UUID, FK)
  - student_id (UUID, FK)
  - created_at
  - note: targets are a snapshot at assignment creation; new students joining later are not auto-added

- submissions
  - id (UUID, PK)
  - assignment_id (UUID, FK)
  - student_id (UUID, FK)
  - submitted_at (timestamp)
  - submission_type (enum: FILE, IN_PERSON)
  - grade (decimal, nullable)
  - note (text, nullable)
  - late_status (enum: ON_TIME, LATE_UNDECIDED, LATE_FORGIVEN, LATE_PUNISH)
  - late_period_id (UUID, FK -> late_periods, nullable)
  - decided_by (UUID, FK -> users, nullable)
  - decided_at (timestamp, nullable)
  - created_at, updated_at, version
  - unique (assignment_id, student_id)

- assignment_files
  - id (UUID, PK)
  - assignment_id (UUID, FK)
  - object_key (text)
  - size_bytes (bigint)
  - mime_type (text)
  - created_at

- submission_files (student uploads)
  - id (UUID, PK)
  - submission_id (UUID, FK)
  - object_key (text)
  - size_bytes (bigint)
  - mime_type (text)
  - created_at

- storage_plans
  - id (UUID, PK)
  - name
  - max_total_bytes (bigint)
  - max_file_bytes (bigint) // 5MB per submission
  - created_at

- storage_subscriptions
  - id (UUID, PK)
  - owner_type (enum: SCHOOL, TEACHER)
  - owner_id (UUID) // school_id or teacher user_id
  - plan_id (UUID, FK -> storage_plans)
  - active (bool)
  - starts_at, ends_at

- storage_usage
  - owner_type (enum: SCHOOL, TEACHER)
  - owner_id (UUID)
  - used_total_bytes (bigint)
  - updated_at

- teacher_late_policy
  - teacher_id (UUID, PK -> users)
  - threshold (int)
  - updated_at

- late_periods
  - id (UUID, PK)
  - teacher_id (UUID, FK -> users)
  - name
  - starts_at (timestamp)
  - ends_at (timestamp, nullable)
  - is_active (bool)
  - created_at

- student_late_stats
  - teacher_id (UUID)
  - student_id (UUID)
  - period_id (UUID, FK -> late_periods)
  - total_missed (int)
  - missed_since_punishment (int)
  - punishment_required (bool)
  - updated_at

- punishment_records
  - id (UUID, PK)
  - teacher_id (UUID, FK -> users)
  - student_id (UUID, FK)
  - period_id (UUID, FK -> late_periods)
  - triggered_at (timestamp)
  - resolved_at (timestamp, nullable)
  - resolved_by (UUID, FK -> users, nullable)
  - note (text, nullable)
  - created_at

Notes
- Use optimistic locking via version columns on mutable tables.
- Keep SyncLog/version fields on server (for future offline support).
- Seed storage_plans with default values (100MB total, 5MB per file).

### 1.2 API (Server)

Assignment CRUD
- POST /api/assignments
- GET /api/assignments?classId=...&studentId=...
- PUT /api/assignments/{id}
- DELETE /api/assignments/{id}

Targeting
- POST /api/assignments/{id}/targets (add/remove students)
- Class targeting expands to current members at creation time only (no retroactive auto-assign)

Submissions
- POST /api/assignments/{id}/submissions (grade/note optional)
- POST /api/assignments/{id}/submissions/in-person (grade/note optional)
- PATCH /api/submissions/{id} (grade/note/late-status)
- Duplicate submissions for the same assignment+student return 409 (already submitted)

Late periods
- GET /api/late-periods
- POST /api/late-periods
- PUT /api/late-periods/{id}
- POST /api/late-periods/{id}/activate
- POST /api/late-periods/{id}/recalculate
- Enforce exactly one active period per teacher (activate should deactivate any previous active period)
- If no active period exists, auto-create an open-ended period and set active
- Return late counters per active period

Quota
- GET /api/storage/quota (resolved owner_type + plan + remaining bytes)
- GET /api/storage/usage (resolved owner_type + used_total_bytes)

PDF/File
- POST /api/assignments/{id}/files (upload assignment PDF)
- POST /api/assignments/{id}/submissions/{submissionId}/files (upload student file)
- GET /api/files/{id} (download/stream)
  - must resolve whether file is assignment_files or submission_files
  - enforce access rules accordingly (teacher vs parent)

Authz
- Teachers: create/update assignments, upload files, mark submissions.
- Parents (later): read-only access to their child's assignments/submissions.

### 1.3 Storage + Security

Primary approach (recommended)
- Server proxies file access:
  - Upload via server to MinIO.
  - Download via server streaming; no public URLs.
  - Authz check on every download by user role + school + assignment target.
- Pros: no shared URLs, tighter access control, consistent auditing.
- Cons: higher server bandwidth usage.

Operational safeguards
- Enforce max upload size at HTTP and storage layers.
- Configure read/write timeouts for file streaming.
- Audit logging for file access (file_id, user_id, action, timestamp) in logs or DB.

Alternative
- Signed, short-lived URLs generated by server; not stored in client.
- Pros: lower server bandwidth.
- Cons: URL can be shared within expiration window.

### 1.4 Quota + Limits

- Enforce total byte cap at teacher or school level (subscription).
- Resolve owner by precedence:
  - If teacher subscription active, use teacher plan.
  - Else fall back to school subscription.
- Store used bytes in DB; increment/decrement on upload/delete.

Upload enforcement plan
- Preflight check in UI using GET /api/storage/quota.
- On upload:
  - lock storage_usage row (SELECT ... FOR UPDATE)
  - verify remaining bytes
  - reserve bytes, then stream upload
  - on failure, roll back reservation
- Errors:
  - 413 FILE_TOO_LARGE (per-file limit)
  - 409 QUOTA_EXCEEDED (total limit) with remaining bytes in response

Proposal
- Internal quota tracking in DB (no external library).
- Alternative: use external rate-limit for request volume only; still track storage in DB.

### 1.5 Client UX

- Assignments list by class and by student.
- Assignment detail shows due date, status (late/on-time), and files.
- Assignment creation does not require a PDF; files can be attached later.
- Submit flow:
  - Upload file (limit check) OR mark in-person submission
  - Grade/note can be entered in the same step, and edited later
- Quota errors must show a clear message and action to resolve (upgrade plan or delete files).

### 1.6 Class + Student Management

API
- Classes:
  - GET /api/classes
  - POST /api/classes
  - PUT /api/classes/{id} (rename)
  - DELETE /api/classes/{id}
- Students:
  - GET /api/students?classId=...
  - POST /api/students
  - PUT /api/students/{id}
  - DELETE /api/students/{id}
- Movement:
  - POST /api/classes/{id}/students/{studentId} (move/add to class)
  - DELETE /api/classes/{id}/students/{studentId} (remove from class)

UI
- Class list with rename and delete actions.
- Class detail shows students with move and delete actions.
- Student profile shows assignments and late stats.

Notes\r
- Moving a student does not change existing assignment targets (snapshot remains).\r
- Prefer soft-delete for students to preserve submissions and stats.\r
- Classes are soft-deleted (archived) to preserve history and stats.

## Phase 2: Late Logic + Punishment Tracking

### 2.1 Late Calculation

Proposal
- late_status = ON_TIME when submitted_at <= due_at
- late_status = LATE_UNDECIDED when submitted_at > due_at and teacher has not decided
- Teacher decides per pupil: LATE_UNDECIDED -> LATE_FORGIVEN or LATE_PUNISH
- late_period_id is set when a late decision is made, using the active period

### 2.2 Periods + Recalculation

- Periods are defined per teacher via checkpoint dates.
- When a period is activated, new late decisions use that period.
- If a period is edited (dates changed), recalculate affected counts for that teacher.
- Recalculation should run as a background job and update student_late_stats for that period.

### 2.3 Punishment Policy

Proposal
- Maintain teacher-owned policy:
  - threshold (e.g., every 3 missed assignments)
  - per-student counters and punishment state

Logic
- If late_status == LATE_PUNISH:
  - total_missed += 1
  - missed_since_punishment += 1
- If missed_since_punishment >= threshold => punishment_required = true

### 2.4 Punishment Resolution

- When punishment_required becomes true, create a punishment_record with triggered_at.
- Teacher resolves via API; on resolve:
  - punishment_required = false
  - missed_since_punishment = 0
  - set resolved_at/resolved_by on punishment_record

Endpoints
- POST /api/punishments/resolve (studentId, periodId, note)
- GET /api/punishments?studentId=...&periodId=...

### 2.5 Late Stats Reporting (API)

- GET /api/late-stats/periods (list periods with totals)
- GET /api/late-stats/students?periodId=... (per-student counts)
- GET /api/students/{id}/late-stats?periodId=...

### 2.6 Reporting UI Scope\r

- Class dashboard: bar chart of late counts per student (period selector).\r
- Student profile: total_missed, missed_since_punishment, punishment_required.\r
- Period summary: line chart for missed counts across periods; summary tiles for totals.\r
- Optional: stacked bar to show forgiven vs punish breakdown per student.

Alternative
- School-wide counters instead of per-teacher.
- Pros: simpler; cons: ignores teacher-specific policies.

## Phase 3: Parents Mode

### 3.1 Data Model Additions

- Users.role: add PARENT
- parent_student_links
  - id (UUID, PK)
  - parent_user_id (UUID, FK -> users)
  - student_id (UUID, FK)
  - status (enum: PENDING, ACTIVE, REVOKED)
  - created_by (UUID, FK -> users)
  - created_at
  - revoked_at (timestamp, nullable)

- parent_invites
  - id (UUID, PK)
  - student_id (UUID, FK)
  - code_hash (text)
  - expires_at (timestamp)
  - created_by (UUID, FK -> users)
  - used_by (UUID, FK -> users, nullable)
  - used_at (timestamp, nullable)
  - status (enum: ACTIVE, USED, EXPIRED, REVOKED)

Notes
- Parent accounts can link to students across multiple schools.
- Do not rely on users.school_id for parent auth; enforce via parent_student_links.
- A parent can link to multiple students.
- Parents are read-only; no create/update actions outside invite redemption.

### 3.2 API

Teacher/Admin
- POST /api/parent-invites (studentId, expiresAt)
- GET /api/parent-links?studentId=...
- POST /api/parent-links/{id}/revoke

Parent
- POST /api/parent-invites/redeem (code)
- GET /api/parent/students
- GET /api/parent/assignments?studentId=...
- GET /api/parent/submissions?studentId=...

Authz
- Parent access limited to their linked students.
- No class roster access, no other student details.

### 3.3 UI + Flows (Parent App)

Invite and onboarding
- Parent enters invite code and sees the linked student + school before confirming.
- If code expired/used, show clear error and guidance to request a new invite.

Parent home
- List linked students with quick stats (open assignments, overdue count).
- Student switcher at top; per-student views are isolated.

Assignments
- Assignment list filtered to the selected student.
- Status labels: due, overdue, submitted, late (read-only).
- Detail view: show description + assignment files (PDF view in browser/system viewer).

Submissions
- Show submitted_at, late_status, grade, and note (read-only).
- If submission file exists, allow view/download via server proxy endpoint.

Account
- Read-only profile + linked students list.
- No class roster, no other students, no grading/editing.

### 3.4 UI + Flows (Teacher/Admin)

- Generate invite codes per student with optional expiry.
- View active parent links per student and revoke if needed.
- Audit list of active parents for a class (optional, admin-only).

### 3.5 UX Guardrails

- Parent role can never navigate into teacher-only routes.
- All parent API calls require parent role + link validation.

### 3.6 Defaults (Initial Behavior)

- Invite expiry default: 7 days (configurable per invite).
- Only one active invite per student; creating a new invite revokes the previous active invite.
- Redeem creates or links the parent account and sets link status ACTIVE immediately.
- Parent link is permanent for now; only teacher/admin can revoke.
- Multiple parents per student are allowed; parents can link to multiple students.
- Grades are visible to parents when present (default; can be restricted later).
- Parent access to files: assignment files and submission files for linked students only.
- Notifications: no email/push notifications in Phase 3 (future work).

## Phase 4: Testing Strategy

### 4.1 Scope\r

- Every feature includes:\r
  - Unit tests (service/repository logic)\r
  - API tests (Ktor test host, end-to-end)\r
  - Compose UI tests (ExperimentalTestApi)\r
  - Roborazzi snapshots for stable UI states\r
- Every new UI element must have explicit UI coverage (Compose UI test or snapshot).

### 4.2 Prioritization

- P0: Auth, assignment CRUD, submission, file upload, quota enforcement.
- P1: Late logic and punishment tracking.
- P2: Parent mode.

### 4.3 Fixtures

- All mock data from SharedTestFixtures.kt.
- Build fixtures per role: teacher, parent, student, school.
- Include quota fixtures (teacher plan, school plan) and late-policy fixtures.

## Phase 5: Delivery Plan

- Phase 1 first: assignments + submissions + file upload.
- Phase 2: late logic + punishment UI.
- Phase 3: parents mode.
- Phase 4: harden tests + snapshot coverage.

---

This plan will be refined as requirements harden and schemas are agreed.



