# 05 — Round 2 MySQL 8.4 重建交接規格

本輪只有靜態分析和五份文件；**沒有修改 Java/Dart/SQL/設定、沒有連 Azure/MySQL、沒有執行 Hibernate update、沒有部署**。後端本機同時有 `main` (`94132993`) 與 `feat/rehab-ml-cloud-label` (`239a64ae`)；Flutter 是 `feat/rehab-ml-poc` (`429f200c`)，起始已有與此任務無關的 `android/build/reports/problems/problems-report.html` 修改，未碰觸。GitHub 遠端分支／正式 Render 版本不代表本機功能分支已部署；本輪沒有 Push/Merge/PR。

## 直接可用的表清單與建表先後

**main 預期 18 表**（並非現場存在證明）：

1. 基礎：`users`, `exercise`。
2. 依賴 users/exercise：`exercise_result`, `user_bindings`, `friend_requests`, `friendships`, `password_reset_requests`, `chat_conversations`, `custom_rehab_exercises`, `exercise_assignments`, `rehab_plans`, `training_history`, `training_session_results`, `user_avatars`。
3. 子表：`chat_messages`, `custom_exercise_assignments`, `rehab_plan_items`, `training_history_video`。

**功能分支額外 11 表**，須與 main 分開版本化、不得當成 Azure 既有表：`research_consents`, `research_samples`, `research_annotations`, `research_annotation_revisions`, `research_audit`, `research_grants`, `research_review_requests`, `research_grant_audit`, `research_export_audit`, `research_retention_policies`, `research_retention_events`。依賴順序：base research SQL → authority/review/export/retention 增量；建表實作順序見 [02_ENTITY_RELATIONSHIPS.md](02_ENTITY_RELATIONSHIPS.md)。研究 SQL 交接文件記錄為尚未在正式 SQL Server 執行；不得當作已部署事實。

每個欄位、SQL Server 來源型別/長度、MySQL 建議、NULL、PK/FK/UNIQUE/INDEX/DEFAULT/CHECK 見 [01_DATABASE_INVENTORY.md](01_DATABASE_INVENTORY.md)；來源位置/Native SQL 與轉換風險見 [04_SQLSERVER_TO_MYSQL_COMPATIBILITY.md](04_SQLSERVER_TO_MYSQL_COMPATIBILITY.md)。資料是否在雲端、裝置或原始碼見 [03_DATA_STORAGE_AUDIT.md](03_DATA_STORAGE_AUDIT.md)。

## Round 2 執行序列（尚未執行）

1. 在獲准的**隔離 SQL Server 唯讀**環境導出：`sys.tables`、`sys.columns`、`sys.types`、`sys.default_constraints`、`sys.key_constraints`、`sys.foreign_keys`、`sys.check_constraints`、`sys.indexes`；核對 01 中所有 `E`/待確認、列數、PK 最大值、孤兒 FK、唯一鍵衝突、NULL 數與最大長度。不要先執行任何 DDL。保存不含個資的 schema diff。
2. 明確選定目標是 **main baseline** 還是 **main + 未部署研究功能**；提供獨立 migration 版本與測試結果。若研究尚未正式獲准，正式研究收集仍關閉，不配置虛構保留期限。
3. 在隔離 MySQL 8.4 測試庫建立 `utf8mb4` schema、UTC 時間策略、完整 18 表，必要時追加 11 研究表。以明確 migration 工具/腳本取代 `ddl-auto=update` 產生的不可重複現場差異；FK/UNIQUE/CHECK/DEFAULT/刪除行為不得因 DDL 可執行就省略。
4. 先解決 `users.account_id` 20 vs 50、`account_id_normalized` 大小寫/Unicode、filtered active password-reset UNIQUE、`poseMeasurementRules` 前後端合約、`exercise_result.created_at` 來源與帳號刪除對 chat/plan/history/result FK 的影響。這些是 blocker，不應在搬遷時擅自選一個值。
5. 把 `TrainingHistoryVideoService.java` 的 `dbo.` JDBC SQL 改為相容的 MySQL 查詢；用真實 MySQL LONGBLOB 測上傳/覆寫/Range 讀取。其他 Repository `@Query` 為 JPQL，先測再改。DB driver/dialect/URL 只在 Round 2 的環境變更中處理，不得把正式密碼寫進 repo。
6. 使用獲授權、脫敏或合成資料測建表、匯入及回滾/恢復計畫；正式搬遷前需備份與維護窗口。保留原 ID，按依賴順序匯入；校正 AUTO_INCREMENT，做表列數/業務鍵/FK/大檔 checksum 對帳。裝置本機 SharedPreferences/檔案不在 SQL dump 內，須另做使用者同意的遷移方案。
7. 跑 backend schema integration tests（**真 MySQL 8.4**，不能拿 H2 冒充）、API 授權/帳號/指派/聊天/訓練歷史/影片/研究回歸、Flutter↔backend 合約測試；最後在隔離 Android 裝置測完整路徑。
8. 只有在上述通過且人工核准後，才另行規劃正式資料庫搬遷、Render 設定與部署。本輪禁止執行這些操作。

## 待確認問題（不可自行假設）

- Azure 正式庫 18 表實際存在/欄位/約束/列數；部分表只有 Entity 而無建表 DDL。功能分支 11 表的正式存在性也待確認。
- `users.account_id` 長度衝突；各 UNIQUE 在 SQL Server collation 與 MySQL utf8mb4 collation 下是否發生碰撞。
- `training_history` 初始 DDL、`exercise_result.created_at` DB DEFAULT、未宣告索引/FK 和所有 Entity-only 欄的精確 SQL Server 型別。
- 預設 `exercise` 表的真實 ID、名稱與 Flutter `kTrainingActions` 映射；不允許用程式碼序號替代 DB ID。
- Flutter `poseMeasurementRules` 是否被目前後端 DTO 忽略；需在隔離測試環境做 create→read→edit round-trip。
- 手機本機 CUSTOM、history 待同步、AI 動作模板、研究樣本與影片的處置政策。
- 管理員/研究保留政策的正式審批、備份及既有匯出資料清理責任；不可宣稱主 DB 刪除即所有副本刪除。

## Round 2 最低驗收條件

- 完整 migration 可在空 MySQL 8.4 測試庫重建 schema，重跑/升版不破壞資料；`information_schema` 比對 01 的每個欄、型別、nullability、PK/FK/UNIQUE/INDEX/DEFAULT/CHECK。
- main 與研究分支 schema 分開、研究收集開關預設關閉；正式 Azure 未部署研究表不會被當作既存資料匯入。
- 表/欄數量、FK、NULL、重複鍵、ID、中文/emoji、時間精度、JSON、圖片與影片 bytes 全部對帳；大檔 Range 讀取通過。
- 既有登入/Google/重設密碼、好友/聊天已讀、患者綁定、DEFAULT/CUSTOM 指派、計畫、歷史/結果/影片、研究授權與同意的後端整合測試通過；不能削弱授權。
- 明確寫出未通過項、正式庫未知項及回滾/備份方案；沒有現場驗證不得標示正式遷移完成。
