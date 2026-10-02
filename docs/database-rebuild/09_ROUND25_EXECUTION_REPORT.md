# 09 — Round 2.5 MySQL 8.4.11 實機執行紀錄

## 1. 結論與範圍

**PASS：本機隔離庫 Schema 與指定核心 DML 驗證通過，可在人工驗收後進入 Round 3。**

實際伺服器為 MySQL 8.4.11，並非以 H2、靜態解析或 MySQL client 版本冒充實機結果。依序成功完成 V001／18 表驗證／V002／29 表驗證／虛構資料 DML／ROLLBACK／新連線完整複驗。沒有修改已套用的版本化 Migration，沒有開始 R3、部署或資料搬遷。

此結果不代表 Spring Boot 已切換 MySQL，也不代表 Android、Render、真實 SQL Server 資料搬遷或正式研究收集已驗收。兩連線競態、應用整合及大檔案傳輸仍待 R3。

## 2. Baseline 與授權

- 後端：`C:/Users/kuoja/Documents/GitHub/trianing-system`。
- 分支：`feat/rehab-ml-cloud-label`。
- R2.5 基準 HEAD：`1319b30e370ec51e6b9c9eea150cf028e9407684`；至交付未變動。
- Round 2 成果已提交；R2.5 唯讀預檢階段僅有 07 修改及 09 檢查點。收到本次「允許」後接續，不覆蓋其他修改。
- 已閱讀 06／07／08，使用既有 Migration 與驗證工具，沒有重建整套 SQL。
- 預檢發現 Schema 不存在後先停止；使用者明確回覆「允許」，再次查詢仍不存在，才建立全新隔離庫。
- 所有測試僅針對 `rehab_r2_validation`。沒有修改任何原有業務／系統 Schema。
- root 認證使用 MySQL CLI 互動式 password prompt；沒有密碼值 argv、env、檔案或報告，未讀 Workbench 密碼儲存區。

## 3. 實際環境與唯讀預檢

| 項目 | 實際結果 |
|---|---|
| Host／port | 127.0.0.1:3306 |
| Windows Service | MySQL84，Running |
| SELECT VERSION() | 8.4.11 |
| 建庫前 SELECT DATABASE() | NULL |
| 建庫前 SHOW DATABASES | information_schema、mysql、performance_schema、sys |
| 目標 Schema 建庫前是否存在 | 不存在；授權後重新確認 |
| @@innodb_page_size | 16384 bytes |
| @@max_allowed_packet | 67108864 bytes（64 MiB） |
| 驗證 session time_zone | +00:00；UTC default 測試另暫設 +08:00 後恢復 |
| 最終 autocommit／foreign_key_checks | 1／1 |

實際 sql_mode：

```text
ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,
ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION
```

新庫建立語句：

```sql
CREATE DATABASE rehab_r2_validation
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs;
```

未使用 DROP、IF NOT EXISTS、--force 或關閉 FOREIGN_KEY_CHECKS。**現在該測試庫已存在，續作不可再直接重跑 CREATE DATABASE／Migration，也不可自行清空它。**

## 4. 原 SQL Server Database Name 對照

- 已檢查 `src/main/resources/application.properties`：
  `spring.datasource.url=${DB_URL}`，不是包含可確認 databaseName 的 literal URL。
- 當前執行環境沒有可解析的 DB_URL 資料庫名稱。
- **原 SQL Server Database Name：UNVERIFIED／待使用者確認**。不得猜測，未輸出完整連線 URL。
- 本輪 MySQL `rehab_r2_validation` 僅是隔離驗證庫，不是正式名稱決策。
- Spring Boot 設定完全未改；正式資料庫名稱及部署 DB_URL 對照留待 R3，不需要使用者提供密碼。

## 5. Migration 與實際 Metadata

| 階段 | 執行狀態 | 表 | 欄 | Generated | FK | DDL 明定鍵／索引 | 自動 FK 支援索引 | 實體索引總數 | CHECK |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| V001 Main | PASS，CLI exit 0 | 18 | 149 | 2 | 26 | 49 | 6 | 55 | 12 |
| V001＋V002 | PASS，CLI exit 0 | 29 | 240 | 2 | 33 | 68 | 7 | 75 | 16 |

