# 01 — 資料表與欄位盤點

來源：本機後端 `main` (`94132993`) 與 `feat/rehab-ml-cloud-label` (`239a64ae`) 的全部 29 個 `@Entity`、Repository、服務及 SQL migration。**沒有連線 Azure；本文件不是現場資料庫 introspection。** 以下 `M` 表示 SQL migration 明定的 SQL Server 型別；`E` 表示只有 Entity/JPA 宣告，實際 Azure 型別、長度、DEFAULT、索引需查 `sys.columns` 等目錄確認。`E` 後的 SQL 型別僅為 JPA 意圖／推定，**待確認**。NULL 以 migration 優先，否則依 Entity `nullable`/primitive。MySQL 建議統一 `utf8mb4`；`Instant`／`LocalDateTime` 建議 `DATETIME(6)`（應用層明定 UTC）；`LocalDate`→`DATE`、`BIT`→`TINYINT(1)`、`NVARCHAR(MAX)`→`LONGTEXT`、`VARBINARY(MAX)`→`LONGBLOB`。Java 初值或 `@PrePersist` 不等於 SQL DEFAULT。表內每行格式：`欄名 : SQL Server 型別 → MySQL 8.4 型別 [NULL?]`。

## A. `main` 的 18 個預期表（不能據此斷言每表已在 Azure 建立）

### `users` — 帳號與單一基本角色

`id: E BIGINT IDENTITY → BIGINT AUTO_INCREMENT [NO,PK]`; `email: E VARCHAR(255) → VARCHAR(255) [NO,UNIQUE]`; `password: M VARCHAR(255) → VARCHAR(255) [YES]`; `name: E VARCHAR(255) → VARCHAR(255) [YES]`; `goal: E VARCHAR(255) → VARCHAR(255) [YES]`; `role: E VARCHAR(20) → VARCHAR(20) [NO]`; `binding_code: E VARCHAR(12) → VARCHAR(12) [YES]`; `friend_code: E VARCHAR(12) → VARCHAR(12) [YES,UNIQUE]`; `account_id: **M VARCHAR(20)、Entity length=50 衝突** → VARCHAR(20/50，待決議) [YES]`; `account_id_normalized: M computed LOWER(account_id) PERSISTED → generated STORED 或指定大小寫規則 [YES]`; `google_subject: M VARCHAR(255) → VARCHAR(255) [YES,非空唯一]`。`account_id_normalized` 與 `google_subject` 用 SQL Server filtered UNIQUE，非單純 JPA unique。`role=PATIENT` 是 Java 初值。來源：`User.java`、`sqlserver_migration_m7_7_google_auth.sql`、`sqlserver_migration_m7_8_account_identity.sql`。沒有完整 users 建表腳本。

### `exercise` — DB 預設動作目錄

`id: E BIGINT IDENTITY → BIGINT AUTO_INCREMENT [NO,PK]`; `exercise_name: E VARCHAR(255) → VARCHAR(255) [NO]`; `description: E VARCHAR(255) → VARCHAR(255) [YES]`。實際文字型別/長度待確認。來源：`Exercise.java`；無建表 migration。

### `exercise_result` — 舊版預設動作結果

`id: E BIGINT IDENTITY → BIGINT AUTO_INCREMENT [NO,PK]`; `user_id: E BIGINT → BIGINT [NO,FK users]`; `exercise_id: E BIGINT → BIGINT [NO,FK exercise]`; `rep_count: E INT → INT [NO]`; `accuracy: E DECIMAL(5,2) → DECIMAL(5,2) [YES]`; `progress: E DECIMAL(5,2) → DECIMAL(5,2) [YES]`; `speed_state: E VARCHAR(255) → VARCHAR(255) [YES]`; `is_complete: E BIT → TINYINT(1) [YES]`; `created_at: E DATETIME2 → DATETIME(6) [YES?]`。`created_at` 在 Entity 為 `insertable=false,updatable=false`，DB DEFAULT/NULL 實際待確認。來源：`ExerciseResult.java`。

