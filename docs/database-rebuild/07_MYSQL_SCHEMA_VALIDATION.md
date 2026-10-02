# 07 — MySQL Schema 驗證紀錄

## 實際結果（2026-10-02）

| 驗收項 | 結果 | 證據／範圍 |
|---|---|---|
| 後端分支／工作樹 | PASS | R2：feat/rehab-ml-cloud-label，起始HEAD d6fbb2e548e829823ce501d0ae3cf9ea47b3f28c；R2.5基準1319b30e370ec51e6b9c9eea150cf028e9407684，詳見09 |
| MySQL client | PASS | `C:/Program Files/MySQL/MySQL Server 8.4/bin/mysql.exe --version` → 8.4.11 Win64 |
| MySQL84 service | PASS | Get-Service → Running；不等於SQL登入成功 |
| SQL登入 | PASS（R2.5） | 互動式password prompt成功登入本機MySQL8.4.11；Round2無密碼1045為歷史紀錄 |
| 建立隔離測試庫 | PASS（R2.5） | 重新確認不存在，使用者回覆「允許」後建立rehab_r2_validation |
| V001／V002實際MySQL execution | PASS（R2.5） | 18表成功後完成Main驗證，再建研究11表；沒有DDL錯誤，Migration原檔未改 |
| 靜態表數／順序／Engine／字元集 | PASS | V00118、V00211，總29；每表InnoDB/utf8mb4，父表先於FK子表 |
| Entity欄位／型別／長度／可空性 | PASS | 238 Entity欄，DDL240欄；兩個computed為刻意新增；created_at的NN新決策見06 |
| FK graph／型別／Collation／刪除語義 | PASS static | 33FK，3CASCADE；未亂補審計或多型FK |
| 明名PK／UNIQUE／INDEX | PASS static | 共68（含29個PK；不含MySQL自動產生FK index）；Entity明名約束均對上 |
| CHECK／DEFAULT | PASS static | 16CHECK；9個有SQLDEFAULT的欄；沒有把Java初始值套成SQLDEFAULT |
| 已生成metadata manifest與DDL一致 | PASS | validate_static.py檢查validate_schema.sql逐字與產生結果一致 |
| 靜態檢查器mutation tests | PASS | R2原13/13；R2.5補CHECK metadata escaping回歸後14/14，靜態工具本身不連DB |
| Git／新增檔whitespace | PASS（R2歷史） | R2的git diff --check及8個新增檔逐一no-index --check均通過；R2.5有文件／驗證器增量修改，另見09 |
| generated UNIQUE／CASCADE／CHECK／日期／BLOB實際DML | PASS（R2.5） | 23結果斷言＋28Expected Failure通過；16CHECK全部拒絕非法資料，最終29表皆0列 |
| Spring/MySQL整合 | NOT RUN | 本輪禁止Java／JDBC改動；不跑Hibernate update |
| Azure／Render／正式schema | NOT RUN | 無存取、無部署、無舊資料搬遷 |

Round2歷史：CLI連線失敗後停止憑證探索。沒有向使用者索取root密碼，沒有使用密碼參數值、讀取Workbench憑證或建立新登入。Workbench成功連線只代表使用者有存取能力，不能宣稱Codex已SQL驗證。

Round2.5更新：使用MySQL CLI互動式密碼輸入完成唯讀預檢；版本8.4.11，原SHOW DATABASES僅四個系統schema，隔離庫不存在；sql_mode包含STRICT_TRANS_TABLES，innodb_page_size=16384。使用者回覆「允許」後再次確認不存在，才新建隔離庫。已完成V001／Main驗證／V002／Research驗證／DML／ROLLBACK後複驗，結果詳見09及保存的XML/JSON。未將密碼寫入命令列參數、檔案或報告，也未讀Workbench憑證。

## R2.5實際Metadata／DML結果

| 階段 | 表數 | 欄數 | Generated | FK | 明定鍵／索引 | 自動FK index | 實體index合計 | CHECK |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| V001 Main | 18 | 149 | 2 | 26 | 49 | 6 | 55 | 12 |
| V001＋V002 | 29 | 240 | 2 | 33 | 68 | 7 | 75 | 16 |

兩階段SHOW TABLES逐名比對原Entity／Round1清單，沒有重新命名；TABLE/COLUMN/INDEX/FK/CHECK drift、額外欄／表／FK／CHECK resultsets全部0列。所有CHECK為ENFORCED=YES。7個自動index均為非唯一FK supporting index，已獨立列出。