V001 成功後完成 Main 驗證，才執行 V002，沒有任何 CREATE TABLE 失敗。以 `@r2_include_research=0`／`1` 分別執行既有 `validate_schema.sql`。

Metadata 核对包含所有表名、欄位型別／長度／NULL／DEFAULT／AUTO_INCREMENT／generated expression、PK／UNIQUE／INDEX、FK 與更新／刪除規則、CHECK expression 與 ENFORCED。修正驗證器的引號格式誤判後，Main、完整 Research 及 DML 後最終複驗的所有 drift／extra 結果皆為 0 列。CHECK 均 ENFORCED=YES。

68 指 **29 個 PK 加其餘 DDL 明定 UNIQUE／INDEX**，不是 information_schema.STATISTICS 欄位列數。MySQL 自動 FK 支援索引另計 7 個，因此物理索引是 75，而非 68。7 個皆為非唯一索引：

| 表 | 自動索引 | 支援欄位 |
|---|---|---|
| chat_messages | fk_chat_message_sender | sender_id |
| exercise_result | fk_exercise_result_exercise | exercise_id |
| exercise_result | fk_exercise_result_user | user_id |
| friend_requests | fk_friend_requests_receiver | receiver_id |
| friendships | fk_friendships_high | user_high_id |
| user_bindings | fk_user_bindings_linked | linked_user_id |
| research_annotations | fk_research_annotation_therapist | therapist_user_id |

### 實際 SHOW TABLES：Main 18

以下是 CLI 實際回傳、並逐名核對原 Entity／Round 1 清單的名稱，沒有自行重新命名：

```text
chat_conversations
chat_messages
custom_exercise_assignments
custom_rehab_exercises
exercise
exercise_assignments
exercise_result
friend_requests
friendships
password_reset_requests
rehab_plan_items
rehab_plans
training_history
training_history_video
training_session_results
user_avatars
user_bindings
users
```

### 實際 SHOW TABLES：Research 另 11

完整階段 SHOW TABLES 是上述 18 加以下 11，共 29。Research 為本輪驗證的開發分支結構，不能據此宣稱原 Azure 正式庫已有這些表。

```text
research_annotation_revisions
research_annotations
research_audit
research_consents
research_export_audit
research_grant_audit
research_grants
research_retention_events
research_retention_policies
research_review_requests
research_samples
```

### 原 Migration 指紋

檔案 bytes SHA-256 與 Round 2 一致，Git diff 為空：

- V001：`6B731EC0153FE54A40F75C819D1CF9C4269C609AEB785007987268A678C4156A`
- V002：`C1661E45C2733EE9F2AA1EED2072235690A29632C34B991DC9A6BF90BE64310A`

## 6. DML 實機驗證

**23／23 結果斷言 PASS；28／28 Expected Failure 符合實際錯誤碼，共 51 項核心驗證。**

虛構資料測試脚本為 `R25_DML_TESTS.sql`，以互動 CLI 逐項執行，而非批次 --force。腳本含刻意非法語句，不應直接作 Migration／一般批次成功腳本使用。全部處於同一 transaction，最後 ROLLBACK，未留下測試帳號、研究同意、政策或授權。

| 指定案例 | 實際驗證與結果 |
|---|---|
| Nullable UNIQUE／大小寫 | 多個 NULL 可共存；email CI 重複拒絕；google_subject 大小寫相異可共存，完全相同拒絕，PASS |
| account_id_normalized | LOWER 生成值正確，正規化後重複 account 拒絕，PASS |
| active_user_id | 同一 user 第二筆 active reset 拒絕，PASS |
| Used／active reset 共存 | 2 筆 consumed 的 generated 欄 NULL＋1 筆 active 共存，PASS |
| Password reset CASCADE | 刪除獨立虛構 user 後 reset 行數為 0，PASS |
| exercise_result UTC DEFAULT | +08:00 session 下省略 created_at，值在 UTC before／after 範圍內，與 NOW 差約 28800 秒，PASS |
| FK RESTRICT／CASCADE | 使用者／聊天室／樣本刪除限制、缺父資料拒絕、plan items／history video cascade，PASS |
| CHECK | 全部 16 個 constraint 的非法值均被 MySQL 3819 拒絕，PASS |
| 中文／Emoji | 「復健🦵」HEX 往返一致，PASS |
| LONGTEXT | 中文 Emoji JSON 大於 65535 bytes，保存後 SHA-256 一致，PASS |
| LONGBLOB | 80000 bytes 含 00／01／7F／FF 的資料，長度、起始 HEX 與 SHA-256 一致；video／avatar 測試，PASS |
| Research UNIQUE／FK | consent 唯一、sample participant＋client ID 去重、grant 唯一、缺 participant 拒絕、sample 有 annotation 時 RESTRICT，PASS |
| Revision／audit 特殊設計 | 同 sample＋revision 可多列（普通索引，不虛構 UNIQUE）；無 FK 的審計 actor／邏輯 sample ID 可保留，PASS |
| Default／NULL | reset failed_attempts=0、plan done=0、annotation revision=0；nullable boolean 可 NULL，PASS |

