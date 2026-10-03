# G5 global research activation (2026-10-03)

## Owner authorization and configuration
- Existing Render service: srv-dad76non74is73dj1blg / https://trianing-system-1.onrender.com. Observed Live commit 7c55d1c, an ancestor of local G5 main38c6ba8; real-time chat preserved.
- User explicitly authorized global activation, existing MySQL CRUD, synthetic accounts/sample, setting changes and deployment. No new database, migration, public seed endpoint, account allowlist or automatic patient consent.
- RESEARCH_COLLECTION_ENABLED=true; RESEARCH_CONSENT_VERSION=hand-research-consent-v1; RESEARCH_HAND_CONSENT_VERSION=hand-research-consent-v1.
- Existing single-study key remains standing-knee-raise-v1, including its registered hand actions. Authentication, voluntary consent, binding, grants and independent review remain required.
- Model availability remains unchanged; no trained/clinical model or ethics approval is asserted.

## Policy and pre-write evidence
- MySQL8.4.11 / rehab_r2_validation: prior policy count0, named demo accounts0, DEMO-HAND samples0. Read-only snapshots saved in ignored .local/research-before-*.txt.
- Created hand-retention-v1, 90 days from server sample creation/upload (uploaded_at), DELETE on expiry, reference OWNER-AUTH-20261003-G5.
- This is an internal owner instruction, NOT IRB/institutional approval. The generic approval_reference field stores that literal provenance; do not interpret it as formal approval.
- Existing effective policies take precedence; never overwritten. Existing patients must actively accept the current consent version; local/cloud consent remain separate. No ordinary patient consent was inserted by setup.

## Reproducible synthetic setup/reset
From backend PowerShell: `./tools/research-activation.ps1 -Action Seed` or `-Action Reset`.
- Uses existing DPAPI .local/mysql-app.credential.xml, localhost schema CRUD only. No root password, DDL or secret logging.
- Creates demo_patient, demo_therapist, demo_reviewer, demo_manager; DEMO display names and @demo.invalid emails. Fake login credentials are generated and DPAPI-protected in ignored .local/research-demo-logins.credential.xml. Incompatible existing accounts stop transaction rather than overwrite.
- Binds only the fake patient to those therapists. Manager bootstrap is audited. Normal grant, policy, consent and upload services are used. Reviewer is independent from annotator.
- DEMO-HAND-001 / DEV-SUBJECT-001 / sidePinch: schema2,41 frames,4 seconds,21 image xyz points, open-to-pinch-to-return. Features calculated by ResearchHandFeatures.extract and checked by unchanged production Validator. No random landmarks, invented confidence/world data or medical labels.
- Initially UNLABELED. Seed rerun preserves manual annotations and reuses existing payload/server ID, preventing duplicates.
- Reset clears only this sample's annotation/revisions for the exact fake patient; does not delete sample, unrelated records or audits. Prefixes DEMO- / DEV-SUBJECT- never enter approved training export. Manager counts are operational workload, not clinical metrics.
- Setup is an explicit opt-in test-tool, not packaged runtime/startup logic. Normal Maven test does not create persistent fixture rows.

## Actual pre-deployment validation
- Focused43 PASS. First attempt had5 export-fixture NPEs from missing mock clientSampleId; fixed null-safe prefix detection and added synthetic export exclusion test.
- Actual MySQL Seed PASS; second Seed PASS/idempotent. Original G5 Validator accepted full sequence and 21 points.
- Full MySQL-enabled Maven:285 discovered,284 PASS,1 intentionally skipped opt-in setup test,0 failures/errors (target/research-activation-full-final.log). Setup separately executed twice/PASS.
- First full run failed2 tests assuming no policy/only one manager. Tests now isolate absent policy and deterministic late deletion failure; no real policy/grant removal, no weakening of security/rollback assertions.
- Maven package -DskipTests PASS; Hibernate validate on existing29-entity schema. No DDL executed.
- Runtime Render API and Android acceptance pending at this commit; append verified evidence afterward.

## Restoration
- Stop global collection: Render RESEARCH_COLLECTION_ENABLED=false then redeploy. Do not delete policies already referenced by samples or overwrite patient consent.
- Repeat screenshots: Reset command above affects only synthetic annotation. Remove synthetic payloads using original authenticated DELETE /api/ml-research/my-data as demo_patient; governance audits remain. Delete fake accounts only through original authenticated account-deletion flow, never a broad SQL delete.
- Original Render research keys were absent; prior Live version7c55d1c. Keep G5 code for hand contracts; disabling collection is safer than restoring an older incompatible runtime.