### `user_bindings` — 病患與治療師等綁定

`id: E BIGINT IDENTITY → BIGINT AUTO_INCREMENT [NO,PK]`; `patient_id: E BIGINT → BIGINT [NO,FK users]`; `linked_user_id: E BIGINT → BIGINT [NO,FK users]`; `relationship: E VARCHAR(20) → VARCHAR(20) [NO]`; `created_at: E DATETIME2 → DATETIME(6) [NO]`。UNIQUE `(patient_id,linked_user_id)`；時間由 Java `@PrePersist` 補，不是已證實 SQL DEFAULT。來源：`UserBinding.java`。

### `friend_requests` — 好友邀請

`id: E BIGINT IDENTITY → BIGINT AUTO_INCREMENT [NO,PK]`; `sender_id: E BIGINT → BIGINT [NO,FK users]`; `receiver_id: E BIGINT → BIGINT [NO,FK users]`; `status: E VARCHAR(20) → VARCHAR(20) [NO]`; `created_at: E DATETIME2 → DATETIME(6) [NO]`; `responded_at: E DATETIME2 → DATETIME(6) [YES]`。UNIQUE `(sender_id,receiver_id)`；`PENDING`/時間為 Java 初值。來源：`FriendRequest.java`。

### `friendships` — 已建立好友

`id: E BIGINT IDENTITY → BIGINT AUTO_INCREMENT [NO,PK]`; `user_low_id: E BIGINT → BIGINT [NO,FK users]`; `user_high_id: E BIGINT → BIGINT [NO,FK users]`; `created_at: E DATETIME2 → DATETIME(6) [NO]`。UNIQUE `(user_low_id,user_high_id)`；資料庫 low<high CHECK 未見。來源：`Friendship.java`。

### `password_reset_requests` — 重設碼雜湊

`id: M VARCHAR(36) → VARCHAR(36) [NO,PK]`; `user_id: M BIGINT → BIGINT [NO,FK users,ON DELETE CASCADE]`; `code_hash: M VARCHAR(64) → VARCHAR(64) [NO]`; `expires_at: M DATETIME2(7) → DATETIME(6) [NO]`; `failed_attempts: M INT → INT [NO,DEFAULT 0]`; `consumed_at: M DATETIME2(7) → DATETIME(6) [YES]`; `created_at: M DATETIME2(7) → DATETIME(6) [NO]`; `version: M BIGINT → BIGINT [YES,@Version]`。索引 `(user_id,created_at)`、`expires_at`；filtered UNIQUE `user_id WHERE consumed_at IS NULL`，MySQL 必須另設計 active generated key。來源：`sqlserver_migration_account_recovery.sql`。

### `chat_conversations` — 一對一聊天室

`id: M BIGINT IDENTITY → BIGINT AUTO_INCREMENT [NO,PK]`; `participant_one_id: M BIGINT → BIGINT [NO,FK users]`; `participant_two_id: M BIGINT → BIGINT [NO,FK users]`; `conversation_type: M VARCHAR(20) → VARCHAR(20) [NO]`; `created_at: M DATETIME2(7) → DATETIME(6) [NO]`; `updated_at: M DATETIME2(7) → DATETIME(6) [NO]`; `last_message_text: M NVARCHAR(2000) → VARCHAR(2000) [YES]`; `last_message_at: M DATETIME2(7) → DATETIME(6) [YES]`。UNIQUE `(participant_one_id,participant_two_id,conversation_type)`；索引兩個 participant；CHECK participant_one<participant_two、type in (`THERAPIST`,`PEER`)。來源：`sqlserver_migration_chat.sql`。

### `chat_messages` — 訊息與已讀

