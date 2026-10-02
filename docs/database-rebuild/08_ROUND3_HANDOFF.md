# 08 — Round 3 Spring Boot/MySQL 遷移交接（本輪未實作）

## 接續原則／目前交付

從本機後端 `feat/rehab-ml-cloud-label` 的實際工作樹接續，Round2基準HEAD `d6fbb2e548e829823ce501d0ae3cf9ea47b3f28c`。本輪新增文件／SQL仍未git add/commit；沒有切main、push、部署、修改Azure或執行正式SQL。Flutter `feat/rehab-ml-poc` HEAD `429f200cefe085cbb72c8d612146cd296ec16045`，既有build-report修改勿覆蓋。

下一輪先讀06決策、07驗證，再檢查git status/diff。不要重寫Round1盤點或把SQL產出當SQL已執行；不要未授權改正式設定。Fresh Database Rebuild已決定不搬舊Azure測試資料、不保留舊ID。

| 交付 | 狀態 |
|---|---|
| V001 main18表／V002 optional11表 | 完整DDL，static PASS；MySQL execution NOT TESTED |
| 29表全部Entity欄／型別／constraints檢查 | PASS：238field+2generated、240column、33FK、68key/index、16CHECK |
| 靜態檢查器tests | 13/13 PASS；不是MySQL整合測試 |
| MySQL8.4.11CLI／Windows MySQL84 | 可用／Running，但無憑證登入 ERROR1045 |
| Hibernate／backend接MySQL | 尚未修改，NOT RUN |
| Flutter／Android／Render | 本輪未變更、未建置、未部署 |

### Schema順序

V001按檔案順序：users → exercise → exercise_result → user_bindings → friend_requests → friendships → password_reset_requests → chat_conversations → chat_messages → custom_rehab_exercises → custom_exercise_assignments → exercise_assignments → rehab_plans → rehab_plan_items → training_history → training_history_video → training_session_results → user_avatars。

選配V002：research_consents → research_samples → research_annotations → research_annotation_revisions → research_audit → research_grants → research_review_requests → research_grant_audit → research_export_audit → research_retention_policies → research_retention_events。

MySQL資料庫建置與constraints實測按07手動指令，先確認新隔離庫不存在；若已有同名庫停止。DDL不transaction回滾、不用IF NOT EXISTS忽略drift；保留版本與checksum。沒有seed與29以外的migration-history table。

## A. JDBC／Hibernate遷移（需要下一輪授權）

來源：`pom.xml:49-53` mssql-jdbc；`src/main/resources/application.properties:3,9,11,15,21` SQLServerDriver/SQLServerDialect、ddl-auto=update、show-sql=true、sql.init.mode=never。

下一輪最小方案：

- 用現有Spring Boot dependency management支援的 `com.mysql:mysql-connector-j`，不為此亂升Boot/Hibernate。
- Driver：`com.mysql.cj.jdbc.Driver`；Dialect：`org.hibernate.dialect.MySQLDialect`（或讓Hibernate依DB偵測，不能仍SQLServerDialect）。
- 本機隔離驗收URL範本：`jdbc:mysql://127.0.0.1:3306/rehab_r2_validation?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true`。沒有用户名／password值嵌入URL；沿用環境設定注入帳密。正式URL依託管商TLS規則，不以trustServerCertificate/allowPublicKeyRetrieval隨意繞過安全。
- 加 `spring.jpa.properties.hibernate.jdbc.time_zone=UTC`，確認session/JVM/API Instant與LocalDateTime均UTC；LocalDate plan_date不做錯誤時區平移。
- schema先受控套用V001/V002，Hibernate以 `ddl-auto=validate` 驗收；不用update自動補schema。驗證失敗需分析mapping/type，不准退回update掩蓋。
- `spring.sql.init.mode=never` 保留，不自動跑舊T-SQL。新的migration-framework需下一輪明確決議；版本執行紀錄與checksums不可省略。
- SQL log／bind-value log關閉敏感輸出；不要把hash/token、avatar/video/研究payload、姓名／email寫到測試報告。
- 實測Hibernate對Instant/DATETIME6、Boolean/TINYINT1、byte[]/LONGBLOB、generated未知欄與unique metadata的相容性，不宣稱static entity檢查等於Hibernate validate通過。

