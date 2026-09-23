# ML Cloud Backend Progress

Branch `feat/rehab-ml-cloud-label` starts from clean `main` `94132993d7d32ffc4b090ec429975fa3543275f5`. Flutter companion is `feat/rehab-ml-poc` at `ad1f0bf13b3ebb6828aa69cb9f62b8bd7cfee546`.

Stage 1 secure minimum backend implemented: versioned patient consent with server UUID, validated/pseudonymized skeleton samples, stable idempotent upload, patient own list/detail/delete, bound-therapist list/detail/label, account deletion cleanup, additive SQL migration. Research collection defaults OFF. No deploy or production SQL.

Tests: `mvn -q '-Dtest=ResearchDataServiceTest,AccountServiceTest' test` passed 12 + 14; `mvn -q -DskipTests package` passed. Rerun `git diff --check` before commit.

Next: commit backend slice; implement Flutter patient cloud consent/sync and therapist list/detail/label/player; focused tests and Flutter commit. Later: authorized reviewer/manager role, review, export, statistics and retention. No real labeled data or trained model.