`id: M BIGINT IDENTITY → BIGINT AUTO_INCREMENT [NO,PK]`; `conversation_id: M BIGINT → BIGINT [NO,FK chat_conversations]`; `sender_id: M BIGINT → BIGINT [NO,FK users]`; `text: M NVARCHAR(2000) → VARCHAR(2000) [NO]`; `sent_at: M DATETIME2(7) → DATETIME(6) [NO]`; `read_at: M DATETIME2(7) → DATETIME(6) [YES]`。索引 `(conversation_id,sent_at)`、`(conversation_id,read_at)`；無 ON DELETE CASCADE。來源：`sqlserver_migration_chat.sql`。

### `custom_rehab_exercises` — 3D 自訂動作與 JSON

`id: E VARCHAR(128) → VARCHAR(128) [NO,PK]`; `name: E NVARCHAR(200) → VARCHAR(200) [NO]`; `description: E NVARCHAR(2000) → VARCHAR(2000) [NO]`; `created_by_therapist_id: E BIGINT → BIGINT [NO,FK users]`; `created_at: E DATETIME2 → DATETIME(6) [NO]`; `updated_at: E DATETIME2 → DATETIME(6) [NO]`; `repetitions: E INT → INT [NO]`; `sets: E INT → INT [NO]`; `hold_seconds: E FLOAT(53) → DOUBLE [NO]`; `rest_seconds: E FLOAT(53) → DOUBLE [NO]`; `duration: E FLOAT(53) → DOUBLE [NO]`; `keyframes_json: E NVARCHAR(MAX) → LONGTEXT [NO]`; `evaluation_rules_json: E NVARCHAR(MAX) → LONGTEXT [NO]`。索引 `created_by_therapist_id`。無完整 migration，實際 SQL 型別待確認。Flutter model 有 `poseMeasurementRules`，但此 Entity/後端 DTO 無同名欄或欄位，需 round-trip 實測，**不可假定已入庫**。來源：`CustomRehabExerciseEntity.java`、`CustomRehabExerciseDto.java`、Flutter `custom_rehab_exercise.dart`。

### `custom_exercise_assignments` — 自訂動作指派

`id: E BIGINT IDENTITY → BIGINT AUTO_INCREMENT [NO,PK]`; `custom_exercise_id: E VARCHAR(128) → VARCHAR(128) [NO,FK custom_rehab_exercises]`; `patient_id: E BIGINT → BIGINT [NO,FK users]`; `assigned_by_therapist_id: E BIGINT → BIGINT [NO,FK users]`; `assigned_at: E DATETIME2 → DATETIME(6) [NO]`; `is_active: E BIT → TINYINT(1) [NO]`。UNIQUE `(custom_exercise_id,patient_id)`；索引 `(patient_id,is_active)`、`assigned_by_therapist_id`；active=true 是 Java 初值。來源：Entity。

### `exercise_assignments` — 預設動作指派

`id: E BIGINT IDENTITY → BIGINT AUTO_INCREMENT [NO,PK]`; `exercise_id: E BIGINT → BIGINT [NO,FK exercise]`; `patient_id: E BIGINT → BIGINT [NO,FK users]`; `assigned_by_therapist_id: E BIGINT → BIGINT [NO,FK users]`; `assigned_at: E DATETIME2 → DATETIME(6) [NO]`; `is_active: E BIT → TINYINT(1) [NO]`。UNIQUE `(exercise_id,patient_id)`；索引 `(patient_id,is_active)`、`assigned_by_therapist_id`。來源：Entity。

### `rehab_plans` — 病患每日計畫

`id: M BIGINT IDENTITY → BIGINT AUTO_INCREMENT [NO,PK]`; `plan_id: M NVARCHAR(100) → VARCHAR(100) [NO,UNIQUE]`; `patient_id: M BIGINT → BIGINT [NO,FK users]`; `created_by: M NVARCHAR(50) → VARCHAR(50) [NO]`; `plan_date: M DATE → DATE [NO]`; `condition_type: M NVARCHAR(20) → VARCHAR(20) [NO]`; `created_at: M DATETIME2(7) → DATETIME(6) [NO,DEFAULT SYSUTCDATETIME()]`; `updated_at: M DATETIME2(7) → DATETIME(6) [NO,同 DEFAULT]`。UNIQUE `(patient_id,plan_date)`；索引同欄；CHECK condition in (`fracture`,`stroke`)。來源：`sqlserver_migration_rehab_plans.sql`。

