# ML Cloud Backend Handoff

## Third-round final validation checkpoint (2026-09-23)

- Current backend branch/HEAD `feat/rehab-ml-cloud-label` / `e905fe6`; Flutter companion `feat/rehab-ml-poc` / `13e954a` before final docs checkpoint. All implementation checkpoints committed; no push/merge/PR/deploy/production SQL.
- Full `mvn test` ONCE: 229 tests, 227 PASS, two FAIL: `LegacyBindingControllerSecurityTest.legacyEndpointCannotCreateTherapistBindingWithoutHmac` (expected 403, got 404) and `UserJsonSecurityTest.userJsonNeverExposesPasswordHashOrGoogleSubject` (expected false, got true). Auth/security code/tests were untouched by this third-round diff; do not cross-scope patch them here. `mvn package -DskipTests` PASS.
- No SQL Server test DB/Render deployment or formal production migration. Required manual order: second-round base research SQL, then authority, review, export, retention scripts; controlled first-manager bootstrap only after explicit account verification, backup and approval. `spring.jpa.hibernate.ddl-auto=update` does not replace review of explicit SQL scripts. No formal retention duration has been configured; default collection flag remains false.
- Frontend full `flutter test` 436 PASS/7 FAIL, debug APK PASS. Real Android/device integration and actual database/API E2E NOT RUN. Do not claim system ready for real participants.

## Third-round final security follow-up (2026-09-23)

- Reviewer-request eligibility now also requires an existing per-study annotation grant, not merely therapist role and an active binding. A manager still must approve; UI switch alone never grants permission.
- Export revalidates stored historical samples without the *new upload* one-year recency limit, while preserving shape/confidence/feature checks. This prevents policy-approved older samples from being incorrectly rejected solely because time passed. Expired, policy-less, withdrawn, unreviewed or unassessable samples remain excluded.
- Focused `ResearchAuthorityServiceTest,ResearchManagementServiceTest,ResearchDataServiceTest` PASS. Final full-suite/package/SQL Server/Render validation pending. Flutter companion shared-login `13e954a`; backend Stage E `59e1efd` plus this follow-up pending commit.

## Third-round start (2026-09-23)

- Backend local branch `feat/rehab-ml-cloud-label` at `8d743794e4d14d9ed914226e26ce090dfc9a7662`, clean. Flutter `feat/rehab-ml-poc` at `0b25cc52c29b6ed7da4c5fb092212a6b48e00fc5`, clean.
- `User.role` remains one string (PATIENT/THERAPIST); `/api/auth/login` already returns role and HMAC token. Add per-study research grants without replacing business role. Google remains patient-only.
- Next: controlled first-manager authorization, reviewer grants, review/export/retention; small tests and commits. No push/merge/deploy/production SQL.

## Third-round Stage B checkpoint (2026-09-23)

- Added additive per-study `research_grants`, `research_review_requests`, and `research_grant_audit` models, repositories, API and explicit SQL Server migration. Existing PATIENT/THERAPIST role and HMAC identity stay unchanged.
- `ResearchAuthorityService` gates annotation, review and management; bound therapists may request review, while an existing manager must decide. Self-approval and removal of the last manager are rejected. Therapist sample access now also requires a research grant.
- First manager requires a controlled, manual `sqlserver_bootstrap_first_research_manager.sql` with an explicitly verified existing user ID. Neither script has been run. No public bootstrap endpoint or built-in password exists.
- Focused validation: `mvn -q '-Dtest=ResearchAuthorityServiceTest,ResearchDataServiceTest,AccountServiceTest' test` PASS. SQL Server, full suite, deployment and real-user acceptance NOT RUN.
- Next: Stage C annotation draft/submit/review, Flutter reviewer UI, then export/retention and unified login. Do not redo Stage B or assume either SQL script is deployed.

## Third-round Stage C backend checkpoint (2026-09-23)

- `PUT /api/ml-research/samples/{id}/label` now saves DRAFT; `POST .../label/submit` moves it to SUBMITTED; `GET /api/ml-research/review-queue` lists bound/currently-consented submitted samples; `POST .../label/review` accepts `{approve, note}` and changes to APPROVED or RETURNED. Review requires separate reviewer grant, active patient binding/consent, and a different account than the annotator. Returned labels can be edited/resubmitted; submitted/approved labels are locked. `unassessable` remains a distinct label and must never enter training export.
- Each annotation/review mutation creates an immutable snapshot in `research_annotation_revisions`; the current annotation row includes revision, reviewer, timestamps and return note. Legacy LABELED rows are migrated to DRAFT, never approved automatically.
- Required additive SQL script: `sqlserver_migration_ml_research_review.sql`, AFTER the second-round base research migration. No migration has been run. Backend and Flutter must be deployed compatibly; old Flutter can still save a draft but cannot submit it.
- Focused backend tests: `mvn -q '-Dtest=ResearchDataServiceTest,ResearchAuthorityServiceTest' test` PASS. Full suite, SQL Server, Render and Android E2E NOT RUN. Next: Flutter submit/review UI; then export/retention, unified login.

