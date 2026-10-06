# Body Research Round 3 Progress

## Branch/checkpoint
- main Round 2 checkpoint: 45c7bbb0fc3b4aa09d22a212bf7b92c764e9f36c.
- master checkpoint 3e6d013184104898596e82e2a580006145c1b05d.
- TV checkpoint 2a0b10b95e95b5e7c29ff41f2815c258bf26d84d.
- No new branches/push/deploy. Existing Round 2 complete.
- Verified Round 3 implementation checkpoint main 6048bf32e6bccb6ae694297e68a57a600d41157b; compatible master 1326472811298223182d009c252645e1da5a9610 and TV 968d8a4be92c4a9696db7944001b4d187ce33ad4. Final documentation-only HEAD can be read with git rev-parse HEAD.

## Phase A
- Environments are separate: local isolated MySQL validation only; laboratory MySQL NOT RUN/NOT VALIDATED; production NOT DEPLOYED. Laboratory access needs new explicit authorization.
- Local MySQL 8.4.11 confirmed; DPAPI rehab_app credential, CRUD only on old schema.
- User completed fresh isolated rehab_body_r3_validation setup.
- PASS actual metadata: MySQL 8.4.11, 29 tables, 253 columns, V004 including REJECTED.
- V005 subsequently applied by owner. Read-only local metadata PASS on 2026-10-06: 29 tables/257 columns/34 FK; no V001-V004 replay.
- Pi camera TCP reachable; only Android phone connected, not TV hardware.

## Implementation/testing (pre-V005 checkpoint, preserved history)
- Implemented review/disposition/resample/revision guard and v3 source-specific export.
- PASS backend focused 58/58 (10 new review tests and 7 export tests).
- Latest full mvn test PASS: 328 discovered, 298 passed, 30 skipped. Seven new real MySQL tests skipped pending V005; not a MySQL PASS. mvn package -DskipTests PASS.
- BodyRound3MySqlIntegrationTest and tools/body-round3-mysql-test.ps1 added: localhost/schema/V005 preflight; test-only synthetic collection bean; normal collection stays false; transactional fixtures rollback.
- v3 upload uses consent-row serialization plus READ_COMMITTED. v3 edits lock existing sample before first annotation insertion. Real duplicate upload/first draft race test compiles, NOT RUN pending V005.
- Seven BodyRound3MySqlIntegrationTest cases compiled: metadata/closed normal flag, idempotency/conflicts, legacy body/hand persistence, authenticated upload->independent review->export, immutable resample lifecycle, revoked/unauthorized, concurrent upload/first draft.
- Most fixtures rollback. Concurrent fixture explicitly commits then cleans only its generated fake user/sample/assignment/policy IDs in a finally transaction; no table clears.
- User confirmed V005 not yet run; no Hibernate success claimed.
- Preserve identity/consent/binding/independent-review restrictions; no production activation.
- Update this file after focused validation and record exact remaining work.

## Final checkpoint / continuation
- Full backend file manifest and exact commands: docs/BODY_RESEARCH_ROUND3_REPORT.md.
- Final Flutter master focused 61/61, TV focused 35/35, scoped analyzes zero issues; phone and TV Debug/Release builds PASS.
- Flutter full run 545 passed / 7 inherited failures, not all passing. Detailed evidence kept in Flutter report.
- git diff --check PASS. Local checkpoint commit only; no push or deployment.
- V005 continuation complete locally: tools/body-round3-mysql-test.ps1 expanded real MySQL run PASS 12/12, zero skips. No root or migration needed for reruns.
- Do not replay setup/V001-V004. No laboratory or production operation authorized.
- Actual local Hibernate/API/concurrency PASS; Pi/TV hardware E2E still NOT RUN. Round 3 PARTIAL, not ready for Round 4.

## Local MySQL validation completed — 2026-10-06
- Original seven real MySQL cases passed before expanding explicit assertions.
- Expanded 12/12 PASS: schema/Hibernate, v1/v2/v3 CRUD, duplicate retry/attempt uniqueness, concurrent upload/first draft, HTTP 403/409, revision/disposition/audit, resample linkage and export filtering.
- Expanded first run FAIL (one assertion/one fixture error): ZIP content-type includes charset; duplicate synthetic research grant. Corrected semantic MIME assertion/test-helper upsert only; business code/schema unchanged. Failure evidence retained.
- All 29 tables exact COUNT(*) zero after fixtures rollback/precise concurrent cleanup. No truncate/table clear or auto-increment reset.
- Latest full Maven (DB_URL removed from child process): 331 discovered, 298 passed, 33 skipped, zero failures/errors. Ten Body methods skipped here; separate actual MySQL run executes twelve parameterized cases and passes. Package PASS.
- Normal research collection remains false; only test-local synthetic service accepts fixtures. No laboratory/prod connection or deployment, push or Round 4.
- Evidence/matrix: docs/BODY_RESEARCH_ROUND3_MYSQL_VALIDATION.md; target/body-r3-mysql-validation.log; target/body-r3-post-v005-full-maven.log; target/body-r3-post-v005-package.log.
- Remaining: isolated Android/Pi/TV hardware E2E and release runtime acceptance; laboratory NOT VALIDATED; production NOT DEPLOYED.