### `rehab_plan_items` — 計畫項目

`id: M BIGINT IDENTITY → BIGINT AUTO_INCREMENT [NO,PK]`; `rehab_plan_id: M BIGINT → BIGINT [NO,FK rehab_plans,ON DELETE CASCADE]`; `exercise_id: M NVARCHAR(100) → VARCHAR(100) [NO]`; `item_order: M INT → INT [NO,CHECK >=0]`; `sets: M INT → INT [NO,CHECK >0]`; `reps_per_set: M INT → INT [NO,CHECK >0]`; `done: M BIT → TINYINT(1) [NO,DEFAULT 0]`。UNIQUE `(rehab_plan_id,exercise_id)`；索引 `(rehab_plan_id,item_order)`。`exercise_id` 是文字識別，沒有對 `exercise.id` 的 FK。來源：`sqlserver_migration_rehab_plans.sql`。

### `training_history` — 自由訓練歷史與評分

`id: E BIGINT IDENTITY → BIGINT AUTO_INCREMENT [NO,PK]`; `user_id: E BIGINT → BIGINT [NO,FK users]`; `session_id: E NVARCHAR(100) → VARCHAR(100) [YES]`; `action_name: M NVARCHAR(100) → VARCHAR(100) [NO]`; `difficulty: E INT → INT [NO]`; `duration_seconds: E INT → INT [NO]`; `completed_reps: M INT → INT [NO,DEFAULT 0]`; `target_reps: E INT → INT [NO]`; `mistake_count: E INT → INT [NO]`; `mistake_logs: M NVARCHAR(MAX) → LONGTEXT [YES]`; `body_score: M DECIMAL(5,2) → DECIMAL(5,2) [YES]`; `body_rep_scores: M NVARCHAR(MAX) → LONGTEXT [YES]`; `template_score: M DECIMAL(5,2) → DECIMAL(5,2) [YES]`; `template_id: M VARCHAR(160) → VARCHAR(160) [YES]`; `template_name: M NVARCHAR(200) → VARCHAR(200) [YES]`; `template_valid_rep_count: M INT → INT [NO,DEFAULT 0]`; `template_rep_scores: M NVARCHAR(MAX) → LONGTEXT [YES]`; `template_difference_summary: M NVARCHAR(MAX) → LONGTEXT [YES]`; `client_timestamp: E VARCHAR(32) → VARCHAR(32) [NO]`; `created_at: E DATETIME2 → DATETIME(6) [NO]`。UNIQUE `(user_id,client_timestamp)`；索引 `(user_id,created_at DESC)`。原始完整建表 DDL 不在 repo。來源：`TrainingHistoryEntity.java`、`sqlserver_migration_training_history_video.sql`、`sqlserver_migration_training_history_scores.sql`。

### `training_history_video` — 訓練影片位元組

`history_id: M BIGINT → BIGINT [NO,PK/FK training_history,ON DELETE CASCADE]`; `file_name: M NVARCHAR(255) → VARCHAR(255) [YES]`; `content_type: M NVARCHAR(100) → VARCHAR(100) [NO]`; `file_size: M BIGINT → BIGINT [NO,CHECK >0]`; `video_data: M VARBINARY(MAX) → LONGBLOB [NO]`; `created_at: M DATETIME2 → DATETIME(6) [NO]`; `updated_at: M DATETIME2 → DATETIME(6) [YES]`。影片服務使用 JDBC streaming。來源：`sqlserver_migration_training_history_video.sql`。

### `training_session_results` — 完整訓練 session 結果

