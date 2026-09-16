# Kiro Usage Report

**Report Period: August 1, 2026 to August 31, 2026**

> Generated on 2026-09-16. The reporting period is the previous calendar month (August 2026), derived automatically from the current date.
>
> **Evidence base:** Git history (`git log`), spec artifacts under `.kiro/specs/`, spec execution metadata (`tasks.meta.json`), and workspace source files. All estimates are explicitly labeled. Where a metric cannot be established from workspace evidence, it is marked *Not determinable from available evidence*.

---

## Executive Summary

1. **No verifiable Kiro spec-execution activity occurred within the August 2026 reporting period.** The only two Kiro specs in the workspace (`owner-email`, `transaction-page`) both recorded their task executions in **September 2026** according to their `tasks.meta.json` execution timestamps (transaction-page: Sep 4–7; owner-email: Sep 16). Therefore, they are reported as **historical/baseline context**, not August monthly activity.

2. **August was a high-volume general-development month, but Kiro attribution cannot be confirmed.** The August window contains 55 commits and roughly 61,000 raw line additions across ~233 new Java files, ~85 TypeScript files, and ~18 SQL files. None of these carry Kiro session/execution metadata, so per the reporting rules they are **not** claimed as Kiro-generated. They represent overall project development (payment module, vouchers, vendors, deployment, NOC, suspense/outstanding).

3. **Spec-driven development is real and well-structured, but it started after the reporting period.** Both specs follow a rigorous requirements → design → tasks workflow with property-based test coverage (jqwik). This is strong evidence of mature Kiro usage — just not in August.

4. **Quantitative Kiro productivity/velocity/defect metrics for August are largely not determinable** because no Kiro-attributable changeset falls inside the window. Figures that would otherwise be fabricated are explicitly flagged rather than estimated.

---

## Module-wise Breakdown

### August 2026 (reporting period) — general development, Kiro attribution unconfirmed

New files added during the window (from `git log --diff-filter=A`, excluding `node_modules`):

| Artifact Type | New Files (Aug) | Kiro-Attributable? |
|---|---|---|
| Java (`.java`) | 233 | Unconfirmed — no Kiro metadata |
| TypeScript (`.ts`) | 85 | Unconfirmed |
| SQL (`.sql`) | 18 | Unconfirmed |
| Markdown (`.md`) | 6 | Unconfirmed |
| JSON / YAML / config | ~11 | Unconfirmed |

- **Raw line additions in window (all authors, all sources):** ~61,036 insertions across the 55 commits. This is a raw git figure and includes generated/vendored content; it is **not** an estimate of hand-written or Kiro-generated code.
- **Module themes visible in August commit messages (confirmed facts):** payment module completion, voucher approval/workflow/TDS, vendor create + category refactor, outstanding & suspense module, NOC, tenant upgrade, deployment/nginx/DB backup, landing page.
- **Per-module estimated Kiro LoC:** *Not determinable from available evidence* — no August changeset is linked to a Kiro session.

### Historical/baseline context (September 2026, outside reporting period)

| Module | Spec | Artifact Focus | Complexity |
|---|---|---|---|
| Owner (email) | `owner-email` | Controller, Service, Template builder, DTOs, enums, Angular component, permission seeding, property tests | Medium–High |
| Transaction | `transaction-page` | Controller, Service, Specification builder, Mapper, DTOs, access-scope resolver, Angular feature, property tests | Medium–High |

---

## Spec Activity

Two specs exist under `.kiro/specs/`. Both were **committed** to git on 2026-09-05 (commit `3d5a553`) and **executed** in September 2026 per their execution metadata. **Neither falls within the August reporting period.**