## Third-round Stage D backend checkpoint (2026-09-23)

- `GET /api/ml-research/management/stats` and `/export` require an authenticated per-study manager grant. Export returns a bounded no-store ZIP in memory (`samples/*.json`, `labels.csv`, `manifest.json`) matching `ml/train.py` and never writes a server file. It includes only current-consent, still-present, independently APPROVED, trainable-label samples; `unassessable` is excluded. The stored sample is re-sanitized and five features are checked against `ml/feature_schema.py` math before export. Annotator IDs are export-local aliases, not account IDs. A scoped export audit stores actor/time/study/schema/count, not payload.
- New additive `sqlserver_migration_ml_research_export.sql` creates export audit table; not executed. Export is capped at 500 approved rows / 20 MB uncompressed to avoid unbounded Render memory. Larger datasets need a reviewed batch mechanism, not silent truncation.
- Focused `mvn -q '-Dtest=ResearchManagementServiceTest' test` PASS. No real labeled dataset, model training, SQL Server, Render or Android E2E. Next: Flutter minimal manager UI, retention and unified login.

## Third-round Stage E retention checkpoint (2026-09-23)

- Added immutable per-study retention policy versions (`retentionDays`, `effectiveAt`, approval reference, setter, action DELETE) and deletion-event records. A daily UTC 03:00 batch and manager-triggered batch process up to 100 expired samples transactionally. Re-running after deletion finds no rows. Patient/account deletion records a distinct reason; consent withdrawal remains distinct in existing research audit.
- On upload, the active policy version and calculated expiry are stored per sample. No policy means backend research consent/upload is unavailable even if collection flag is set; policy creation alone does not enable collection. Existing legacy samples with no expiry are not silently made permanent or deleted; they need a reviewed disposition. Export excludes expired or policy-less samples.
- New additive `sqlserver_migration_ml_research_retention.sql`; no duration is inserted by migration. It has NOT been run. No SQL Server/Render/Android validation or formal governance approval.
- Focused `mvn -q '-Dtest=ResearchRetentionServiceTest,ResearchManagementServiceTest,ResearchDataServiceTest' test` PASS. Next: complete Flutter retention UI tests, then shared login. Backup/export copies cannot be instantly revoked by main-DB deletion; governance procedure remains required.

## Git and companion

- Backend branch/base: `feat/rehab-ml-cloud-label` / `94132993d7d32ffc4b090ec429975fa3543275f5`; stage commit `f6caf96781884f5809222396cdc834128da63bc2`.
- Flutter companion branch/base: `feat/rehab-ml-poc` / `ad1f0bf13b3ebb6828aa69cb9f62b8bd7cfee546`; compatible functionality at `d7b8015127e78ecf5b9b16e87ccd199b72a3b912`.
- Check both working trees and latest commits before continuing. Do not push, merge or open PR automatically.

## Architecture and API

- Existing HMAC identity: `CustomExerciseIdentityService` validates a DB-reloaded `User` against `X-User-Id` and `X-Custom-Exercise-Token`.
- Therapist/patient authorization: `UserBindingRepository` with `relationship=THERAPIST`.
- Roles currently include PATIENT and THERAPIST; no proven research-manager role exists. Do not invent public admin access.
- SQL Server uses Spring JPA `ddl-auto=update`; reviewed additive `sqlserver_migration_ml_research.sql` must be manually applied before backend deployment; it has not been executed.
- All `/api/ml-research` routes require existing `X-User-Id` and `X-Custom-Exercise-Token` HMAC identity. Patient: `GET/PUT /consent` (`agree`, `version`), `POST/GET /samples`, `GET/DELETE /samples/{sampleId}`, `DELETE /my-data`. Bound therapist: paged sample list/detail and `PUT /samples/{sampleId}/label` (`label`, `note`, `labelVersion`, `actionDefinitionVersion`).
- Labels: `meets_requirement`, `insufficient_range`, `trunk_compensation`, `unassessable`. Preliminary single-label vocabulary needs physical-therapist approval. No rule-derived labels.
- Upload accepts schema-v1 standing-knee-raise JSON, validates 17 points/confidences/timestamps/features and strips unknown/personal fields. Server issues subject/sample UUIDs. Same client sample ID and same canonical content retries idempotently; conflict returns 409.
- Env names: `RESEARCH_COLLECTION_ENABLED` (false by default), `RESEARCH_CONSENT_VERSION` (blank by default). Neither should be enabled for real people before governance approval.
- Flutter sample JSON v1: 17 normalized body points, scores, angles, timestamps, five ordered features, consent-gated locally; no trained classifier exists.