## Actual Render evidence / target correction
- Non-force push d2f06877d6e857ab06e6f45ab27b4ccaa3e0979f succeeded. Render Auto-Deploy built it and dashboard verified this commit Live. Three research keys saved with explicit user confirmation. No old-version manual deployment was started.
- Critical correction: local127.0.0.1 MySQL and Render laboratory100.94.202.58 are DIFFERENT instances despite matching schema name. Local seed/policy evidence above is not cloud evidence. Runtime login of the local fake account initially returned INVALID_CREDENTIALS.
- Original registration/login APIs then created real Render fake accounts: patient253, therapist254, reviewer255, manager256, using demo_<role>@demo.invalid. They retain PATIENT/THERAPIST roles; all research grants were false. Original binding API linked each fake therapist to fake patient253.
- Actual consent response: active=false,currentVersion=hand-research-consent-v1,available=false,handAvailable=false,unavailableReason=RESEARCH_RETENTION_UNSET. Do not claim global flow operational yet.
- Lab3306 is unreachable from this machine and no local Tailscale CLI exists. No attempt to retrieve DB passwords or bypass identity was made. Policies/grants/sample should be provisioned through original authenticated APIs, once a real manager is authorized.
- A proposed default-off operator-only first-manager initializer was blocked by automated safety review because exact target/environment confirmation was required. Patch was NOT applied. Await user confirmation for verified demo_manager@demo.invalid/user256 on this Render service/schema; no role change/public endpoint/anonymous elevation.
- Pending tools: research-remote-accounts.ps1 (executed/PASS4 accounts), research-remote-configure.ps1 (NOT RUN pending actual manager), research-runtime-check.ps1 (initial login FAIL before remote accounts existed; full review chain NOT RUN). All three PowerShell syntax checks PASS.
- Phone d698e1fa connected and user confirmed unlock; new cloud Android UI acceptance NOT RUN while actual retention/authority remain unset. Original installed G5 Release remains unchanged.

## Explicit first-manager authorization (continuation)
- Owner explicitly authorized verified Render user256/demo_manager@demo.invalid/THERAPIST. Default-off ApplicationRunner uses RESEARCH_INITIAL_MANAGER_USER_ID, RESEARCH_INITIAL_MANAGER_EMAIL and RESEARCH_INITIAL_MANAGER_REFERENCE; no public endpoint, secret, base-role changes or patient allowlist.
- Initializer validates all settings and exact target identity, refuses another existing manager, performs grant/audit atomically at SERIALIZABLE isolation and is restart-idempotent. Remove operator settings after successful initialization.
- Focused tests cover default-off/Spring bean creation, identity/role mismatches, first-manager audit, existing-manager refusal and idempotence. Actual Render execution pending.

## Actual global runtime results (latest continuation)
- e769a5dae129ddd489f819acd84baa544f9c6fec pushed non-force and Render Live. Verified manager256 canManage=true and basic THERAPIST role preserved. Three RESEARCH_INITIAL_MANAGER_* keys removed, same image redeployed successfully; initializer is now inactive.
- Remote policy history initially empty. Original authenticated API created hand-retention-v1/90 days/DELETE/internal owner reference; rerun preserves policy and named sample. First script attempt hit an empty-array PowerShell parsing error before writes; corrected, syntax and runtime rerun PASS.
- Actual Render consent available=true, handAvailable=true, currentVersion=hand-research-consent-v1. Only fake patient253 opted in by setup; normal patients choose themselves. Grants assigned only to fake bound therapist254 and independent reviewer255.
- Actual patient HTTP upload/dedup/therapist detail with41 frames21 points PASS. Therapist self-review403 PASS, independent reviewer approval of synthetic003 PASS.001 left UNLABELED.002 uploaded for Android UI DRAFT/submit/review acceptance.
- Focused initializer/regressions23 PASS (initializer8). Full actual MySQL Maven293 discovered/292 PASS/1 opt-in setup skip/0 failures/errors, including22 MySQL integration PASS. Package PASS. All4 tooling PowerShell parsers PASS.
- Actual Android installed Release: fake therapist login/sample list/001 skeleton/21-of41 pinch slider/DRAFT002 saved/SUBMITTED002 verified. Patient consent/upload and reviewer UI acceptance continuing; do not yet claim full phone E2E.
- Global approved-export download blocked by safety review: could include unrelated sensitive rows. No file downloaded; live export NOT RUN. Existing unit tests prove DEMO-prefix exclusion. No trained/clinical model or institutional ethics approval asserted.

## Correct remote screenshot/reset commands
- `./tools/research-remote-configure.ps1`: real Render policy/grants/consent/idempotent001, never local-only substitute.
- `./tools/research-runtime-check.ps1`: actual API synthetic003 independent review; expects001 still unlabeled, so do not rerun after intentionally manually labeling001.
- `./tools/research-remote-reset.ps1 -SampleName DEMO-HAND-001`: only verified remote fake patient253/named sidePinch fixture, snapshots just that row in ignored .local, original DELETE/re-upload produces new server UUID and UNLABELED state. Does not erase audits or unrelated rows. Runtime reset verification pending.
- `./tools/research-demo-logins.ps1`: operator-only display of generated disposable account passwords from DPAPI. Never paste its output into Git/log/report. Login by demo_<role>@demo.invalid, NOT the local-only underscore accountId.
- Original `research-activation.ps1` still targets localhost and must NOT be described as resetting Render data.