### `owner-email` (historical/baseline — executed 2026-09-16)
- **Feature/module targeted:** Owner Email (send templated emails to owners).
- **Workflow:** requirements-first; requirements.md, design.md, tasks.md all present.
- **Tasks defined vs completed:** 10 top-level task groups. Core implementation tasks 1–5 and 7 are marked complete (`[x]`); task 6 (backend checkpoint) is in-progress (`[-]`); task 8 (frontend composition view) is partially done (`[~]`, 8.1–8.3 complete, 8.4/8.5 optional tests pending); task 9 (integration tests) complete; task 10 (final checkpoint) not started.
- **Files created/modified (per task descriptions and code presence):** `RecipientScope`, `NotEmailedReason`, `SendOwnerEmailRequest`, `NotEmailedEntry`, `SendReportDTO`, `OwnerEmailTemplateBuilder`, `OwnerEmailService`/`Impl`, `OwnerEmailController`, `data.sql` + `DataInitializer` permission seeding, frontend `owner.model.ts`, `OwnerService.sendOwnerEmail`, `OwnerEmailComponent`, `owner.routes.ts`, `owner-list.component.ts`.
- **Stalled?** Not stalled in the reporting period (all activity is September). As of latest state it is near-complete with the final checkpoint (task 10) outstanding.
- **Last completed task:** Task 9 (Integration and wiring tests) and its sub-tasks, per `tasks.md` markers.
- **Execution evidence:** 69 recorded executions in `tasks.meta.json`, all dated 2026-09-16 06:39–08:51.

### `transaction-page` (historical/baseline — executed 2026-09-04 to 2026-09-07)
- **Feature/module targeted:** Read-only Transaction Page (query existing `MaintenancePayment` records with role-scoped access).
- **Tasks defined vs completed:** 14 top-level task groups; `tasks.md` shows **all** marked complete (`[x]`), including final checkpoint (task 14).
- **Files created/modified (per task descriptions):** `TransactionFilterRequest`, `TransactionSummaryDTO`, `TransactionDetailDTO`, `AccessScope`, `TransactionMapper`, `AccessScopeResolver`, `TransactionSpecificationBuilder`, `TransactionService`, `TransactionController`, `GlobalExceptionHandler` (403 mapping), `MaintenancePaymentRepository` (JpaSpecificationExecutor), frontend `transaction.models.ts`, `transaction.service.ts`, filter/detail/list components, `app.routes.ts`.
- **Stalled?** No — appears fully completed.
- **Execution evidence:** 109 recorded executions in `tasks.meta.json` dated 2026-09-04 to 2026-09-07; 4 property tests recorded `passed`.

**August spec activity:** None. *Not determinable that any spec task executed in August* — the metadata places all executions in September.

---

## Code Generation & Scaffolding Volume

- **August (reporting period):** ~342 total new source files added in-window (233 Java + 85 TS + 18 SQL + 6 MD, plus config). Raw insertions ~61,036 lines across 55 commits.
  - **Kiro-generated portion:** *Not determinable from available evidence.* No August file or commit is tied to a Kiro session, and the reporting rules forbid claiming Kiro generation solely because a file exists.
- **Historical/baseline (September specs):** Files listed under Spec Activity above were produced through Kiro spec execution (supported by `tasks.meta.json`). Estimated generated volume for these specs is **not** counted toward August.

**Estimated LoC generated by Kiro in August:** *Not determinable from available evidence.*

---

## Velocity Improvement

Because no Kiro-attributable work falls within August, a defensible before/after velocity comparison for the reporting period cannot be computed from evidence.

| Metric | August (reporting period) | Basis |
|---|---|---|
| Kiro-assisted artifacts | Not determinable | No August Kiro metadata |
| Estimated manual effort | Not determinable | — |
| Estimated Kiro-assisted effort | Not determinable | — |
| Estimated time saved | Not determinable | — |
| Velocity multiplier | Not determinable | Would be fabricated if stated |

**AI-Augmented Sizing matrix (applied to September baseline specs, for context only):**

| Complexity | Example artifacts present | Count (baseline, both specs) | Note |
|---|---|---|---|
| Low | DTOs, enums, mappers, TS models | ~15+ | Estimate from task descriptions |
| Medium | DDL/permission seeding in `data.sql`, specification builder, access-scope resolver | ~4–6 | Estimate |
| High | Best-effort mail send with per-recipient failure isolation, role-scoped query composition | ~2–3 | Estimate |

