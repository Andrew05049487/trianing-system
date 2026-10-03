# G5 hand research backend checkpoint (2026-10-03)

## Baseline and scope
main `7c55d1c7527d94ca0e23c50213f68f7e4f973d7a`, initial clean; fetch local/remote 0/0. Flutter compatible contract checkpoint `baa90e3` plus runtime work in progress. No branch/push/deploy or DB migration. Do not redo G4/standing.

## Implemented
- Four separate registered actions: turnPalm, sidePinch, wristExtension, wristSideBend. Schema2 21 xyz image proxies, no invented confidence/world/anatomical side. Unknown side explicitly supported by existing VARCHAR(8); payload LONGTEXT unchanged.
- Strict whitelist/source/extractor/model input/ordered features/timing/segment/geometry validation and server-side recomputation. Existing schema1 17-point path preserved.
- Hand uploads/exports require nonblank `RESEARCH_HAND_CONSENT_VERSION` matching current `RESEARCH_CONSENT_VERSION` and participant consent. Default blank denies hand scope. An operator must configure a newly approved consent covering these actions; this code is not ethics approval. Collection remains false by default. No environment change performed.
- Hand labels require `hand-research-v1`; per-action definitions/vocabulary, independent reviewer, binding/grants/retention/approved-only export remain enforced. Export schema now action-specific (1 or 2), no PII. Missing/mismatched hand scope fails closed.

## Executed evidence
- Focused Maven ResearchHandContract/Data/Management/Authority/Retention: **40 PASS, 0 FAIL/error/skip**, `target/g5-hand-focused.log` (ignored).
- Existing protected localhost MySQL8.4.11 wrapper `./docs/database-rebuild/mysql/run-local.ps1 -Action FullTest`: **281 PASS, 0 FAIL/error/skip**, including **22 actual MySQL integration tests** (`target/g5-mysql-full.log`). Hibernate validate unchanged. New test loops all four actions: consent, insert/reload unknown side/JSON, duplicate retry, wrong label version rejection, independent review, submitted excluded from export, per-action approved export, locked label, withdrawal exclusion. Entire fixture transactional rollback. Production bean stays closed.
- No V001/V002/V003 executed. No formal data/model trained. SQL environment uses existing DPAPI-protected application credentials, never logged or committed.

## Remaining / continuation
Flutter runtime/model release tests, full regression/analyze/debug/release/device acceptance still ongoing; see Flutter docs/G5_PROGRESS.md. Native pipeline/dependencies untouched. Formal professional definitions/data/models/device model latency NOT READY/NOT RUN, never substitute fixture metrics. Run git status/log before continuing; preserve all tested work. No push/deploy or real collection without separate authorization.

## Final feature-bound/package verification
- Non-pinch unwrapped image-axis range/excursion above360° rejected, matching Dart/Python/ONNX input quality bounds. Focused ResearchHandContractTest2/2 PASS after this hardening (`target/g5-final-bounds.log`).
- Actual MySQL integration suite rerun after final hardening:22/22 PASS,0errors/skip (`target/g5-mysql-final.log` + surefire XML). No DDL/migration, synthetic fixtures rolled back. `mvn package -DskipTests` PASS (`target/g5-package.log`).
- Flutter compatible runtime checkpoint `2485976`; full497PASS/7baselineFAIL. Final build/hardware results recorded by Flutter G5 documents, not inferred here.