## Completed / remaining

- Completed: secure minimal API, four research entities/repositories, validation, consent, sample upload/list/detail/delete, bound-therapist label, audit, account cleanup, SQL migration.
- Backend stage commit `f6caf96781884f5809222396cdc834128da63bc2`; Flutter patient consent/sync and therapist list/detail/label UI committed through `d7b8015`. Both repositories remain undeployed for this feature.
- Later: research-manager authority, approval/review, approved export, management stats and retention automation. No manager authority exists today, so do not expose broad endpoints.
- Withdrawal stops uploads and therapist access. `DELETE /my-data` deletes live rows; backup retention/deletion is not proven and requires policy.

## Tests/errors

- Focused tests: ResearchDataServiceTest 12/12 and AccountServiceTest 14/14 passed. `mvn -q -DskipTests package` passed. Full backend suite, SQL Server/Render and Android E2E not run at this stage.
- No genuine labeled data/trained classifier or ethics approval. Do not collect real subjects until governance review.

## Next continuation steps

1. Read Flutter handoff at `d7b8015`; do not recreate completed consent/sync/player/label UI.
2. After research governance approval, review/run migration manually, deploy compatible backend/Flutter, then Android E2E. Feature flag remains OFF by default.
3. Design explicit researcher authorization for review/export; add approval, training export, stats and retention with separate tests/commits.
# G3.5 checkpoint (2026-10-03)

Final local validation: FullTest via existing DPAPI application credential on localhost MySQL8.4.11/rehab_r2_validation: **258 PASS, 0 FAIL, 0 SKIP**, including 21 actual MySQL integration tests. Added synthetic second-action upload/dedup/detail/label/independent review/action-scoped export/withdrawal test (rollback). Final MockMvc consent 200/401/403/503 assertions rerun with MySQL suite: 21 PASS. Hibernate validate/29 entities passed. Metadata 29 tables, 241 columns (existing V003), 33 FK,16 CHECK,75 indexes (68declared+7automatic); no migration. Package PASS; diff-check PASS. Final synthetic accounts/samples/policies all 0. No production collection enabled.

Frontend detailed evidence: `docs/G35_VALIDATION.md`: full Flutter449PASS/7FAIL, all seven reproduced on master snapshot; scopedresearch26PASS/analyze0issues/Python8PASS/debugAPKPASS. Remaining manual Render/device validation and local master merge gate (pre-existing generated report needs owner direction). Backend remains feature branch, no merge/push/deploy. Last current commit must be verified with `git log -1` before continuation; next backend production integration is explicitly manual.

Stage B: ResearchActionRegistry dispatches per-action contracts and feature extraction over common 17-point payloads. Production standing only; SyntheticResearchContract exists only under src/test. Legacy schema1 missing actionDefinitionVersion retains original canonical hash; new samples carry version. Annotation vocabulary/version checked against payload; export optional actionId defaults standing and manifest carries version/features. No DB migration. Focused ResearchDataService/Management/Authority/Retention tests 36 PASS; compatible Flutter registry/storage/queue/UI tests 26 PASS and Python 8 PASS, scoped analyze clean. Actual MySQL/full suite/APK still pending. No Render configuration or formal collection enabled.

Branch `codex/g35-research-integration`, based on main `5394f735a461476ee692bf44350aafb0f6888648`. Stage A adds consent `unavailableReason` and distinct 503 reasons for closed collection, unset version, missing effective approved retention. Security gates unchanged. ResearchDataServiceTest + ResearchRetentionServiceTest: 23 PASS. Flutter compatible feature branch implements safe localized error parsing (18 focused tests PASS). No migration, Render or collection configuration changed. Next: action contracts, regression/build, actual local MySQL integration. Older SQL Server notes below are historical; R3 MySQL/validate remains authoritative.
