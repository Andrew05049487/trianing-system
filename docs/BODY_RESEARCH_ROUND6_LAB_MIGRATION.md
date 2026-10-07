# Round 6 Laboratory migration and deployment acceptance

Status: **PASS for the authorized Lab migration, Hibernate validation, Render recovery and read-only software smoke.** Broader write E2E / model / physical-device acceptance is not implied.

## Environment and scope

- Date: 2026-10-07 (Asia/Taipei).
- Backend main: `9871f96a4f55db498ceea6895d28daf02d518eab`.
- Laboratory MySQL: `100.94.202.58:3306/rehab_r2_validation`, actual server 8.4.11.
- Account verified in MySQL: `rehab_render@100.%`.
- Connection: existing Render service `trianing-system-1` Web Shell, existing userspace Tailscale SOCKS5 127.0.0.1:1055, existing DB environment variables. Credentials never extracted or printed.
- User confirmed complete pre-R6 Lab backup exists; its contents were not independently inspected.
- This is Laboratory validation, NOT the earlier local `rehab_body_r3_validation` database. No import, database recreation, Pi access, physical motion tests, or new round.

## Migration inventory and privilege decision

All five versioned MySQL migrations and thirteen historical SQL Server migration files were inspected. Historical SQL Server scripts are not applicable to MySQL and were not executed.

- V001/V002: existing main/research foundation; not rerun.
- V003: `custom_rehab_exercises.pose_measurement_rules_json` present; not rerun.
- V004: four ALTER TABLE statements; 12 added columns, one UNIQUE, one index, two CHECKs; no new table or FK.
- V005: three ALTER TABLE statements; four added columns, one index, one self-referencing FK with ON DELETE SET NULL.
- `research_annotation_revisions.disposition` is added by the last ALTER in V005.
- Actual grant: SELECT, INSERT, UPDATE, DELETE, CREATE, REFERENCES, INDEX, ALTER on this schema. No DROP granted or required. None of the seven pending statements drops/renames/recreates a table or deletes rows.
- MySQL ALTER TABLE requires ALTER/CREATE/INSERT; FK creation additionally requires REFERENCES. Existing permissions cover the reviewed statements.
- MySQL DDL implicitly commits; this was NOT treated as a transactionally rollbackable multi-statement migration. Execution stops on an error or unexpected/partially applied baseline.

Exact files executed without modification:

| File | SHA-256 | DDL statements |
| --- | --- | ---: |
| V004__body_attempt_research.sql | 51cbace47f56a5e64485f60761c3c8193e2b007709199b9bc435a9effade3881 | 4 |
| V005__body_review_resample.sql | cd149642aa5f2bd27d26b6c79ad25d25cb8fb5a4e146735d3e194a0eadbd0b41 | 3 |

## Actual execution evidence

One-shot Java/JDBC tooling was transferred to /tmp/r6-lab-jdbc in the existing Render instance (no HTTP migration endpoint, no startup migration, no repository business-code change). It verifies host/schema/server/account/grants/baseline before any ALTER and uses the existing Connector/J inside app.jar. Migration mode is explicit; it refuses an already/partially migrated schema.

| Check | Result |
| --- | --- |
| Read-only preflight: exact account/server/schema, FK checks enabled | PASS |
| Before: 29 tables / 241 columns / 33 FK | PASS |
| V004 fields and constraints absent; V005 fields and FK absent | PASS |
| V004 four original ALTER statements | 4/4 PASS |
| After V004: 29 / 253 / 33 | PASS |
| V005 three original ALTER statements | 3/3 PASS |
| After V005: 29 / 257 / 34 | PASS |
| Added UNIQUE/CHECK/index/FK presence; resample FK ON DELETE SET NULL | PASS |
| Detailed metadata: CHECKs / Generated columns / total indexes including FK support | 18 / 2 / 78, PASS |
| All 16 added columns: actual type, nullable and default values | Match reviewed V004/V005, PASS |
| Full V001-V005 metadata definition audit | 11 result queries, zero TABLE/COLUMN/INDEX/FK/CHECK drift, PASS |
| Existing 241-column values: pre/post ordered SHA-256 fingerprints and row counts of all 29 tables | Unchanged, PASS |
| Hibernate 6.3.1.Final validate against Lab, latest-main 29 Entity classes, matching Boot naming/type settings | PASS |
| Maven compile | PASS |
| Full Maven test | 333 discovered: 300 passed, 33 skipped, 0 failures/errors |
| Maven package -DskipTests | PASS |