預期錯誤碼明細（每項 id／預期碼／實際碼／SQLSTATE／非敏感摘要見 JSON）：

| MySQL code | 語義 | 實際次數 | 狀態 |
|---|---|---:|---|
| 1062 | Duplicate Key | 7 | Expected Failure，PASS |
| 1451 | FK RESTRICT 父列不能刪除 | 3 | Expected Failure，PASS |
| 1452 | 子列缺 FK 父資料 | 2 | Expected Failure，PASS |
| 3819 | CHECK violation | 16 | Expected Failure，PASS |

ROLLBACK 後先測主要 fixture 表，再由新連線逐表 COUNT，**29／29 表均為 0 列**。Metadata 再次完整驗證沒有漂移。所有 CLI 連線已關閉。AUTO_INCREMENT 回滾可能保留序號缺口，沒有嘗試 reset／recreate。

## 7. 實際錯誤與最小處理

### 7.1 CHECK metadata 表示格式：已修正驗證器，不修改 Schema

首次 Main 驗證有 3 個 CHECK_DRIFT：

- chat_conversations 的 conversation_type；
- rehab_plans 的 condition_type；
- user_avatars 的 source_type。

MySQL 8.4.11 的 information_schema.CHECK_CLAUSE 在字串 delimiter 使用反斜線引號；HEX 確認有 `5C27`，SHOW CREATE TABLE 則顯示正常單引號。這是 verifier 格式正規化不足，不是 Migration 執行錯誤。

只修改 `mysql/validate_static.py` 與其對應的 `mysql/validate_schema.sql` CHECK comparison：將 delimiter 的 `CHAR(92)+CHAR(39)` 正規化為 `CHAR(39)`。不改 literal 值／大小寫、不放寬約束、不使用 LOWER 掩蓋 case-sensitive 規則。新增一個 regression test。修後 Main／Research／最終複驗無 drift，非法值 DML 證明 CHECK 真正生效。

### 7.2 附加診斷 1193：非核心測試／DDL 失敗

完成核心測試與 ROLLBACK 後，附加查詢使用 `@@in_transaction`，MySQL 回報：

```text
ERROR 1193 (HY000): Unknown system variable 'in_transaction'
```

此變數不適用本機 MySQL，不能拿來驗證 transaction。未修改資料或 DDL，也不影響 51 項已完成驗證。已改用新連線確認 DATABASE、autocommit=1、foreign_key_checks=1、29 表各 0 列及完整 metadata。沒有把此診斷錯誤隱藏或誤列為 Expected Failure 測試。

**沒有 DDL 需要修正，也未改已成功套用的 V001／V002。**

## 8. 保存的證據與實際命令

保存原 MySQL XML resultset 的 statement／欄位／row；CLI 分段輸出合併後用單一 `mysql_results` 根包住，沒有改寫測試值。各 XML 包含全部該次驗證結果（空 resultset 亦保留）：

- `R25_MAIN_METADATA.xml`：Main SHOW TABLES、完整驗證及數量。
- `R25_ALL_METADATA.xml`：全 29 表 SHOW TABLES、完整驗證及數量。
- `R25_CHECK_DIAGNOSTIC.xml`：3 個 CHECK 的實際 clause／HEX／SHOW CREATE TABLE。
- `R25_FINAL_VERIFICATION.xml`：DML 後完整 metadata、29 表逐表 0 列、最終連線狀態。
- `R25_DML_TESTS.sql`：只有虛構、可審查的 transaction 測試。
- `R25_DML_RESULTS.json`：23 個結果斷言及 28 个 Expected Failure 碼／摘要。