`id: E BIGINT IDENTITY → BIGINT AUTO_INCREMENT [NO,PK]`; `session_id: E VARCHAR(36) → VARCHAR(36) [NO,UNIQUE]`; `patient_id: E BIGINT → BIGINT [NO,FK users]`; `exercise_type: E VARCHAR(16) → VARCHAR(16) [NO]`; `exercise_id: E NVARCHAR(128) → VARCHAR(128) [NO]`; `exercise_name: E NVARCHAR(255) → VARCHAR(255) [NO]`; `completed_sets: E INT → INT [NO]`; `completed_reps: E INT → INT [NO]`; `target_sets: E INT → INT [NO]`; `target_reps: E INT → INT [NO]`; `started_at: E DATETIME2 → DATETIME(6) [NO]`; `completed_at: E DATETIME2 → DATETIME(6) [NO]`; `duration_seconds: E BIGINT → BIGINT [NO]`; `completion_status: E VARCHAR(24) → VARCHAR(24) [NO]`; `score: E DECIMAL(5,2) → DECIMAL(5,2) [NO]`; `created_at: E DATETIME2 → DATETIME(6) [NO]`。索引 `(patient_id,completed_at)`；`exercise_id` 是 DEFAULT/CUSTOM 多型文字鍵，無 FK。來源：`TrainingSessionResultEntity.java`。

### `user_avatars` — 使用者圖片

`user_id: M BIGINT → BIGINT [NO,PK/FK users]`; `mime_type: M VARCHAR(32) → VARCHAR(32) [NO]`; `image_data: M VARBINARY(MAX) → LONGBLOB [NO]`; `source_type: M VARCHAR(16) → VARCHAR(16) [NO]`; `updated_at: M DATETIME2(3) → DATETIME(6) [NO,DEFAULT SYSUTCDATETIME()]`。CHECK source in (`CUSTOM`,`GOOGLE`)；FK 無 cascade，AccountService 清理。來源：`sqlserver_migration_user_avatar.sql`。

## B. 僅功能分支的 11 個研究表

以下是**未證實在 Azure 執行**的增量 migration，不能列為 main 正式庫已存在的表。SQL 型別均為 migration 明定（M）。

### `research_consents` — 每位參與者的雲端研究同意

`user_id: M BIGINT → BIGINT [NO,PK/FK users]`; `subject_id: M VARCHAR(36) → VARCHAR(36) [NO,UNIQUE]`; `consent_version: M VARCHAR(64) → VARCHAR(64) [NO]`; `active: M BIT → TINYINT(1) [NO]`; `consented_at: M DATETIME2 → DATETIME(6) [YES]`; `withdrawn_at: M DATETIME2 → DATETIME(6) [YES]`。無 cascade。來源：`sqlserver_migration_ml_research.sql`。

### `research_samples` — 匿名骨架時序 JSON

`id: M VARCHAR(36) → VARCHAR(36) [NO,PK]`; `participant_user_id: M BIGINT → BIGINT [NO,FK users]`; `subject_id: M VARCHAR(36) → VARCHAR(36) [NO]`; `client_sample_id: M VARCHAR(100) → VARCHAR(100) [NO]`; `captured_at: M DATETIME2 → DATETIME(6) [NO]`; `uploaded_at: M DATETIME2 → DATETIME(6) [NO]`; `movement_side: M VARCHAR(8) → VARCHAR(8) [NO]`; `camera_view: M VARCHAR(8) → VARCHAR(8) [NO]`; `payload_json: M NVARCHAR(MAX) → LONGTEXT [NO]`; `client_payload_hash: M VARCHAR(64) → VARCHAR(64) [NO]`; `retention_policy_version: M VARCHAR(64) → VARCHAR(64) [YES]`; `expires_at: M DATETIME2 → DATETIME(6) [YES]`。UNIQUE `(participant_user_id,client_sample_id)`；索引 `(participant_user_id,captured_at)`、filtered `expires_at WHERE expires_at IS NOT NULL`。`subject_id` 與 consent 是邏輯關聯，無 FK。來源：基礎與 retention SQL。

### `research_annotations` — 目前標註與審核狀態