Data integrity comparison hashed existing column values inside MySQL; no account/research payloads, images or credentials were printed. V004 adds ACTIVE as the default disposition for legacy rows, leaving their original values unchanged. Nullable version/source/context columns remain NULL for legacy records.

The first standalone Hibernate check incorrectly omitted the application's existing `tinyInt1isBit=false`, reporting BIT versus TINYINT for is_active. Only the temporary verifier configuration was corrected to match production (`tinyInt1isBit=false`, `useServerPrepStmts=true`); no schema or application mapping was changed. Recheck: `R6_HIBERNATE_VALIDATE_PASS entities=29 ... ddl_auto=validate`, exit 0.

## Render deployment and software acceptance

- GitHub origin/main was read-only checked with `git ls-remote`: same `9871f96a4f55db498ceea6895d28daf02d518eab` as local main. No push was needed or performed.
- Previous successful deployment was older `c5fa9eb`; its Live badge was NOT treated as proof of the latest main.
- Manually deployed latest existing main only AFTER standalone Hibernate validate passed.
- New deployment: `dep-db2srpk9v7es73a58k1g`, started 2026-10-07 12:43:18 GMT+8; succeeded and Live in 59.5 seconds.
- Actual new-instance startup log: `2026-10-07T04:44:02.769Z ... Initialized JPA EntityManagerFactory for persistence unit 'default'`.
- Actual startup: `Started TrainingSystemApplication in 38.516 seconds`; Tomcat port 10000 retained; Render reports live at 12:44:18 GMT+8.
- Searching this new deployment's startup interval for `Schema-validation` returned `No matching logs`. The former missing-disposition failure is resolved.
- Proof screenshot: `C:/Users/kuoja/.codex/visualizations/2026/09/02/01a05fee-fbd0-74c3-a162-e29bf04f4a34/round6-render-live.png` (Live, matching commit, JPA initialization).

Read-only API smoke against the actual Render URL: **31/31 PASS**, using ONLY existing disposable DEMO credentials from the untracked protected local credential file; no credentials/tokens/payloads printed:

- Exercise catalog HTTP 200.
- Patient / therapist / reviewer / manager login and original PATIENT/THERAPIST role plus identity token preserved.
- Patient consent GET HTTP 200; observed available=true and handAvailable=true; consent not changed.
- Four accounts' research authority GET HTTP 200.
- Patient / bound therapist / reviewer sample lists and existing DEMO-HAND-001 detail GET HTTP 200; legacy v2 payload's frames retain 21 landmarks.
- Authorized reviewer queue, manager stats and retention history HTTP 200.
- Missing/invalid identity HTTP 401 (the actual existing source contract); patient manager/reviewer access HTTP 403.
- No consent PUT, sample POST/DELETE, annotation/review mutation, grant update, export request or retention processing was executed. Export GET is intentionally omitted because it writes an export audit and downloads research data.

The initial API smoke assertion incorrectly expected 403 for missing identity; source confirms 401 `RESEARCH_AUTH_REQUIRED`. Only the temporary assertion was corrected, not the application or authorization behavior.

## Complete metadata audit evidence

