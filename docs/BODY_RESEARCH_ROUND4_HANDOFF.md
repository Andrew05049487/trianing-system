# Body Research Round 4 Backend Handoff

Branch main; baseline 496327cfe860290c6987d40dc40ac098a3a4e43c.

Body v3 approved export is extended additively, without changing endpoints, schema, runtime or authorization:
- exportContractVersion=body-approved-export-v1; labelMappingVersion=body-attempt-label-v1.
- Deterministic sampleId ordering; anonymous group IDs; preserve UUID resample linkage.
- Per-sample exact payload SHA-256, eligibility snapshot (independent APPROVED review, ACTIVE disposition, consent active, expiry), revision and anonymous annotation/reviewer aliases.
- Aggregate filter reason counts; no raw account IDs or review free-text included.
- professionalQualificationAttested=false: backend permissions do not prove clinical qualifications. A fresh export plus separately governed professional attestation is required for real training.
- consentSnapshotOnly=true: never promises later withdrawal will be reflected in a previously downloaded export.

Implementation commit: c5f45f7622fe299fcbd801ae12a25b49996ed49e.
Focused ResearchManagementServiceTest: 10/10 PASS after hardening.
Full mvn -q test: 333 tests total, 300 PASS / 33 gated skips / 0 failures/errors. Not real MySQL validation this round.
Named test-only ZIP target/body-r4-approved-test-export.zip is emitted from mocks. Actual Java ZIP -> Python builder codec smoke PASS; it is never considered a real professional dataset.
Flutter consumer is ml/train_body.py and ml/body_v3 on master; preserves legacy ml/train.py body v1 / hand v2.
No real approved dataset provided. All training smoke inputs must be explicit SYNTHETIC/ENGINEERING_ONLY; never reuse Round 3 fake motion data as real evidence.

No SQL migration, database access, new branch, push or deployment. Round 3 local MySQL validation preserved; Laboratory NOT VALIDATED / Production NOT DEPLOYED. No additional hardware motion acceptance required.
