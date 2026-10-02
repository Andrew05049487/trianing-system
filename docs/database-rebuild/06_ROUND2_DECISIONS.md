# 06 — Round 2 Fresh MySQL Schema 決策紀錄

## 範圍、版本與狀態

- 日期：2026-10-02。後端 `feat/rehab-ml-cloud-label`，HEAD `d6fbb2e548e829823ce501d0ae3cf9ea47b3f28c`；開始工作樹乾淨。本機 `main` 可讀，HEAD `94132993d7d32ffc4b090ec429975fa3543275f5`，未切分支。
- Flutter `feat/rehab-ml-poc`，HEAD `429f200cefe085cbb72c8d612146cd296ec16045`；既有 `android/build/reports/problems/problems-report.html` 修改完整保留。
- 已讀 01～05；DDL 前重新核對 29 Entity、AccountService/AuthService、關鍵 SQL Server migrations 與 Flutter CUSTOM JSON。29 表不是舊 Azure 精確副本，更不代表研究表已部署。
- **Fresh Database Rebuild** 取代 01～05 中「先取得 Azure metadata／搬遷舊 ID／本輪改 JDBC」的舊交接要求。01～05 保持原樣供追溯。
- 本輪只新增 `docs/database-rebuild/` 文件、SQL 及無第三方依賴的靜態驗證工具；不修改 Java、Dart、舊 SQL、設定；不啟動 Spring Boot 或 Hibernate update。
- 靜態檢查 PASS；MySQL 執行及應用整合 **NOT TESTED**，詳見 07。環境有 MySQL 8.4.11，但 CLI 無可用登入憑證。

## 建表與版本管理

1. `mysql/V001__main_schema.sql`：主系統 **18 表**。
2. `mysql/V002__research_schema.sql`：選配研究 **11 表**，依賴 V001，累計 **29 表**。
3. `mysql/validate_schema.sql`：讀取 information_schema，`@r2_include_research=0/1` 控制 18/29 表期望。

執行前確認目標是全新隔離庫，名稱若已存在必須停止。本輪沒有建立任何資料庫。SQL 不建立 database、不 USE 未知 database；操作者需明確選定全新庫。使用普通 CREATE TABLE，**沒有 IF NOT EXISTS、DROP、清空資料、關閉 FK、種子資料或自動 bootstrap**。重跑遇到已存在表是失敗，不是成功。

一般 SQL 檔以 V001/V002 與檔案 SHA-256 做審核版本；操作者在受控變更紀錄保存 schema 名稱、時間、檔案雜湊、完成步驟與驗證輸出。已套用版本不得原地修改或再次執行；後續另建 V003+。沒有另加 schema-history 表，維持 18/29 表範圍。Round 3 可採 migration framework，但不能無檢查地 baseline 任意既有庫。

MySQL DDL 會 implicit commit；整批失敗不會自動回滾。禁用 mysql `--force`；Workbench 關閉遇錯仍繼續。失敗保留現況，記錄已成功表／錯誤，人工審查後決定如何處理，不自動 DROP 或拿 IF NOT EXISTS 掩蓋半成品。

## 型別、Collation 與時間

| 類型／語義 | MySQL 決策 | 原因／限制 |
|---|---|---|
| Java Long／SQL BIGINT | signed BIGINT；IDENTITY 對 AUTO_INCREMENT | 不擅自改 UNSIGNED；保留 Java Long 與 FK 相容性 |
| 一般文字 | utf8mb4；`utf8mb4_0900_as_cs` | 區分大小寫與重音，避免研究／動作／版本識別碼非預期合併；不是 Azure collation 推定 |
| email | VARCHAR(255), `utf8mb4_0900_as_ci`, UNIQUE | 與 AuthService email trim/lower normalization 相符；重音不折疊；國際化 email 仍需 Round 3 測試 |
| account_id／normalized | VARCHAR(50), `utf8mb4_0900_as_ci` | 現有 ID 僅 ASCII 英數；LOWER generated 與 CI 查詢一致 |
| google_subject | VARCHAR(255), `utf8mb4_0900_bin`, nullable UNIQUE | 外部 stable identity 精確比對；不能用 email collation 任意合併 subject |
| NVARCHAR(n) | VARCHAR(n) utf8mb4 | 中文可保存；不得用原 SQL Server byte count 解釋 MySQL字元數 |
| NVARCHAR(MAX)／JSON 字串 | LONGTEXT utf8mb4 | 延續目前 String/Jackson payload，而非擅改 native JSON；不自行加 JSON_VALID CHECK |
| VARBINARY(MAX)／影片／頭像 | LONGBLOB | 獨立表；容量不等於允許任意大 request，仍受 service limit、max_allowed_packet、JDBC/heap 約束 |
| SQL BIT | TINYINT(1)，可空性照來源；CHECK IN(0,1) | 顯式保留 BIT 的二值範圍；nullable Boolean 仍允許 NULL |
| DATETIME2／Instant／LocalDateTime | DATETIME(6)；DATE 不變 | MySQL 最大微秒，比 DATETIME2(7) 少一位；DATETIME 不自動轉時區，必須固定 JDBC/session UTC |
| float設定 | DOUBLE | 對應 Java Double；沒有改成有損整數或重算 duration/hold |
| 分數 | DECIMAL(5,2) | 對應現有 BigDecimal；不新加醫療／分數閾值 CHECK |

