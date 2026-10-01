# 04 — SQL Server → MySQL 8.4 相容性清單

靜態掃描 `src/main/java`、Repository、Controller/Service、`pom.xml`、`application.properties` 與全部 SQL migration；未執行轉換或連線。行號以本機 `feat/rehab-ml-cloud-label` HEAD `239a64ae` 為準。此文件指出**待 Round 2 處理**的來源與風險，不是已完成 MySQL SQL。

| 來源：行數 | SQL Server 專屬／差異 | MySQL 8.4 重建要求 |
|---|---|---|
| `src/main/resources/application.properties:1-17` | `DB_URL`、SQLServerDriver、SQLServerDialect；`ddl-auto=update`；`show-sql=true` | 測試環境另設 MySQL JDBC URL/driver/dialect，正式 schema 建議受控 migration、禁任意 Hibernate update；核對時區、字元集與 SQL log 敏感資訊。`spring.sql.init.mode=never` (:21) 不會自動跑腳本 |
| `pom.xml:49-53` | Microsoft SQL Server JDBC driver | Round 2 才加 MySQL Connector/J 並決定保留/移除舊 driver；本輪不改 dependency |
| `src/main/java/com/example/trainingsystems/service/TrainingHistoryVideoService.java:192-203,259-266,312-314,358-365,430-444` | 原生 JDBC SQL 明寫 `dbo.training_history_video`；BLOB streaming，`SUBSTRING(video_data,?,?)` | 移除 `dbo.` 或明確 MySQL schema；用 MySQL 驗證 `SUBSTRING(LONGBLOB,start+1,length)` 二進位切片、分段下載、超大檔記憶體/封包上限、`setBinaryStream` 與交易隔離。這是**唯一找到的 SQL Server schema-qualified 原生 Java SQL**；其他 `@Query` 為 JPQL，不應盲改 |
| `entity/CustomRehabExerciseEntity.java:32-36,64-71` | `@Nationalized`、`nvarchar(max)` | MySQL `utf8mb4 VARCHAR`/`LONGTEXT`，移除 dialect-specific `columnDefinition` 前需保留非空/JSON 合約 |
| `entity/TrainingHistoryEntity.java:46-56,76-106`；`TrainingHistoryVideoEntity.java:25-55`；`TrainingSessionResultEntity.java:47-60`；`UserAvatarEntity.java:24-29` | `NVARCHAR(n/MAX)`、`VARBINARY(MAX)` | `VARCHAR(n)`/`LONGTEXT`/`LONGBLOB`，檢查中文字數與 utf8mb4 index bytes、JDBC streaming |
| `entity/ResearchSampleEntity.java:45`；`ResearchAnnotationEntity.java:25,52`；`ResearchAnnotationRevisionEntity.java:37,52` | 研究 JSON/備註 `nvarchar` | 僅研究分支；改 utf8mb4 LONGTEXT/VARCHAR，並維持既有 JSON validation |
| `sqlserver_migration_chat.sql:1-52` | `OBJECT_ID`/`sys`、`dbo.`、`IDENTITY`、`DATETIME2(7)`、`NVARCHAR`、CHECK、unique/index | 用 MySQL `CREATE TABLE IF NOT EXISTS` 等受控 migration；`AUTO_INCREMENT`/`DATETIME(6)`/utf8mb4；保留 canonical 與 type CHECK，驗證實際資料後再建 UNIQUE |
| `sqlserver_migration_rehab_plans.sql:1-64` | `GO`、`SYSUTCDATETIME()` (:10-13)、`BIT`、CHECK、cascade、`sys.indexes` | MySQL 不接受 `GO`；時區用 UTC 應用賦值或 `UTC_TIMESTAMP(6)`；保留 plan/item CHECK 與 item cascade；索引以 `information_schema`/migration 管理 |
| `sqlserver_migration_account_recovery.sql:8-52` | `DATETIME2(7)`；`uq_password_reset_active_user` 的 `WHERE consumed_at IS NULL` (:49-52) | MySQL 無 filtered index；可設 `active_user_id` generated column = `CASE WHEN consumed_at IS NULL THEN user_id ELSE NULL END` 並 UNIQUE；需驗證既存多 active rows、交易競態。保留 user cascade |
| `sqlserver_migration_m7_7_google_auth.sql:9-25` | `COL_LENGTH`、`ALTER COLUMN password`、google_subject filtered UNIQUE | MySQL `MODIFY COLUMN`；一般 UNIQUE nullable 欄允許多 NULL，但大小寫/collation 需固定；不能假定與 SQL Server subject 唯一語義完全相同 |
| `sqlserver_migration_m7_8_account_identity.sql:6-30` | `GO`、`EXEC(N'...')`、computed `LOWER(account_id) PERSISTED`、filtered UNIQUE | generated STORED column/功能索引或 case-insensitive collation；先決議 Entity 50 vs SQL 20；避免因 MySQL/SQL Server Unicode casefold 不同造成 ID 碰撞 |
| `sqlserver_migration_training_history_video.sql:7-137` | `SET XACT_ABORT/NOCOUNT`、`TRY/CATCH/THROW`、`OBJECT_ID/COL_LENGTH/sys.*`、`NVARCHAR(MAX)`/`VARBINARY(MAX)`、`WITH VALUES`、`TOP(1000)` verification | 以 MySQL migration runner transaction + metadata 查詢/驗證腳本重寫；非所有 MySQL DDL 可在交易中回滾；`LIMIT 1000` 取代 TOP；保留檢查舊資料、影片 FK cascade；不可以直接執行原 T-SQL |
| `sqlserver_migration_training_history_scores.sql:8-48` | `COL_LENGTH`、`NVARCHAR(MAX)`、`DEFAULT (0) WITH VALUES` | 受控加欄/backfill/NOT NULL；檢查既存 null；JSON 內容保持原字串語義 |
| `sqlserver_migration_user_avatar.sql:3-16` | `VARBINARY(MAX)`、`DATETIME2(3)`、`SYSUTCDATETIME()`、CHECK | `LONGBLOB`，MySQL `DATETIME(6)`；保留 source CHECK 與 FK，勿擅增 cascade |
| `sqlserver_migration_ml_research.sql:5-76` | `OBJECT_ID/sys.foreign_keys`、`DATETIME2`、`NVARCHAR(MAX)`、UNIQUE/無 cascade | **只供功能分支**；MySQL 研究表 DDL 必須獨立版本化；不可視為 Azure 已有 |
| `sqlserver_migration_ml_research_authority.sql:5-50`；`sqlserver_migration_ml_research_review.sql:1-38` | 增量授權/revision、`COL_LENGTH`、`IDENTITY`、`LABELED`→`DRAFT` 更新 | 依 base→authority/review 順序轉換，先備份並核對狀態列；review 狀態資料變更不可無條件重跑 |
| `sqlserver_migration_ml_research_export.sql:1-12`；`sqlserver_migration_ml_research_retention.sql:1-39` | 匯出審計、政策/事件；filtered expiry index (:38-39) | MySQL 普通 `expires_at` index 可包含 NULL（查非空到期仍可走 range scan）；驗證 query plan；不建立虛構正式 retention term |
| `sqlserver_bootstrap_first_research_manager.sql:1-27` | 受控首次授權使用 SQL Server 語法，非 schema migration | 另設人工審批的 MySQL bootstrap 程序；保留明確目標 ID、單次/審計保護，絕不可隨 schema migration 自動執行 |