The existing `mysql/validate_static.py` parser and read-only `validation_sql` builder were reused in a temporary external tool and extended from the unmodified V003-V005 statements. Expected manifest: 29 tables / 257 columns / 71 explicitly declared indexes / 34 FK / 18 CHECK. Original validator/migration files were not edited.

Actual latest Render instance `74pxf` checked the Lab schema after deployment. All table names/engine/collation, column names/types/nullability/defaults/generated expressions, declared index columns/order/uniqueness/visibility, FK targets/delete-update rules and CHECK clauses/enforcement matched. Result:

```text
SCHEMA_COUNTS tables=29 columns=257 foreign_keys=34
R6_FULL_METADATA_AUDIT_PASS queries=11 drift=0 fk_support_indexes=7 declared_indexes=71
R6_FULL_METADATA_EXIT=0
```

The seven additional non-unique indexes were individually reviewed as expected InnoDB FK support:
`chat_messages.fk_chat_message_sender`, `exercise_result.fk_exercise_result_exercise`, `exercise_result.fk_exercise_result_user`, `friend_requests.fk_friend_requests_receiver`, `friendships.fk_friendships_high`, `research_annotations.fk_research_annotation_therapist`, `user_bindings.fk_user_bindings_linked`.

The first extended read-only audit returned MySQL 3141 / SQLSTATE 22001 because a multiline CHECK JSON manifest was interpreted through SQL literal backslash escaping. The temporary verifier now binds JSON with PreparedStatement; rerun passed. No global/session sql_mode changes, schema fixes or repeated migration were used to hide it.

Temporary source/tool artifacts (no credentials or private data) are outside both repositories at `C:/Users/kuoja/.codex/visualizations/2026/09/02/01a05fee-fbd0-74c3-a162-e29bf04f4a34/`: R6LabCheck.java, R6Hibernate.java, R6HibernateLauncher.java, R6GenerateValidation.py, R6MetadataAudit.java, R6ReadOnlyAcceptance.ps1. They were invoked explicitly, not installed as startup hooks or API endpoints. Original deployment instance's /tmp tooling naturally expired when replaced; the latest instance's metadata-only /tmp artifacts are ephemeral and do not run automatically.

## Final remaining checks / boundaries

1. Lab migration, full metadata audit, Hibernate validation and the authorized Render redeployment are completed; there is no remaining migration to execute for this target.
2. The 33 Maven skips are NOT passed Lab CRUD tests: BodyRound3MySqlIntegrationTest 10 and MySqlMigrationIntegrationTest 22 lack local DB_URL; ResearchActivationSetupTest 1 lacks RESEARCH_ACTIVATION_SETUP. Lab v3 upload/annotation/review/export write E2E was not rerun in this preservation-focused task. Existing unit tests and prior local isolated validation are distinct evidence, not a substitute for Lab write E2E.
3. No real model was trained, activated or deployed. Existing collection/consent/model configuration was not changed by this task.
4. Physical TV, phone full-body motion and Pi repetition/camera/network tests intentionally NOT RUN, per user direction. No Pi access occurred.
5. DBA should remove the temporary CREATE/ALTER/INDEX/REFERENCES grants after acceptance; the Render account has no GRANT OPTION and this task did not change grants.
6. Render displays an existing `Payment failed` warning. The authorized deployment succeeded, but the account owner must resolve billing to avoid future service interruption; no payment/account changes were performed.
7. Final report is a new uncommitted backend documentation file. Business source, original migration files and Flutter remain unchanged; no branch/commit/push was created.
8. Git diff --check and package passed. Frontend master remains clean; backend main has only this new untracked report.

## Do not redo

- V004 and V005 are now applied to Lab. Do not rerun any V001-V005 migration or recreate/import the database.
- No DROP privilege is needed for these migrations; do not expand database grants.
- Do not use ddl-auto=update, enable research/model flags, or modify Pi/Flutter.
- Latest main is now Live and the listed read-only API smoke passed; do not expand this into claims of new model/clinical or physical-device acceptance.
