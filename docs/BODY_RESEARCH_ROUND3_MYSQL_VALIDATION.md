# Round 3 Local Isolated MySQL Validation — 2026-10-06

## Environment boundary

| Environment | Current result |
|---|---|
| Local Windows MySQL / rehab_body_r3_validation | Requested isolated validation COMPLETE / PASS |
| Laboratory MySQL / actual future project database | NOT VALIDATED; not connected |
| Production / Render | NOT DEPLOYED; no settings, research flags or data changed |

Owner applied V005; V001-V004 were not rerun. Agent ran no DDL. Local server 127.0.0.1:3306,
MySQL 8.4.11, application CRUD credential imported through existing DPAPI file.
Secrets never placed in source, SQL, documentation or command arguments.
Normal service collection-enabled=false. A test-only primary service permits synthetic fixtures inside the test context;
this is not formal study activation or an approved retention policy.

## Measured metadata / startup

- 29 tables, 257 columns, 34 FK, 29 mapped Hibernate entities.
- V005 preflight found all four added fields before tests.
- Spring Boot 3.2.0 / Hibernate 6.3.1 / Java 21 context started successfully with ddl-auto=validate.
- No Hibernate update/create/create-drop or migration replay.
- HTTP contract tests use the real Spring controller stack through MockMvc and actual MySQL repositories.
  They are not an external HTTP socket or Android/Pi/TV device E2E.

## Requested validation matrix

| Item | Actual result / evidence |
|---|---|
| Hibernate mapping | PASS: schema validation and 29 entity/table checks |
| v1 body CRUD | PASS: create/read; mutable annotation draft update/revision; patient delete |
| v2 hand CRUD | PASS: create/read, null v3 metadata; hand draft update; patient delete |
| v3 body CRUD | PASS: upload/detail, draft/submit/independent review, patient delete; skeleton payload remains immutable |
| Concurrent duplicate upload | PASS: two threads return same ID; exactly one sample |
| Concurrent first draft | PASS: one save, one revision HTTP-equivalent 409; no duplicate annotation insertion |
| Idempotent retry | PASS: same ID/payload returns original row |
| Same sample ID/different payload | PASS: service and actual controller HTTP 409 |
| Attempt uniqueness | PASS: different sample ID/same attempt -> 409 |
| Stale revision | PASS: controller label/submit/review 409; revision/snapshot count unchanged |
| Unauthorized access | PASS: unrelated therapist even with grant gets controller detail/label 403 |
| Self-review | PASS: author with reviewer grant still gets controller 403 |
| Annotation revisions | PASS: flush/clear, SQL revisions 1/2/3, DRAFT/SUBMITTED/APPROVED snapshots |
| Disposition | PASS: ACTIVE/REJECTED/NEEDS_RESAMPLE reload after flush/clear |
| Audit persistence | PASS: SQL actions for upload/draft/submit/decision, reason/disposition snapshots, export audit rows |
| Needs Resample linkage | PASS: new child ID, stored parent FK, parent payload unchanged, separate approved child |
| Approved filtering | PASS: authorized ZIP contains one valid approved sample; other source empty |
| Rejected/Needs Resample exclusion | PASS: independent review decisions yield zero exported rows; linked child only export case |
| Consent-revoked exclusion | PASS: withdrawal through existing service yields zero export |
| Cleanup | PASS: exact COUNT(*) after tests is zero on every one of the 29 tables |

CRUD respects existing immutable-sample design; no API was invented to replace stored skeleton frames.
Fixtures use random synthetic user/sample IDs, example.invalid accounts and contract-valid deterministic skeletons.
Most cases rollback; concurrent case commits ephemeral fixtures and finally deletes only its generated records.
No tables were cleared/truncated; auto-increment allocations were not reset.

## Commands and results

    & tools/body-round3-mysql-test.ps1

- Initial original suite: 7/7 PASS, no skips.
- Expanded first run: 12 cases, one assertion failure and one fixture error.
  Exact ZIP type assertion rejected the existing charset=UTF-8 suffix;
  synthetic helper attempted to insert a second grant for the same user/study.
  Corrected MIME-compatible assertion (ZIP contents still parsed and checked) and grant helper upsert.
  No business logic, schema constraint or authorization assertion was relaxed.
- Expanded final real MySQL: 12/12 PASS, 0 skipped/failures/errors.

    mvn test
    mvn package -DskipTests
    git diff --check

- Full regression: 331 discovered, 298 PASS, 33 skipped, 0 failures/errors.
  DB_URL was removed in the Maven child process to prevent access to any other database.
  Skips: 22 old MySQL, 10 methods of this gated class, one activation test.
  The 10 methods produce 12 invocations when enabled (three parameterized dispositions).
  Thus full-suite skips do not replace the separate real MySQL 12/12 evidence.
- Package PASS; diff check PASS.
- No Flutter source/native changes in this continuation; prior scoped tests/analyze/Debug/Release remain recorded.
  Prior full Flutter 545 PASS/7 inherited FAIL is not relabeled all passing.

## Execution evidence

- target/body-r3-mysql-validation.log: final actual MySQL startup and 12/12 result.
- target/body-r3-mysql-expanded-first-failure.log: preserved initial expanded failures.
- target/body-r3-post-v005-full-maven.log: latest full suite.
- target/body-r3-post-v005-package.log: package result.
- target/surefire-reports/*.xml: latest full-suite reports (Body class gated there).
- Read-only post-test verification: SHOW TABLES and exact COUNT(*) on all 29 tables, all zero.

Logs are ignored build artifacts, not patient data exports. MockMvc request printing disabled
to avoid dumping authenticated test headers/payloads if assertions fail.

## Remaining Round 3 gates

Local isolated database request is complete. Overall Round 3 remains PARTIAL because real
Pi camera -> TV inference/collector -> Therapist phone UI E2E and phone/TV release runtime
have not been executed against an explicitly isolated API/device setup.
Laboratory validation remains NOT VALIDATED, production NOT DEPLOYED. Do not start Round 4.
No push, deploy, production collection activation, model training, Pi/network changes or new branches.
