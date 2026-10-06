# Body Research Round 2 — Backend

Baseline main: c5fa9eb6243f14923fc0b3f0cefce5db254d5a67.
Current branch main; no new branch since workflow correction. Earlier empty
codex/body-research-foundation branch remains; no implementation commits there.

Implemented v3 body dispatcher/strict validator with feature recomputation,
DEFAULT standing-knee assignment check and participant+attempt idempotency.
Existing authentication, consent, binding, authority and independent-review checks retained.
v1 body and v2 hand unchanged; v3 cannot enter the old classifier/export format.
New additive V004 in docs/database-rebuild/mysql; V001..V003 unchanged.
V004 NOT EXECUTED; no production database connection or deployment.
Review/audit nullable schema columns prepared; full new review UI is Round 3.

## Final validation
- PASS: mvn '-Dtest=ResearchBodyAttemptTest,ResearchBodyMigrationTest,ResearchDataServiceTest,ResearchHandContractTest' test; 33/33, no failures/errors/skips.
- PASS: full mvn test, 303 run / 0 failures/errors / 23 skipped (280 passed). Before final shared-fixture test; final focused run includes it.
- 22 MySqlMigrationIntegrationTest skipped: DB_URL unavailable. One ResearchActivationSetupTest skipped: controlled configuration absent.
- PASS: mvn package -DskipTests, target/trainingsystem-0.0.1-SNAPSHOT.jar.
- PASS: final git diff --check.
- PASS: two V004 static compatibility tests; NOT RUN: V004 live MySQL / Hibernate validation / real upload.
- Evidence: target/round2-full-test.log and target/surefire-reports.
- No production connection, schema mutation, activation, deployment, model training, commit or push.

## Remaining / handoff
1. Review uncommitted main diff; V001..V003 untouched.
2. In explicitly isolated MySQL only, apply V004 and validate Hibernate/old rows/concurrent duplicate requests. Do not deploy new entity mapping before migration.
3. Pi/TV actual observation/upload and phone compatibility hardware acceptance.
4. Modality-aware v3 therapist UI/export and Python feature parity belong to Round 3, not started.
5. DEFAULT standing-knee assignment supported; CUSTOM lacks trusted action mapping and fails closed.
6. v3 has no old classifier-compatible trainable labels/export; no feature-schema mixing.
- Flutter compatible working-tree HEADs: master 91fdd2ba1b7406b83406bbd45af76ff1ebd91584; TV 2f3b67e544475ba3b35de129c8964f1ec3042635. Changes uncommitted.
- Complete file manifest/16-section report: Flutter master docs/BODY_RESEARCH_ROUND2_REPORT.md.
- Keep existing identity/consent/binding/review restrictions. No further branches or wholesale merges.
