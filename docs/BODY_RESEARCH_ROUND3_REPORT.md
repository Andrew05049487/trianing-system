# Body Research Round 3 Backend Checkpoint / Handoff

Status: PARTIAL. Implementation/mock tests and local isolated V005/MySQL/Hibernate/API/concurrency
acceptance PASS. Pi/TV hardware E2E and release runtime remain pending. Do not start Round 4.

## Environment separation (2026-10-06)

- Local Windows MySQL 8.4.11, rehab_body_r3_validation: V001-V004 migration and actual metadata PASS (29 tables/253 columns).
- V005: subsequently applied by owner. Current actual metadata PASS: 29 tables/257 columns/34 FK; Hibernate/CRUD/concurrency PASS. Earlier pending status is historical, not current.
- Laboratory MySQL: NOT RUN / NOT VALIDATED. It is the actual future project database, not this local validation schema. Do not connect without explicit access/authorization.
- Production / Render: NOT DEPLOYED; no settings, collection flags or data changed.

## Implementation and contract

- v3 only: body-attempt-label-v1; expectedRevision required on label/submit/review. Existing legacy schema 1/2 calls retain optional revision compatibility.
- Existing endpoints reused. Review decision APPROVE/RETURN/REJECT/NEEDS_RESAMPLE, reasonCode and expectedRevision are additive request fields.
- APPROVE -> APPROVED/ACTIVE; RETURN -> RETURNED/ACTIVE; REJECT -> RETURNED/REJECTED; NEEDS_RESAMPLE -> RETURNED/NEEDS_RESAMPLE.
- Nonapprove note mandatory; rejection/resample reason mandatory. Approved or non-ACTIVE edits locked; independent reviewer/grants/binding remain required.
- v3 upload consent-row write lock and READ_COMMITTED serialize retries; v3 edits lock sample before first annotation insert. Actual local MySQL races PASS.
- Optional resampleOfSampleId links an immutable existing NEEDS_RESAMPLE parent with matching owner, exercise/type, current subject and unexpired consent context; new payload/attempt ID required.
- Export v3 requires explicit phone or tv_pi source. Only independently approved/ACTIVE/current-consent/nonexpired/non-DEMO/valid features and trainable labels included. Invalid JSON skipped. Hand/body/legacy schemas not pooled.
- Export manifest includes versions, source and pseudonymous subject/session/attempt grouping; direct IDs/auth/name/email omitted. Existing 500 approved-candidate/20MB bounds retained.

## Migration

- V001-V003 unchanged.
- V004 development-only disposition CHECK adds REJECTED, already present in owner's local setup. Do not replay.
- V005__body_review_resample.sql: sample resample_of_sample_id VARCHAR(36) nullable/self FK ON DELETE SET NULL/index; annotation reason_code VARCHAR(64); revision reason_code VARCHAR(64) and disposition VARCHAR(32), nullable.
- Measured after owner V005: MySQL 8.4.11, 29 tables/257 columns/34 FK. No migration replayed by agent.
- Application remains ddl-auto=validate. No Hibernate update/create fallback.

## Tests actually executed

- Focused 58/58 PASS:
  mvn -Dtest=ResearchBodyReviewTest,ResearchBodyAttemptTest,ResearchBodyMigrationTest,ResearchDataServiceTest,ResearchManagementServiceTest,ResearchHandContractTest,ResearchBodyExportTest test
- Full mvn test PASS: 328 discovered, 298 passed, 30 skipped, 0 failures/errors.
  Skips: 22 existing gated MySQL, 7 new BodyRound3MySqlIntegrationTest, 1 activation test. Skips are NOT RUN, not PASS.
