# Round 4 Backend Export Integration

Branch main. Baseline496327cfe860290c6987d40dc40ac098a3a4e43c.
Implementation c5f45f7622fe299fcbd801ae12a25b49996ed49e; test/handoff53e170fa63cafa3af4410ff027580f87dc57701d. Final documentation-only HEAD available from git log.

## Actual changes
- ResearchManagementService: deterministic approved sampleId sort; additive body3 exportContractVersion body-approved-export-v1 and labelMappingVersion body-attempt-label-v1.
- Anonymous per-sample payload SHA256, consent/expiry/ACTIVE/APPROVED/independent review snapshot, annotation revision/time and anonymous author/reviewer aliases. Preserve sample UUID resample linkage for downstream group checks.
- Aggregate excluded-approved reasons; no names/email/raw user IDs/free-text notes/tokens. Existing bounded in-memory ZIP, manager/study authority, approved-review/consent/retention/synthetic exclusions and export audit remain in force.
- Does NOT attest clinician qualification: professionalQualificationAttested=false, consentSnapshotOnly=true. Real training requires fresh authorized export and separately governed professional review; previous downloaded files cannot reflect later withdrawals automatically.
- No schema migration/entity/controller/API path/collection setting or SQL changes. Body1 and hand2 contracts preserved.

## Actual validation
- mvn -q -Dtest=ResearchManagementServiceTest test:10/10 PASS after hardening.
- mvn -q test:333total,300PASS/33gated skips/0failure/errors. Do not count MySQL-gated skips as MySQL PASS.
- Mock-only generated target/body-r4-approved-test-export.zip -> actual Python builder codec:PASS. The temporary fixture envelope is explicitly SYNTHETIC_ENGINEERING_ONLY; not a real professional dataset or HTTP/MySQL/clinical E2E.
- git diff --check:PASS.
- No new MySQL or Android validation needed for this additive software export contract. Round3 accepted tests not rerun.

## Downstream compatible version
Flutter master40145b34e5179709438fcbb0459ac7e08ec967d4: ml/train_body.py / ml/body_v3. Existing train.py body1/hand2 untouched.
Complete38-topic engineering report: Flutter docs/BODY_RESEARCH_ROUND4_REPORT.md; commands/contract:ml/BODY_V3_README.md.
Synthetic smoke72attempts/12artificial subjects, RF/XGB/SVM all actually exported and parity-checked. EXPERIMENTAL only; NO VALIDATED REAL MODEL YET. REAL_DATA_TRAINING=DATA_INSUFFICIENT. No model installed in App.

## Environment separation
- Local isolated MySQL:Round3 PASS retained; Round4 NOT RUN / no connection or modification.
- Laboratory MySQL:NOT VALIDATED; NOT ACCESSED.
- Production:Round4 NOT DEPLOYED; no Render/config/database operation.
- No new branch, push, migration, production research activation, Raspberry Pi change or hardware motion request. Stop before Round5.