`sample_id: M VARCHAR(36) → VARCHAR(36) [NO,PK/FK research_samples]`; `therapist_user_id: M BIGINT → BIGINT [NO,FK users]`; `label: M VARCHAR(32) → VARCHAR(32) [NO]`; `note: M NVARCHAR(1000) → VARCHAR(1000) [YES]`; `label_version: M VARCHAR(64) → VARCHAR(64) [NO]`; `action_definition_version: M VARCHAR(64) → VARCHAR(64) [NO]`; `status: M VARCHAR(16) → VARCHAR(16) [NO]`; `updated_at: M DATETIME2 → DATETIME(6) [NO]`; `revision: M INT → INT [NO,DEFAULT 0]`; `submitted_at: M DATETIME2 → DATETIME(6) [YES]`; `reviewer_user_id: M BIGINT → BIGINT [YES]`; `reviewed_at: M DATETIME2 → DATETIME(6) [YES]`; `review_note: M NVARCHAR(1000) → VARCHAR(1000) [YES]`。reviewer ID 沒有實體 FK；review migration 將舊 `LABELED` 改成 `DRAFT`。來源：基礎與 review SQL。

### `research_annotation_revisions` — 版本快照

`id: M BIGINT IDENTITY → BIGINT AUTO_INCREMENT [NO,PK]`; `sample_id: M VARCHAR(36) → VARCHAR(36) [NO,FK research_samples]`; `revision: M INT → INT [NO]`; `actor_user_id: M BIGINT → BIGINT [NO]`; `annotator_user_id: M BIGINT → BIGINT [NO]`; `label: M VARCHAR(32) → VARCHAR(32) [NO]`; `note: M NVARCHAR(1000) → VARCHAR(1000) [YES]`; `label_version: M VARCHAR(64) → VARCHAR(64) [NO]`; `action_definition_version: M VARCHAR(64) → VARCHAR(64) [NO]`; `status: M VARCHAR(16) → VARCHAR(16) [NO]`; `reviewer_user_id: M BIGINT → BIGINT [YES]`; `review_note: M NVARCHAR(1000) → VARCHAR(1000) [YES]`; `created_at: M DATETIME2 → DATETIME(6) [NO]`。索引 `(sample_id,revision)`，但 SQL 無同欄 UNIQUE。來源：`sqlserver_migration_ml_research_review.sql`。

### `research_audit` — 樣本操作審計

`id: M BIGINT IDENTITY → BIGINT AUTO_INCREMENT [NO,PK]`; `actor_user_id: M BIGINT → BIGINT [NO]`; `action: M VARCHAR(32) → VARCHAR(32) [NO]`; `sample_id: M VARCHAR(36) → VARCHAR(36) [YES]`; `created_at: M DATETIME2 → DATETIME(6) [NO]`。無 FK，以便主樣本刪除後保留最少審計。來源：基礎 SQL。

### `research_grants` — 每人、每研究授權

`id: M BIGINT IDENTITY → BIGINT AUTO_INCREMENT [NO,PK]`; `user_id: M BIGINT → BIGINT [NO,FK users]`; `study_id: M VARCHAR(64) → VARCHAR(64) [NO]`; `can_annotate: M BIT → TINYINT(1) [NO]`; `can_review: M BIT → TINYINT(1) [NO]`; `can_manage: M BIT → TINYINT(1) [NO]`; `granted_by_user_id: M BIGINT → BIGINT [YES]`; `updated_at: M DATETIME2 → DATETIME(6) [NO]`。UNIQUE `(user_id,study_id)`；授權者 ID 無 FK。來源：authority SQL。

### `research_review_requests` — 審核資格申請

`id: M BIGINT IDENTITY → BIGINT AUTO_INCREMENT [NO,PK]`; `user_id: M BIGINT → BIGINT [NO,FK users]`; `study_id: M VARCHAR(64) → VARCHAR(64) [NO]`; `status: M VARCHAR(16) → VARCHAR(16) [NO]`; `requested_at: M DATETIME2 → DATETIME(6) [NO]`; `decided_at: M DATETIME2 → DATETIME(6) [YES]`; `decided_by_user_id: M BIGINT → BIGINT [YES]`。UNIQUE `(user_id,study_id)`。來源：authority SQL。