首次Main metadata檢查3個CHECK誤判：MySQL8.4.11 CHECK_CLAUSE用反斜線引號delimiter，SHOW CREATE TABLE則顯示正常引號。已保存HEX／SHOW CREATE證據；僅validate_static.py及validate_schema.sql補delimiter正規化，另補unit test，不改V001/V002、約束內容或literal大小寫。修後Main／Research及DML後完整metadata複驗皆無drift。

DML：23/23結果assertion、28/28預期拒絕（1062×7、1451×3、1452×2、3819×16）。全程僅新隔離庫虛構fixture，transaction最後ROLLBACK，29表逐表確認0列；沒有殘留帳號／policy／研究同意。AUTO_INCREMENT可能留下合理跳號，沒有reset。附加診斷@@in_transaction回傳1193（MySQL不支援該系統變數），沒有影響DML結果；最後改以新連線確認schema、autocommit=1、foreign_key_checks=1及逐表0列。1193非Migration／約束失敗，完整記錄見09。

未驗證：兩連線競態、Spring Boot/Connector/J/Hibernate、100MiB影片streaming／Range下載、Android/Render E2E。max_allowed_packet實測64MiB，與現有100MiB video上限整合需R3處理。原SQL Server databaseName由DB_URL注入且本process未提供，仍UNVERIFIED；application.properties未改。

## 可重複靜態驗證

在後端repo根目錄執行（Python3 standard library，沒有pip dependency）：

```powershell
python -B docs/database-rebuild/mysql/validate_static.py
python -B -m unittest discover -s docs/database-rebuild/mysql -p test_validate_static.py -v
git diff --check
```

本機實際使用的Python路徑是 `C:/Users/kuoja/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe`，因一般PATH沒有python/py。

R2.5重新執行的實際輸出：

```text
PASS static: main=18 research=11 columns=240 Entity_fields=238 indexes=68 FK=33 CHECK=16
Static tool does not execute MySQL; see 09_ROUND25_EXECUTION_REPORT.md for separate execution evidence.
Ran 14 tests ... OK
```

靜態工具會檢查建表順序、重複欄、PK、FK圖／刪除規則／父鍵型別、關鍵filtered替代、特殊非FK、revision普通索引、9SQL defaults、索引長度上限、T-SQL殘留；與全部Entity的field/type/length/null、明名UniqueConstraint/Index/column unique交叉檢查。

mutation tests確認缺表、重複欄、缺FK、FK collation不符、誤用STORED active欄、虛構role default、revision誤UNIQUE、缺CHECK、T-SQL、Entity長度／Unique漂移都會失敗。這是受控DDL子集檢查器，不是完整MySQL語法parser，不會宣稱所有SQL語法或JDBC正確。

`--emit-validation`僅列印可重製的metadata SQL，不連線、不寫檔；變更schema前先重新審核Source，不能只改manifest讓測試通過。維持首次版本檔一旦套用不可修改的規則。

## Schema版本指紋

以下為目前檔案bytes SHA-256；若Git換行轉換造成不同，部署前用待執行檔重新計算並人工確認diff，勿忽略版本漂移。

| 檔案 | SHA-256 |
|---|---|
| V001__main_schema.sql | `6B731EC0153FE54A40F75C819D1CF9C4269C609AEB785007987268A678C4156A` |
| V002__research_schema.sql | `C1661E45C2733EE9F2AA1EED2072235690A29632C34B991DC9A6BF90BE64310A` |

## 人工MySQL8.4驗收：只限全新隔離庫

### 1. 登入／確認庫不存在

使用已安全設定的Workbench連線，或本機互動password prompt（不把密碼貼進聊天、source、命令參數或Git）：

```powershell
& 'C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe' --protocol=TCP -h 127.0.0.1 -P 3306 -u root -p
```

在SQL工作區單獨先執行：

```sql
SELECT VERSION(), @@sql_mode, @@innodb_page_size;
SELECT SCHEMA_NAME FROM information_schema.SCHEMATA
WHERE SCHEMA_NAME = 'rehab_r2_validation';
```

**若查到任何同名schema，立即停止。** 不清空、不重建、不拿IF NOT EXISTS跳過；請回報原有schema，經另外確認後才能使用另一個全新專用名稱。