每表指定 InnoDB/utf8mb4/collation；所有字串 FK 與父 PK 型別／長度／collation 相同。已有明名 Entity UNIQUE/INDEX 保留名稱、順序；MySQL 自動補 FK supporting index 屬正常 metadata，驗證時另外列出供審查。索引總長按 utf8mb4 最多 4 bytes/字元保守檢查，小於 InnoDB 3072 bytes 上限（伺服器 page-size 等實際環境仍待確認）。

## 必須解決的衝突與決策

### 1. account_id：Entity 50、舊 migration 20、業務驗證 20

- `entity/User.java:48-50` 是 length=50；`sqlserver_migration_m7_8_account_identity.sql:6-30` 使用 VARCHAR(20) 與 LOWER PERSISTED。
- `service/AccountService.java:28` 使用 `^[A-Za-z0-9]{4,20}$`；`AuthService.java:229-234` trim/lower。
- 新 schema **採 50 配合 Entity，不放寬應用4～20驗證**。產生相同長度的 `account_id_normalized` STORED generated，唯一索引允許多 NULL。
- 正規化不在 DB 擅增 TRIM（目前原 computed 僅 LOWER）。前端不能直接寫入 generated 欄。Raw account_id 沒有第二個重複 UNIQUE。

### 2. Filtered UNIQUE 等效方案

- `users.google_subject`、`users.account_id_normalized` 以 nullable UNIQUE 實作，多 NULL 合法，非 NULL 仍唯一。
- `sqlserver_migration_account_recovery.sql:49-52` 的 `WHERE consumed_at IS NULL` 改成額外 **VIRTUAL** 欄：`active_user_id = CASE WHEN consumed_at IS NULL THEN user_id ELSE NULL END`，UNIQUE(active_user_id)。
- 未使用包含已過期但未消耗的請求，完全依原 predicate，不擅自依 NOW()/expires_at 改語義。Consumed rows 不占唯一位置；交易競態仍由 UNIQUE 阻擋。
- 不用 STORED：MySQL 限制 STORED generated 的 base column 使用 FK CASCADE；VIRTUAL 避免與原 `password_reset_requests.user_id ON DELETE CASCADE` 衝突。這個組合的真實 MySQL執行／cascade／競態驗證尚未完成，不能用靜態 PASS 代替。

### 3. poseMeasurementRules 資料契約缺口

- Flutter `lib/models/custom_rehab_exercise.dart:19,117-118,135,160` 有此 JSON欄。
- 後端 `CustomRehabExerciseDto`、`CustomRehabExerciseEntity`、`CustomRehabExerciseService` 目前只有 keyframes/evaluationRules 的對應持久化；沒有 pose rules 欄位。
- **本輪不加無讀寫者的 DB 欄位**。Round 3 先決議 DTO／序列化／Entity／service 合約，再以新 migration 加 nullable `pose_measurement_rules_json LONGTEXT`（候選方案，尚未採用）。舊紀錄與沒有規則的動作須有明確 `[]` fallback；不能拿 legacy GLB XYZ 規則替代人體規則。

### 4. exercise_result.created_at

- `entity/ExerciseResult.java:42-44` `insertable=false, updatable=false`；舊 source 沒有證實 Azure預設生成機制。
- Fresh schema明確採 **DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6))**，避免 Java省略 INSERT欄後產生 NULL。這是新建庫設計，不是原Azure已確認事實。
- Round 3 測 INSERT省略時間 → DB生成UTC → flush/reload讀回。Entity nullable metadata與新欄NN不同要確認；不要假設 Hibernate save後 Java物件已即時回填。
- rehab_plans兩個時間、user_avatars.updated_at 的 SQL Server SYSUTCDATETIME預設亦用UTC expression；不加 ON UPDATE CURRENT_TIMESTAMP，保留app更新時間語義。

### 5. SQL DEFAULT 與 Java初始值分離

只保留 Migration已確認的0預設：password_reset_requests.failed_attempts、rehab_plan_items.done、training_history.completed_reps/template_valid_rep_count、research_annotations.revision；上述UTC時間預設如前節。

**沒有**角色PATIENT、friend_request PENDING、active=true、reps/sets/hold/rest、研究status/grants、retention天數的SQL預設。Java `@PrePersist`／欄位初始值仍由app提供。ResearchAnnotationEntity `status="LABELED"` 與service第三輪狀態邏輯有差異；Fresh schema不seed、不跑 LABELED→DRAFT UPDATE，也不設LABELED default。

### 6. FK與刪除