實際使用 MySQL CLI：`C:/Program Files/MySQL/MySQL Server 8.4/bin/mysql.exe`。以 `--no-defaults --no-login-paths --protocol=TCP --host=127.0.0.1 --port=3306 --user=root --password` 進入互動密碼輸入；執行階段指定唯一目標 `--database=rehab_r2_validation`。V001／V002 讀取原檔後執行，validation 設定 stage 參數再執行；未在參數附密碼值。

靜態與驗證器 regression 命令：

```powershell
& 'C:/Users/kuoja/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe' -B docs/database-rebuild/mysql/validate_static.py
& 'C:/Users/kuoja/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe' -B -m unittest discover -s docs/database-rebuild/mysql -p test_validate_static.py -v
git diff --check
```

結果：靜態 Entity／DDL／manifest 一致，**14／14 unit tests PASS**。

交付前另以 PowerShell／.NET 解析 4 份 XML 與 JSON，PASS：XML 均有合法單一根；Main SHOW TABLES 的 18 名稱與非研究 Entity 逐名一致，完整 SHOW TABLES 的 29 名稱與全部 Entity @Table 逐名一致；所有 drift 結果 0 列；最終 29 表 row_count=0；JSON 的 23 個 assertion 及 28 個預期／實際錯誤碼一致。

**git diff --check PASS**；7 個未追蹤新增檔亦逐一 no-index --check 通過。4 個 tracked 修改均在 docs/database-rebuild，沒有其他程式／設定修改。Git 的 LF→CRLF 提示只是現有換行轉換提醒，不是 whitespace error；未改 Migration 換行。

## 9. 尚未驗證／Round 3 必要事項

| 項目 | 狀態／交接 |
|---|---|
| 兩連線同時寫入／去重競態 | NOT RUN；單連線約束已驗證，不冒充 concurrency E2E |
| Spring Boot＋Connector/J＋Hibernate | NOT RUN；本輪沒有改 driver／dialect／設定，也未啟動 Schema Update |
| 100 MiB 影片整合 | NOT RUN；本次只測 80000 bytes，packet 實測 64 MiB，需 R3 審核限制／傳輸策略 |
| Streaming／Range download | NOT RUN；SQL 二進位往返不等於 HTTP 影片測試 |
| Android／Render／Flutter | NOT RUN；本輪不改、不 build、不部署 |
| 真實 SQL Server 資料搬遷／原資料庫名稱 | 未驗證；待確認名稱與搬遷計畫 |
| 正式研究政策／授權／真人收集 | 未啟用；無真實同意或 retention policy，不可宣稱已滿足倫理或正式收集條件 |
| 備份／既有業務相容性 | 本輪未搬遷資料；R3 依 08 處理，不宣稱原業務已切换成功 |

只放行 **Schema 層級的 R3 開發前置條件**；先請使用者人工核對本報告與證據，下一輪再照 08 做應用相容性，不能跳過其風險與驗收條件。保留本隔離庫，不要重建或刪除。

## 10. 變更與 Git 安全

修改：

- `07_MYSQL_SCHEMA_VALIDATION.md`：將實際執行通過項目改 PASS，保留歷史及未測事項。
- `mysql/validate_static.py`：CHECK delimiter normalization 與清楚區分静態／實機結果的說明。
- `mysql/validate_schema.sql`：同一個 CHECK normalization 修正。
- `mysql/test_validate_static.py`：delimiter／literal case regression。

新增：本報告，以及上述 6 份 XML／JSON／SQL 證據／測試檔案。

V001／V002、既有 Java／Dart／SQL Server Migration、Spring Boot／Flutter／Dockerfile／正式環境設定均未修改。沒有輸出或保存認證密碼；只有虛構資料及 metadata。

沒有 git add／commit／push／merge／PR、Render 部署、Azure 或正式資料庫操作。沒有 reset／restore／checkout／clean。使用者要求「提交執行結果」為交付文件，不是被明確禁止的 Git commit。**停止在 Round 2.5，等待人工驗收。**