## Query 分類與遷移前驗證

- `ChatMessageRepository.java:23,36`、`ChatConversationRepository.java:24`、`RehabPlanRepository.java:31`、`ResearchAnnotationRepository.java:20,23`、`ResearchSampleRepository.java:24` 等 `@Query` 是 **JPQL**（沒有 `nativeQuery=true`），應先跑 MySQL 整合測試，不要機械翻譯成 SQL。其餘多數 Repository 是 Spring Data 方法命名。
- `DATETIME2(7)` 比 MySQL `DATETIME(6)` 多一位小數；建立時區與精度規約，測 `created_at` 排序/邊界/冪等。`LocalDateTime` 無 offset；`Instant` JDBC 轉換需固定 UTC。
- SQL Server `NVARCHAR`/`VARCHAR`、collation/尾隨空白與 MySQL utf8mb4 collation/UNIQUE 行為不同。搬遷前對 email、account_id、friend_code、google_subject、plan_id、sample client ID 等所有唯一鍵做衝突檢查。
- SQL Server `BIT` 對 MySQL `TINYINT(1)`，MySQL 8.4 CHECK 可明定 0/1；不要將非 0/1 的歷史資料默認為 true。
- SQL Server `IDENTITY` 對 `AUTO_INCREMENT`；匯入舊 ID 後重新校正下一值。`DATETIME2`, `NVARCHAR(MAX)`, `VARBINARY(MAX)`, `PERSISTED`, `IDENTITY`, `GO`, `dbo.`, `OBJECT_ID`, `COL_LENGTH`, `sys.*`, `TOP`, `WITH VALUES`, `THROW` 都不可直接複製到 MySQL。
- `spring.jpa.hibernate.ddl-auto=update` 尤其無法忠實重建 filtered index、computed column、CHECK、FK 刪除行為；以檢查後的明確 DDL 為準。正式 SQL Server/真實 MySQL 尚未驗證。
