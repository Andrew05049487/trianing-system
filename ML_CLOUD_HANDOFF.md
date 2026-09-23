# ML Cloud Backend Handoff

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