目前功能分支有11 research Entities/repositories，**只建18表不能直接跑未停用研究模組的backend**。主系統only驗收用明確非研究版本／控制research repository啟用；feature backend選29表，但研究真人收集仍維持關閉。禁止讓update自行生成研究表造成18/29界線失效。

## B. SQL Server Entity定義清理

Java相對前綴 `src/main/java/com/example/trainingsystems/entity/`；具體行號見04（目前仍可對照），Round3修改前再確認。

| Entity | 下一輪需要處理 |
|---|---|
| CustomRehabExerciseEntity | @Nationalized、nvarchar(200/2000/max) → utf8mb4 VARCHAR/LONGTEXT；不動keyframe／設定／pose語義 |
| TrainingHistoryEntity | NVARCHAR(100/200/MAX)、JSON字串 → VARCHAR/LONGTEXT，保留nullable及BigDecimal5,2 |
| TrainingHistoryVideoEntity | NVARCHAR(255/100)、VARBINARY(MAX) → VARCHAR/LONGBLOB；驗證LOB streaming，不意外改file_size |
| TrainingSessionResultEntity | NVARCHAR(255)等 → VARCHAR；多型exercise_id保持字串無FK |
| UserAvatarEntity | VARBINARY(MAX) → LONGBLOB；source_type=CUSTOM/GOOGLE、FK/restrict不變 |
| ResearchSampleEntity | nvarchar(max) → LONGTEXT；JSON格式契約不變 |
| ResearchAnnotationEntity／ResearchAnnotationRevisionEntity | note/review_note nvarchar(1000) → VARCHAR(1000) |
| User | account_id50與app max20明確保留；password可NULL；不新增第二套身份模型 |
| ExerciseResult | created_at仍DB-owned；驗證NN UTC default、insert省略、save後讀回；不得自行改時間產生而忘記schema |

移除SQL Server專屬columnDefinition時用合適MySQL mapping保留LONGTEXT/LONGBLOB，不能只刪annotation讓長JSON退化到VARCHAR255。@Nationalized的MySQL實際mapping需Hibernate驗證，不用假設UTF8已自動解決所有nationalized差異。

Generated欄 `users.account_id_normalized` STORED／reset.active_user_id VIRTUAL由schema管理，不需要任意新增可寫Entityfield；若要映射則明確不可INSERT/UPDATE。

## C. Native JDBC／BLOB／Range

`service/TrainingHistoryVideoService.java` 是本repo找到的SQLServer schema-qualified native JavaSQL來源：

- :192 INSERT INTO dbo.training_history_video；:259 UPDATE；:313、:364 SELECT；:433-438 SUBSTRING讀range。
- :230、:285 setBinaryStream；既有最大video預設由 `training.history.video.max-bytes` :49讀取，104857600bytes（100MiB）；READ_CHUNK_BYTES限制單次range約1MiB。

下一輪改成選定MySQL database下未帶dbo的表名／適當schema限定。`SUBSTRING(LONGBLOB,start+1,length)` 1-based offset仍需用真實Connector/J測試二進位回傳與越界，不能只替換字串便宣稱影片功能正常。

必要測試：

1. upload streaming、replace與transaction rollback；不把完整100MiB影片先讀成byte[]；驗證Connector/J setBinaryStream實際是否buffer。
2. max_allowed_packet、reverse proxy/request limits、JDBC heap預算與service上限協調；不要直接設無限制或假定LONGBLOB=可無限制上傳。
3. 206 Content-Range／Content-Length／Accept-Ranges、0起點／尾端／越界416、空／missing影片、1MiB分段的資料HASH一致。
4. FK history cascade delete、user ownership／therapist binding等既有授權仍有效。
5. 頭像JPEG/PNG/WEBP round trip與正確Content-Type，受限存取，不因遷移變成公開URL。

其他Spring Data @Query多為JPQL（Chat*/RehabPlan*/Research*），不要把JPQL機械翻譯成MySQL原生SQL。