### `research_grant_audit` — 授權變更審計

`id: M BIGINT IDENTITY → BIGINT AUTO_INCREMENT [NO,PK]`; `study_id: M VARCHAR(64) → VARCHAR(64) [NO]`; `actor_user_id: M BIGINT → BIGINT [YES]`; `target_user_id: M BIGINT → BIGINT [NO]`; `action: M VARCHAR(32) → VARCHAR(32) [NO]`; `created_at: M DATETIME2 → DATETIME(6) [NO]`。無 FK。來源：authority SQL。

### `research_export_audit` — 訓練資料匯出審計

`id: M BIGINT IDENTITY → BIGINT AUTO_INCREMENT [NO,PK]`; `actor_user_id: M BIGINT → BIGINT [NO]`; `study_id: M VARCHAR(64) → VARCHAR(64) [NO]`; `schema_version: M INT → INT [NO]`; `sample_count: M INT → INT [NO]`; `created_at: M DATETIME2 → DATETIME(6) [NO]`。無匯出 ZIP 位元組欄、無 FK。來源：export SQL。

### `research_retention_policies` — 已核准政策版本

`id: M BIGINT IDENTITY → BIGINT AUTO_INCREMENT [NO,PK]`; `study_id: M VARCHAR(64) → VARCHAR(64) [NO]`; `policy_version: M VARCHAR(64) → VARCHAR(64) [NO]`; `retention_days: M INT → INT [NO]`; `effective_at: M DATETIME2 → DATETIME(6) [NO]`; `configured_by_user_id: M BIGINT → BIGINT [NO]`; `approval_reference: M VARCHAR(128) → VARCHAR(128) [NO]`; `expiry_action: M VARCHAR(16) → VARCHAR(16) [NO]`; `created_at: M DATETIME2 → DATETIME(6) [NO]`。UNIQUE `(study_id,policy_version)`；政策期限/核准資訊由服務驗證，SQL 無 CHECK/FK。來源：retention SQL。

### `research_retention_events` — 撤回、刪除、到期處理

`id: M BIGINT IDENTITY → BIGINT AUTO_INCREMENT [NO,PK]`; `sample_id: M VARCHAR(36) → VARCHAR(36) [NO]`; `policy_version: M VARCHAR(64) → VARCHAR(64) [YES]`; `reason: M VARCHAR(24) → VARCHAR(24) [NO]`; `actor_user_id: M BIGINT → BIGINT [YES]`; `processed_at: M DATETIME2 → DATETIME(6) [NO]`。無 FK，以便樣本刪除；無 DB UNIQUE。來源：retention SQL。

## Entity 與 migration 差異／未證實事項

1. `users.account_id` 長度 50 vs SQL 20；`account_id_normalized` 僅在 SQL；Google subject 與密碼重設的 filtered UNIQUE 僅在 SQL。
2. 聊天 canonical/type CHECK、計畫 CHECK/DEFAULT/CASCADE、影片 CHECK/CASCADE、avatar source CHECK/DEFAULT 均主要由 SQL migration 提供；Hibernate `ddl-auto=update` 不保證重建相同語義。
3. `training_history`、`users`、`custom_rehab_exercises` 等表缺完整初始建表 DDL；Azure 實體型別/欄名/約束需實際導出。`exercise_result.created_at` 由何機制產生亦待確認。
4. `custom_rehab_exercises` Flutter 的 `poseMeasurementRules` 不在當前後端 DTO/Entity，可能被 JSON API 忽略；先作資料往返查證，不得默默創建重複資料結構。
5. 研究表的 review/retention 增量欄位需依順序套用；研究腳本並無已在 Azure 執行的證據。
6. 沒有完整 SQL 腳本的 Entity 表，上述 `E` 型別、DEFAULT、索引和 FK 刪除行為仍須查正式庫，不能直接當作重建 DDL。