## Final acceptance evidence (2026-10-03)
- Actual Android DEMO PATIENT253: local and cloud consent separately enabled by explicit test operation, DEV-SUBJECT-001 entered, sidePinch hand skeleton/feedback/counting observed. User completed five captures; phone shows pending0/synced5, and actual Render returns five handset samples plus three synthetic fixtures (eight total). No real patient's consent was changed.
- The five handset samples are actual device test captures under a disposable identity, NOT computer-generated landmarks and NOT professionally assessed research evidence. The three DEMO-HAND fixtures are synthetic. No images/videos are part of the skeleton sample contract.
- Bound therapist read a handset sample with18 frames/all21 landmarks, saved an unassessable draft and submitted it; independent reviewer255 approved the workflow with an explicit non-clinical DEMO note. This does not establish motion quality, ethics approval or model accuracy.
- Android therapist list,001 skeleton playback at frames1/21,002 draft/submission and independent reviewer approval are visibly verified. Screenshots: Flutter ignored build/research-acceptance/{patient-synced,therapist-list,therapist-skeleton,therapist-pinch-frame,therapist-draft,therapist-submitted,reviewer-queue,reviewer-controls,reviewer-approved}.png. Model remains unavailable. Manager authority API PASS; manager UI screenshot NOT RUN.
- Remote002 reset ran twice/PASS without duplicates; restored DRAFT. Final synthetic statuses:001 UNLABELED,002 DRAFT,003 APPROVED. Reset creates a new server UUID and retains governance audit records.001 stays available for manual labeling/review.
- Dataset hygiene additionally excludes samples from accounts bearing BOTH the explicit DEMO display-name prefix and reserved @demo.invalid email, including ordinary hand_* device IDs. This affects approved training export only, NOT consent/upload/grants/access. Unit tests cover both markers and non-demo accounts. Global export download remains BLOCKED/not executed because it might include unrelated sensitive rows.
- Focused six-suite tests49/49 PASS. Final full `mvn -q test`:295 discovered,294 PASS,1 opt-in setup skip,0 failures/errors, including22 actual local MySQL integration tests. Opt-in persistent seed separately executed twice/PASS. `mvn -q -DskipTests package` PASS. Evidence logs: ignored target/research-demo-final-full.log and target/research-demo-final-package.log. PowerShell tooling parsing and git diff --check PASS.
- No Flutter source/native/dependency changes or APK rebuild were needed for this activation; installed G5 Release was tested on connected Android. No new migration, database recreation, real-account seed, automatic consent, public bootstrap endpoint, fake model or formal training.
- Latest dataset-hygiene deployment will be verified against its exact main commit after the non-force push; the earlier verified Live version is e769a5d. The removed RESEARCH_INITIAL_MANAGER_* keys must remain absent.

## Operator screenshot instructions
1. From backend directory run `./tools/research-demo-logins.ps1` locally to view the generated disposable passwords; never commit or paste its output into reports. Patient/therapist/reviewer/manager login emails are demo_patient@demo.invalid, demo_therapist@demo.invalid, demo_reviewer@demo.invalid and demo_manager@demo.invalid.
2. Patient: sidePinch training -> upper-right research flask -> local/cloud consent and synced sample count. This existing patient screen lists local handset captures; it does NOT download seeded001 into the local repository.
3. Therapist: Home -> research annotation -> pending001 (server ID abc0630d-eb29-4cc9-b67a-6996eb761c1c) -> 21-point playback -> save draft -> submit. Cloud list currently shows server UUID rather than DEMO client ID, so identify001 by this UUID. Two other synthetic fixtures provide draft/approved examples.
4. Independent reviewer: same research annotation page -> enable research review mode -> submitted sample -> review note -> approve/return. Author cannot self-review even if also granted review authority.
5. Manager: existing research management entry (backend canManage verified); authorization, retention and export controls remain original. Global export has not been downloaded during this acceptance.
6. Re-seed safely with `./tools/research-remote-configure.ps1` (preserves existing001 annotation). Reset only a chosen fixture with `./tools/research-remote-reset.ps1 -SampleName DEMO-HAND-001`. It snapshots ONLY that synthetic row in ignored .local, deletes/reuploads through the original authenticated API and resets to UNLABELED. If reupload fails, restore only its saved payload. Do not use localhost reset to claim remote recovery.
7. Stop collection if required: set RESEARCH_COLLECTION_ENABLED=false on the actual Render service and deploy; keep existing consent/policies/audits. Existing exported copies/backups cannot be claimed erased by deleting the live sample. Internal owner authorization is not institutional approval for human-subject research.