## D. CUSTOM poseMeasurementRules缺口

Flutter `lib/models/custom_rehab_exercise.dart:117-118` 會輸出poseMeasurementRules；後端 `dto/CustomRehabExerciseDto.java:19-20` 只有keyframes/evaluationRules，`entity/CustomRehabExerciseEntity.java`、`service/CustomRehabExerciseService.java` 也未提供pose rules讀寫。

V001不加空置欄。Round3先確認現行RTMPose keyframe與anatomical規則需要的資料契約，決議DTO/validation/entity/service序列化與舊exercise預設，再用新migration候選 `pose_measurement_rules_json LONGTEXT NULL`；create→read→update→assign→patient load完整round trip測試。保留legacy evaluationRules與anatomical pose rules永久分離，不映射GLBXYZ成真人人體角度、不改訓練算法。

## E. 帳號刪除與FK整合

`service/AccountService.java:173-204` 會清assignments、ownedCUSTOM、exercise_result、binding、friends、research及avatar；目前未見完整清chat、rehab_plans、training_history、training_session_results。

新schema依來源RESTRICT；Round3先決議資料保留／可刪範圍，再transaction依子表先後清理，不得全體FK CASCADE掩蓋。至少建立有chat messages/conversations、plans/items、history/video、session_results、research annotation/revisions、avatar的虚構帳號，測刪除成功或安全回滾、其他使用者資料不受影響。研究audit／retention event無FK有意保留最小事件，不能因欄名追加FK。

## F. Seed／研究權限／上線限制

- exercise表目前空：先對照Flutter DEFAULT code IDs、API assignment IDs／BodyRehabAction mapping與舊seed来源，寫經review的獨立seed migration；**不搬舊Azure資料、不假定AUTO_INCREMENT會給原ID**。
- 不自動bootstrap管理者、同意或Retention Policy。本輪只建結構；首次研究管理者受控授權腳本須另做MySQL版本並經人工審批，不随V002跑。
- 未核准保存期限／必要研究程序／scope授權前，真人研究收集不啟用。撤回／expiry的主資料與外部export/backups處理限制需明確保留。
- ResearchAnnotationEntity的LABELED初始值与service DRAFT等狀態邏輯回歸測試；不能透過DBdefault或seed核准label。

## 下一輪具體順序與驗收條件

1. 確認本輪SQL/files、git status，執行static checker；由使用者用Workbench按07在全新隔離庫實跑MySQL並保存結果。任何generated/check/FK建表錯誤先修DDL，而非刪掉约束。
2. 下一輪授權後，另設MySQL测试profile／driver/dialect/UTC，修改表中SQLServer-specific definitions及TrainingHistoryVideoService dbo SQL；不改Flutter業務行為。
3. 確認18-only與29-research部署版本；Hibernate validate完整通過、不自動update。
4. 真實MySQL整合測試：密碼/Google/重設（unique競態/consumed/cascade）、帳號ID50/app20、多NULL、chat/friends/bindings、DEFAULT/CUSTOM save/assignment、plans、history/video/avatar/results、刪帳號、research权限/review/export/retention/idempotency。
5. DEFAULT catalog reviewed seed與pose rules契約補洞；真正build/tests據實回報，NOT RUN不可當PASS。
6. 經人工review後才安排Render環境變數／MySQL TLS／備份／回滾／部署健康檢查與Android E2E；本輪與下輪未授權前不做正式部署。

建議續作命令（唯讀／static）：

```powershell
git branch --show-current
git status --short
git rev-parse HEAD
git diff --stat
python -B docs/database-rebuild/mysql/validate_static.py
python -B -m unittest discover -s docs/database-rebuild/mysql -p test_validate_static.py -v
```

**Round 2在此停止，等待验收，不自行进入Round 3。**

官方參考：[Connector/J基本JDBC使用](https://dev.mysql.com/doc/connector-j/en/connector-j-usagenotes-basic.html)、[Connector/J時區處理設定](https://dev.mysql.com/doc/connector-j/en/connector-j-connp-props-datetime-types-processing.html)。這些URL是下一輪設計依據，不是已修改dependency／已驗證database的證明。