Target multiplier for new modules is 3x–4x; **not asserted for August** because the calculation would not be evidence-based.

---

## Quality Scorecard

- **Known defects tied to Kiro-generated code in August:** *Not determinable from available evidence.* August commits include general fix commits ("error fixed", "collate issue fixed", "fix payer name") but none are linked to Kiro-generated code.
- **Defect density `(known defects / estimated Kiro LoC) × 1000`:** Cannot be computed — both numerator (Kiro-linked defects) and denominator (Kiro LoC in August) are not determinable.
- **Security vulnerabilities (ARNICA or equivalent):** No ARNICA or static-analysis report artifacts found in the workspace. *Not determinable from available evidence.* Target of zero High/Critical cannot be evidentially confirmed or refuted.
- **Rework cycles:** The August vendor category work shows an iterative sequence (string → categoryId refactor across ~9 commits on 2026-08-29), but this is standard development iteration, not confirmed Kiro rework.
- **Test coverage (baseline context):** The September specs define extensive jqwik property tests; `transaction-page` recorded 4 property tests as `passed` in metadata.

---

## Documentation & Steering Utilization

### Documentation (August, confirmed by git)
Markdown docs added/modified in the August window (excluding `node_modules`):
- `README.md`
- `BRD_Society_Management.md` (Business Requirements Document)
- `DEPLOYMENT_GUIDE_AUG12.md`
- `DEPLOYMENT_SUSPENSE_FEATURE.md`
- `DEPLOYMENT-MEMBER-PORTAL.md`
- `NGINX_LANDING_PAGE_SETUP.md`

These are project/deployment docs. **Kiro authorship is unconfirmed** — no metadata links them to Kiro.

### Design artifacts (baseline context)
Each spec contains a structured `design.md` and `requirements.md` (EARS-style acceptance criteria seen in `owner-email/requirements.md`), plus a task dependency graph (JSON `waves`) in each `tasks.md`. Coverage includes class relations, DTO/entity mappings, exception handling (403 mapping in transaction-page), and interaction flows (recipient resolution, template assembly). These were created around the September spec work.

### Steering
- **`.kiro/steering/` does not exist** in this workspace. There are **no active steering files**.
- **Reusable prompt patterns / skills:** None found. No steering, skills, or prompt library artifacts are present. *No active steering usage — confirmed absent, not merely unobserved.*

---

## Database & Error Coverage

### DDL / SQL scripts (present in workspace, `backend/src/main/resources/db/`)
- `schema.sql`, `data.sql`, `migration_voucher_workflow_permissions.sql`, `flush_maintenance_data.sql`, `truncate_owners_units.sql`, plus a root `_reset_payments.sql`.
- 18 `.sql` files were **added** during August per git. Kiro attribution unconfirmed.
- **Kiro-tied DDL in the reporting period:** *Not determinable.* The `owner-email` spec's permission-seeding change to `data.sql` (task 5.1) executed in **September**, not August.
- **Validation of relationships/FKs/constraints/indexes:** Not independently validated for this report (the report is read-only and evidence-based); no migration-consistency fixes are attributable to Kiro within August.

### Error scenario & edge-case coverage (baseline context, from spec requirements)
The `owner-email` requirements explicitly handle strong edge cases:
- Validation failures (empty/whitespace/over-length subject and body — server + client).
- Missing email addresses classified as `MISSING_EMAIL`; per-recipient `SEND_FAILURE` isolation (one failure does not halt others).
- Mail transport absent → graceful `MAIL_NOT_CONFIGURED` report, zero errors propagated.
- 30-second frontend timeout with retry; authentication (401) / authorization (403) rejection with audit logging.

`transaction-page` handles invalid date ranges, enum bind failures, unknown unit IDs, access-scope denial (403), and not-found (404).