只有確認無該schema，才執行：

```sql
CREATE DATABASE rehab_r2_validation CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs;
USE rehab_r2_validation;
SET SESSION time_zone = '+00:00';
SELECT DATABASE(), @@session.time_zone, @@sql_mode;
```

要求strict SQL mode（例如MySQL8.4預設包含STRICT_TRANS_TABLES），innodb_page_size預期16KB；非預設小page環境需先重審index限制。無需提升正式權限；專用測試帳號優於日常root操作，帳號建立本轮未執行。

### 2. Main18表

在mysql CLI（Workbench可開檔，確認選定相同schema）依序執行：

```sql
SOURCE C:/Users/kuoja/Documents/GitHub/trianing-system/docs/database-rebuild/mysql/V001__main_schema.sql;
SET @r2_include_research = 0;
SOURCE C:/Users/kuoja/Documents/GitHub/trianing-system/docs/database-rebuild/mysql/validate_schema.sql;
```

**遇到任何SQL錯誤停止**，不要繼續下一檔。禁止 `--force`；Workbench關閉錯誤後繼續执行。預期18張表、所有 `*_DRIFT`／`EXTRA_TABLE/COLUMN/FK/CHECK` resultsets為空。`UNEXPECTED_INDEX_REVIEW` 應只有InnoDB自動補FK supporting index；額外UNIQUE或非預期索引也必須人工確認，不能無視。

### 3. Optional Research +11表

Main完整驗證後才執行；不想安裝研究功能就不執行V002。

```sql
SOURCE C:/Users/kuoja/Documents/GitHub/trianing-system/docs/database-rebuild/mysql/V002__research_schema.sql;
SET @r2_include_research = 1;
SOURCE C:/Users/kuoja/Documents/GitHub/trianing-system/docs/database-rebuild/mysql/validate_schema.sql;
```

預期29張表、全部drift為空；不存在帳號、研究同意、授權或policy。單純安裝研究表不表示已可正式收集真人研究資料。

### 4. Metadata驗證含義

`validate_schema.sql`只SET session variables/查information_schema，不讀私人資料、不建臨時表／routine。查驗表存在／額外表、Engine/collation、240欄type/NULL/default/auto/gen-expression/storage/collation、明名index次序/unique/descending/prefix/visibility、33FK父schema/table/column/delete/update、16CHECK expression/enforced、額外FK/CHECK，以及實際18/29表數。

Expression的非語義格式差異會做有限正規化；CHECK字串literal大小寫保留。若本機MySQL格式仍產生diff，先SHOW CREATE TABLE逐條比對，記錄格式／真正drift，不可只放寬斷言。腳本**回傳查詢結果而非自動exit code判定**；保存所有resultsets供review。

## 人工約束／資料行為驗收（原R2手動案例；R2.5執行狀態見上節／09）

所有DML只在新隔離庫，用虛構fixture、transaction最後ROLLBACK。以下不是真帳號seed；不執行對現有庫的DELETE。每個預期錯誤statement單獨在Workbench執行，確認錯誤後其餘transaction仍有效；不要用整批遇錯繼續掩蓋失敗。

### A. 多NULL與大小寫唯一性

```sql
START TRANSACTION;
INSERT INTO users(email,role) VALUES ('r2-a@example.invalid','PATIENT');
SET @r2_u1=LAST_INSERT_ID();
INSERT INTO users(email,role) VALUES ('r2-b@example.invalid','PATIENT');
SET @r2_u2=LAST_INSERT_ID();
-- 上述兩筆account_id/google_subject/friend_code/password都NULL，應成功。
UPDATE users SET account_id='R2Case1',google_subject='r2-subject-A' WHERE id=@r2_u1;
SELECT account_id,account_id_normalized FROM users WHERE id=@r2_u1;
-- normalized應是r2case1。
UPDATE users SET account_id='r2case1' WHERE id=@r2_u2;
-- 預期1062 duplicate；不可成功。
UPDATE users SET google_subject='r2-subject-A' WHERE id=@r2_u2;
-- 預期1062 duplicate；不可成功。
UPDATE users SET google_subject='r2-subject-a' WHERE id=@r2_u2;
-- binary collation大小寫不同，應成功；不是把Google email當subject。
INSERT INTO users(email,role) VALUES ('R2-A@example.invalid','PATIENT');
-- email CI duplicate，預期1062。
ROLLBACK;
```

