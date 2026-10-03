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