**Missing/observed gaps:** No circuit-breaker or upstream-API retry logic is described (these features are internal, not gateway-facing, so likely out of scope). No BRD cross-reference automation is present beyond the standalone `BRD_Society_Management.md`.

---

## Prompt-to-PR Lifecycle

- **August:** No Kiro prompt-to-PR lifecycle is traceable within the window. Git shows commits but no Kiro prompt/session linkage, and no PR metadata (e.g., `gh` PR artifacts) is present in the workspace. *Not determinable from available evidence.*
- **Baseline context (September):**
  - `owner-email`: spec creation → 69 recorded task executions on 2026-09-16 across multiple chat sessions (distinct `chatSessionId` values per task group in `tasks.meta.json`) → committed with `3d5a553` (2026-09-05 spec files) and subsequent work.
  - `transaction-page`: 109 recorded executions (2026-09-04→07) → commit `3d5a553` "Transaction feature completed" and `3224386` "transaction module test spec completed".
- **Iterative prompt cycles:** The metadata records execution events per task (typically 2 timestamps per sub-task: start/complete), not distinct prompt-retry counts, so exact prompt-cycle counts are **not fabricated** here.
- **Files modified per feature:** Enumerated per task in each `tasks.md` (see Spec Activity).

---

## Financial Efficiency

- **Token consumption / prompt complexity:** No token-level or session-cost telemetry exists in the workspace. **This metric cannot be reliably calculated.**
- **Tokens per useful line (target 12–20):** *Not determinable from available evidence.*
- **Tasks exceeding 5 prompt cycles (optimization candidates):** Cannot be identified — the metadata records execution events, not prompt-retry counts, so no task can be evidentially flagged.

---

## Recommendations

1. **Align reporting cadence with actual Kiro activity.** The substantive Kiro spec work happened in early-to-mid September. A September report will capture the `transaction-page` and `owner-email` specs as genuine monthly activity with strong evidence; August has none to report.
2. **Add steering files.** `.kiro/steering/` is absent. Introduce steering for backend conventions (Spring module layout, DTO/`ApiResponse` envelope, jqwik property-test norms) and frontend conventions (Angular standalone components, permission-gating) to make Kiro output more consistent and reduce rework.
3. **Preserve Kiro-attribution evidence.** Adopt a lightweight convention (e.g., commit trailers like `Kiro-Spec: owner-email` or referencing spec task IDs in commit messages) so future reports can attribute LoC, defects, and velocity to Kiro with hard evidence rather than "not determinable".
4. **Complete the open specs.** `owner-email` still has its final checkpoint (task 10) and optional test tasks (2.6, 8.4, 8.5) open; close these to reach a clean, fully-verified state before the next reporting cycle.
5. **Introduce static analysis reporting (ARNICA or equivalent).** No security-scan artifacts exist, so the zero High/Critical target is unverifiable. Wire a scanner into CI and archive its report so the Quality Scorecard becomes evidence-backed.
6. **Capture token/session telemetry** if financial-efficiency tracking is a goal; today it is unmeasurable from workspace artifacts.

---

### Evidence Appendix (confirmed facts)

- Reporting window derived: previous calendar month of 2026-09-16 = **2026-08-01 to 2026-08-31**.
- August commits: **55** (`git log --since=2026-08-01 --until=2026-09-01`).
- August raw insertions: **~61,036** lines (git numstat sum; includes non-authored/generated content).
- August new files: **233 .java, 85 .ts, 18 .sql, 6 .md** (+ config), via `--diff-filter=A`.
- Specs present: `owner-email`, `transaction-page` (both committed 2026-09-05, `3d5a553`).
- `owner-email` executions: **69**, all **2026-09-16** (out of window).
- `transaction-page` executions: **109**, **2026-09-04 to 2026-09-07** (out of window); 4 property tests `passed`.
- `.kiro/steering/`: **does not exist**.
- Security-scan (ARNICA) artifacts: **none found**.
- Token/session telemetry: **none found**.