### B. active-reset唯一／consumed／cascade

```sql
START TRANSACTION;
INSERT INTO users(email,role) VALUES ('r2-reset@example.invalid','PATIENT');
SET @r2_user=LAST_INSERT_ID();
INSERT INTO password_reset_requests(id,user_id,code_hash,expires_at,created_at)
VALUES ('r2-request-1',@r2_user,REPEAT('0',64),UTC_TIMESTAMP(6)+INTERVAL 10 MINUTE,UTC_TIMESTAMP(6));
INSERT INTO password_reset_requests(id,user_id,code_hash,expires_at,created_at)
VALUES ('r2-request-2',@r2_user,REPEAT('1',64),UTC_TIMESTAMP(6)+INTERVAL 10 MINUTE,UTC_TIMESTAMP(6));
-- 第二筆active預期1062。
UPDATE password_reset_requests SET consumed_at=UTC_TIMESTAMP(6) WHERE id='r2-request-1';
INSERT INTO password_reset_requests(id,user_id,code_hash,expires_at,created_at)
VALUES ('r2-request-2',@r2_user,REPEAT('1',64),UTC_TIMESTAMP(6)+INTERVAL 10 MINUTE,UTC_TIMESTAMP(6));
-- 現在成功；failed_attempts預設0，兩列active_user_id分別NULL/userID。
SELECT id,failed_attempts,active_user_id FROM password_reset_requests WHERE user_id=@r2_user;
DELETE FROM users WHERE id=@r2_user;
SELECT COUNT(*) FROM password_reset_requests WHERE id IN ('r2-request-1','r2-request-2');
-- 預期0；只刪本transaction剛建立的虛構fixture。
ROLLBACK;
```

### C. timestamp／Unicode／二進位／CHECK／特殊關係

用transaction虛構users與exercise再寫exercise_result，省略created_at，SELECT確認非NULL／UTC微秒；BLOB測試fixture用 `UNHEX('00017FFF')`，頭像與影片應round trip相同HEX、內容長度。影片file_size=0應CHECK失敗。

| 測試 | 預期 |
|---|---|
| 中文／emoji name、CUSTOM JSON LONGTEXT | 可round trip，沒有latin1亂碼 |
| chat_conversations one>=two／非法type | CHECK失敗 |
| rehab_plans condition不在fracture/stroke | CHECK失敗 |
| plan items order<0／sets或reps<=0 | CHECK失敗 |
| Boolean欄写2（含nullable is_complete） | CHECK失敗；is_complete=NULL仍合法 |
| 參考不存在父row的FK insert | 1452失敗 |
| delete有chat／plan／history／result依賴的user | 1451 restrict；不能拿cascade當修復 |
| delete虛構plan／history | respective items／video cascade；不影響其他使用者 |
| plan item文字exercise_id／session result多型ID | 不要求exercise表BIGINT FK，但其他NN/FK仍有效 |
| research_annotation_revisions相同(sample_id,revision)兩筆 | 應成功（普通index） |
| research sample相同(participant,client_sample_id)兩次 | duplicate唯一失敗 |
| sample FK不存在／兩筆相同grant(user,study) | 失敗 |
| audit actor／已刪樣本retention event無FK欄 | 可寫最小審計，不憑名稱加不存在的FK |
| research_retention_policies | 空表；沒有虛構policy default/正式期限 |

每組用ROLLBACK退出，不留下fixture。再跑validate_schema核對schema不變。跨兩connection驗證同user競爭active_reset：一方先INSERT未COMMIT，另一方同user INSERT應等待，第一方commit後第二方duplicate；最後只清本次fixture。此競態測試**待執行**；本輪沒有為此COMMIT fixture。

## Git／邊界確認

新增檔案僅位於docs/database-rebuild；原有01～05、Java、Dart、SQL Server migration、pom/application、Dockerfile未修改。後端 `git diff --check`檢查tracked diff；因新檔未git add，另外逐一 `git diff --no-index --check -- /dev/null <新檔>`，確保未tracked新增檔亦查whitespace。沒有git add/commit/push/merge/PR；沒有部署或Production SQL。

R2的13個檢查器tests與static PASS不等於MySQL執行通過。R2.5已補入真實execution／metadata／DML結果，因此僅該範圍改PASS；App全功能整合仍NOT RUN。完整原始resultsets及SQL/JSON證據見09。