- mvn package -DskipTests PASS. Compilation/package does not prove Hibernate validation.
- git diff --check PASS.
- Evidence: target/body-r3-full-maven.log, target/body-r3-package.log, target/surefire-reports/*.xml.
- H2 chat results are not MySQL evidence; no model training/clinical accuracy claimed.

Latest continuation results: local real MySQL 12/12 PASS, no skips. Full Maven regression
331 discovered/298 PASS/33 skipped/zero failures; package PASS. Full run intentionally has no DB_URL;
10 Body test methods skipped in that run, 12 invocations exercised in the separate actual MySQL run.
Earlier 328/298/30 results retained above as history. Detailed matrix: docs/BODY_RESEARCH_ROUND3_MYSQL_VALIDATION.md.

## Real MySQL tests (PASS locally)

BodyRound3MySqlIntegrationTest originally had seven cases; expanded twelve actual invocations now PASS: metadata/normal collection closed,
idempotency/conflict/unique attempt, v1 body/v2 hand nullable compatibility,
authenticated upload->independent review->source-specific export,
immutable linked resample lifecycle, revoked/unrelated authorization, concurrent retry/first draft.
Transactional cases rollback. One commits ephemeral synthetic fixtures, then finally cleans only its generated IDs.
Explicit HTTP 403/409, label/review/export routes, revision/audit SQL reads, legacy/current CRUD and all review dispositions added.
All 29 tables have exact zero row counts after tests. No clearing/recreation or auto-increment reset.
Test-only primary synthetic service permits fake data; normal service remains collection-disabled.
No table clear, production consent, manager bootstrap or formal retention policy.

## Exact files added/modified

- docs/database-rebuild/mysql/V004__body_attempt_research.sql
- docs/database-rebuild/mysql/V005__body_review_resample.sql (new)
- src/main/java/com/example/trainingsystems/controller/ResearchDataController.java
- src/main/java/com/example/trainingsystems/controller/ResearchManagementController.java
- src/main/java/com/example/trainingsystems/entity/ResearchAnnotationEntity.java
- src/main/java/com/example/trainingsystems/entity/ResearchAnnotationRevisionEntity.java
- src/main/java/com/example/trainingsystems/entity/ResearchSampleEntity.java
- src/main/java/com/example/trainingsystems/repository/ResearchConsentRepository.java
- src/main/java/com/example/trainingsystems/repository/ResearchSampleRepository.java
- src/main/java/com/example/trainingsystems/service/ResearchActionRegistry.java
- src/main/java/com/example/trainingsystems/service/ResearchBodyAttemptValidator.java
- src/main/java/com/example/trainingsystems/service/ResearchDataService.java
- src/main/java/com/example/trainingsystems/service/ResearchManagementService.java
- src/main/java/com/example/trainingsystems/service/ResearchTrainingFeatureValidator.java
- src/test/java/com/example/trainingsystems/service/ResearchBodyAttemptTest.java
- src/test/java/com/example/trainingsystems/service/ResearchBodyReviewTest.java (new)
- src/test/java/com/example/trainingsystems/service/ResearchBodyExportTest.java (new)
- src/test/java/com/example/trainingsystems/service/BodyRound3MySqlIntegrationTest.java (new)
- tools/body-round3-mysql-setup.ps1 (new; owner already used; DO NOT REPLAY)
- tools/body-round3-review-migration.ps1 (new)
- tools/body-round3-mysql-test.ps1 (new)
- docs/BODY_RESEARCH_ROUND3_PROGRESS.md (new)
- docs/BODY_RESEARCH_ROUND3_REPORT.md (new)
- docs/BODY_RESEARCH_ROUND3_MYSQL_VALIDATION.md (new continuation evidence)

## Safe continuation commands

Owner V005 is already complete; do not rerun any migrations/setup. To reproduce validation in local PowerShell:

    & 'C:\Users\kuoja\Documents\GitHub\trianing-system\tools\body-round3-mysql-test.ps1'

Runner forces localhost/rehab_body_r3_validation and 4/4-column preflight, imports existing
DPAPI app CRUD credential, generates temporary test secrets in process environment without printing,
restores previous environment in finally. No migrations run by test runner.
Measured schema/Hibernate/API/concurrency and fixture cleanup now PASS; runner safely reproduces the same tests.
If failures occur, fix the actual cause; never weaken assertions or enable Hibernate update.

## Branch / compatibility checkpoint

Existing main only. Round 2 backend checkpoint 45c7bbb0fc3b4aa09d22a212bf7b92c764e9f36c;
Flutter master 3e6d013184104898596e82e2a580006145c1b05d;
TV 2a0b10b95e95b5e7c29ff41f2815c258bf26d84d. Compatible Round 3 code committed locally in
each existing branch; actual SHA obtained with git rev-parse HEAD/final report.
No new branches, whole-branch merges, push or deployment.
Verified Round 3 implementation checkpoints: backend main 6048bf32e6bccb6ae694297e68a57a600d41157b;
master 1326472811298223182d009c252645e1da5a9610; TV 968d8a4be92c4a9696db7944001b4d187ce33ad4.
Subsequent documentation-only commit records these checkpoints; final HEAD is reported separately.
Complete twenty-section cross-repository report is Flutter docs/BODY_RESEARCH_ROUND3_REPORT.md.
Phone/TV Debug and Release PASS; master focused 61/61, TV 35/35, scoped analyze zero issues.
Full Flutter 545 PASS/7 inherited FAIL explicitly preserved. Pi/TV camera E2E and release runtime NOT RUN.