- 33 FK，只有3條 ON DELETE CASCADE：password_reset_requests→users、rehab_plan_items→rehab_plans、training_history_video→training_history。其餘保留 restrictive 行為；ON UPDATE亦restrictive。
- Chat participant CHECK 欄的 FK不顯式指定action clauses，使用MySQL/InnoDB default NO ACTION（等效RESTRICT），避免CHECK與referential actions限制；驗證SQL接受metadata NO ACTION/RESTRICT等效。
- `AccountService.java:173-204` 未完整清除chat／plans／history／session_results。**沒有全域CASCADE掩蓋此問題**，Round 3帳號刪除整合測試是上線blocker。
- `rehab_plan_items.exercise_id` VARCHAR(100)文字識別；`training_session_results.exercise_id` VARCHAR(128)多型識別，都不加錯誤FK。
- Research subject_id、reviewer_user_id、audit actor／target、retention event/policy操作人員按來源保留無FK；不是忘了做約束。
- `research_annotation_revisions(sample_id,revision)` 只有普通INDEX，非UNIQUE。

### 7. CHECK／普通INDEX

保留chat順序/type、plan condition、item order/sets/reps、video正size、avatar source等原SQL CHECK。補8個BIT等效0/1 CHECK，共16個，不新增未經證實的業務範圍。沒有修改評分、訓練設定或label合法值。

研究 `expires_at` filtered index改普通B-tree，NULL也可被索引；查 `expires_at IS NOT NULL AND expires_at<=...` 應在Round 3 EXPLAIN驗證。SQL條件唯一性不是用普通index替代。

## 來源追溯：29表

Java相對根路徑 `src/main/java/com/example/trainingsystems/entity/`；未列migration者主要由Entity形成現有預期。

| DDL表群 | Entity來源 | SQL Server來源 |
|---|---|---|
| users | User.java | m7_7_google_auth、m7_8_account_identity |
| exercise／exercise_result | Exercise.java／ExerciseResult.java | 無本repo完整base DDL；採Entity＋已註明fresh時間決策 |
| user_bindings／friend_requests／friendships | UserBinding.java／FriendRequest.java／Friendship.java | Entity關聯與UNIQUE |
| password_reset_requests | PasswordResetCredential.java | account_recovery |
| chat_conversations／chat_messages | ChatConversationEntity.java／ChatMessageEntity.java | chat |
| custom_rehab_exercises／custom_exercise_assignments／exercise_assignments | CustomRehabExerciseEntity.java／CustomExerciseAssignmentEntity.java／ExerciseAssignmentEntity.java | Entity欄位／關聯／索引 |
| rehab_plans／rehab_plan_items | RehabPlanEntity.java／RehabPlanItemEntity.java | rehab_plans |
| training_history／training_history_video | TrainingHistoryEntity.java／TrainingHistoryVideoEntity.java | training_history_video、training_history_scores |
| training_session_results | TrainingSessionResultEntity.java | Entity |
| user_avatars | UserAvatarEntity.java | user_avatar |
| research_consents／research_samples／research_annotations／research_audit | 同名Research*Entity.java | ml_research + review/retention對samples/annotations增量 |
| research_grants／research_review_requests／research_grant_audit | 同名Research*Entity.java | ml_research_authority |
| research_annotation_revisions | ResearchAnnotationRevisionEntity.java | ml_research_review |
| research_export_audit | ResearchExportAuditEntity.java | ml_research_export |
| research_retention_policies／research_retention_events | 同名Research*Entity.java | ml_research_retention |

上述SQL簡稱均為根目錄 `sqlserver_migration_<名稱>.sql`。研究5組incremental最終結構整合為V002，沒有認定Azure已部署。

## 尚未證實／人工確認

1. **NOT TESTED**：MySQL真正解析、CHECK／generated UNIQUE／cascade／DML／concurrency／Information Schema validation。
2. Source只能證實預期欄位；**未確認**旧Azure實際結構／collation／索引，Fresh決策不需要Azure存取。
3. 主schema沒有exercise種子。DEFAULT依賴資料庫exercise catalog，Round 3須先審核既有ID/API/Flutter動作mapping，再寫獨立seed，不能只建空表就說App全功能可用。
4. 全研究Entities仍在feature branch；即使只執行V001，現有research-enabled backend也不能直接宣稱可啟動。Round 3須明確選18表非研究版本／停用研究repositories，或部署29表配合backend；不能開ddl-auto=update讓它偷偷補表。
5. Backend仍是SQL Server設定，尚不能接這套schema直接驗收App；JDBC、columnDefinition、UTC、刪帳號、pose rules缺口、BLOB必須Round 3處理。
6. Nullable字段、沒有來源支持的關係均沒有憑空加NOT NULL／FK／seed。正式保留期限未設定、研究收集仍不得啟用。

## 官方依據（僅語法／設計參考，不代表本機SQL執行通過）

- [MySQL generated columns 與FK限制](https://dev.mysql.com/doc/refman/8.4/en/create-table-generated-columns.html)
- [CHECK規則與FK referential action限制](https://dev.mysql.com/doc/refman/8.4/en/create-table-check-constraints.html)
- [expression DEFAULT](https://dev.mysql.com/doc/refman/8.4/en/data-type-defaults.html)
- [UNIQUE／索引](https://dev.mysql.com/doc/refman/8.4/en/create-index.html)
- [InnoDB foreign keys](https://dev.mysql.com/doc/refman/8.4/en/create-table-foreign-keys.html)
