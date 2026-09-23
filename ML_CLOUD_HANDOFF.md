# ML Cloud Backend Handoff

## Git and companion

- Backend branch/base: `feat/rehab-ml-cloud-label` / `94132993d7d32ffc4b090ec429975fa3543275f5`; stage commit pending.
- Flutter companion branch/base: `feat/rehab-ml-poc` / `ad1f0bf13b3ebb6828aa69cb9f62b8bd7cfee546`.
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
- Next: commit backend stage, then Flutter consent/sync/therapist UI using exact API version; record paired commit SHAs.
- Later: research-manager authority, approval/review, approved export, management stats and retention automation. No manager authority exists today, so do not expose broad endpoints.
- Withdrawal stops uploads and therapist access. `DELETE /my-data` deletes live rows; backup retention/deletion is not proven and requires policy.

## Tests/errors

- Focused tests: ResearchDataServiceTest 12/12 and AccountServiceTest 14/14 passed. `mvn -q -DskipTests package` passed. Full backend suite, SQL Server/Render and Android E2E not run at this stage.
- No genuine labeled data/trained classifier or ethics approval. Do not collect real subjects until governance review.
