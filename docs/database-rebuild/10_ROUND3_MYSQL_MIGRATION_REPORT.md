# 10 — Round 3 MySQL Migration Report

## 1. Status / scope

**PASS — Round 3 本機後端整合與 MySQL 遷移已完成。正式部署、Android 與 Flutter 真機 E2E：NOT RUN。**

日期：2026-10-03。僅修改後端。沒有 Push、部署 Render、連線 Azure、啟用真人研究收集或開始 Round 4。

使用者已確認 Auto-Deploy 停用並批准本機施工。這是使用者確認，不是透過 Render 控制台重新查證。

## 2. Git 整合與版本

- 開始時 backend：feat/rehab-ml-cloud-label，HEAD `7be57b5284731db4f7de45d7325d2053714f7df2`；原始工作樹乾淨。
- 原 main `94132993`，ML ahead 11 / behind 0；祖先檢查通過，已使用 `git switch main`、`git merge --ff-only feat/rehab-ml-cloud-label` 正常整合。
- 後續修改在 main；沒有新分支、Force Push、丟棄 commit 或破壞性清理。
- Flutter 僅核對契約：feat/rehab-ml-poc `429f200cefe085cbb72c8d612146cd296ec16045`，ahead master 10 / behind 0。既有 `android/build/reports/problems/problems-report.html` 未修改／還原。
- refs 為本機實測；沒有 fetch／push，不能宣稱遠端 refs 的即時狀態。
- R1～R2.5 文件 01～09、V001、V002、validate_schema.sql、validate_static.py 均無 Git diff。
- V001 LF-normalized SHA-256：`6B731EC0153FE54A40F75C819D1CF9C4269C609AEB785007987268A678C4156A`。
- V002 LF-normalized SHA-256：`C1661E45C2733EE9F2AA1EED2072235690A29632C34B991DC9A6BF90BE64310A`。
- FF checkout 的 Windows CRLF 換行使實體檔案 raw SHA 分別變成 BB915349CCEEAF20AC13D7626755F8811C658334AEA35A24F637ADC9B78225C4／61DD71F2DD2AB6E368C3CD19FE3151DE41FBF570CE314CD4D119D7B90B6725DB；正規化雜湊與 R2.5 一致，沒有修改／重播舊版 migration。

## 3. Spring Boot / JDBC / UTC

- Spring Boot 保持 3.2.0；Hibernate 6.3.1.Final；未升級 Java／框架。實測 JDK 21.0.8、Maven 3.9.16。
- Connector/J 由 Boot 管理，實際 artifact `mysql-connector-j-8.1.0.jar`；真實 MySQL 8.4.11 測試通過。移除 mssql-jdbc；H2 改 test scope，正式 JAR 不包含 H2／SQL Server driver。
- driver `com.mysql.cj.jdbc.Driver`，dialect `org.hibernate.dialect.MySQLDialect`。
- 根目錄與 resources/application.properties 同步使用 DB_URL／DB_USERNAME／DB_PASSWORD，`ddl-auto=validate`，`spring.sql.init.mode=never`，show-sql=false。
- 本機 URL：`jdbc:mysql://127.0.0.1:3306/rehab_r2_validation?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&useServerPrepStmts=true&characterEncoding=UTF-8`。
- Hikari：`tinyInt1isBit=false` 防止 Connector/J 將 TINYINT(1) 回報成 BIT；`useServerPrepStmts=true` 支援影片二進位 streaming。
- Hibernate preferred boolean TINYINT／Instant TIMESTAMP、hibernate.jdbc.time_zone=UTC，對應已驗證 DATETIME(6)。
- 實際測試先發現 BIT metadata validation 錯誤；上述修正後通過。之後 DB-generated LocalDateTime 在 Asia/Taipei JVM 出現 8 小時偏移；僅調 preserveInstants 沒解決，未保留此嘗試。
- 最終 main 在 Spring 啟動前統一 JVM default UTC；以 useMainMethod=ALWAYS 測試真正入口。JDBC／Hibernate／JVM UTC 一致，DB-generated LocalDateTime、研究 Instant 往返均通過，精度保留至 DATETIME(6)。
- Friend／binding／avatar／history／video 的 LocalDateTime 新寫入改為 UTC。client_timestamp 原字串保持原樣；plan_date 仍是業務日期，RehabPlanService 明確保留 Asia/Taipei，未把日期當成 UTC instant。
- MySQL CLI 的獨立 session 仍顯示 SYSTEM；Spring application JDBC session 實測 +00:00。没有修改 MySQL global 時區。

## 4. 29 Entity mapping 與 metadata

每列均為真實 Hibernate validate PASS，非 H2 結果。V003 只新增一欄，沒有新增表／修改 R2.5 的鍵或 CHECK。

| Entity | Table | 結果／必要修正 |
|---|---|---|
| User | users | PASS；VARCHAR(50) account_id，商業驗證仍 4～20；JSON 隱藏 password／googleSubject |
| Exercise | exercise | PASS |
| ExerciseResult | exercise_result | PASS；DB UTC 預設 created_at 保持 insertable/updatable=false |
| UserBinding | user_bindings | PASS；UTC |
| FriendRequest | friend_requests | PASS；UTC |
| Friendship | friendships | PASS；UTC |
| PasswordResetCredential | password_reset_requests | PASS；generated active_user_id 不由 Entity INSERT/UPDATE |
| ChatConversationEntity | chat_conversations | PASS；enum 明確 VARCHAR JDBC type |
| ChatMessageEntity | chat_messages | PASS；Instant UTC |
| CustomRehabExerciseEntity | custom_rehab_exercises | PASS；移除 Nationalized，JSON LONGTEXT；V003 nullable LONGTEXT |
| CustomExerciseAssignmentEntity | custom_exercise_assignments | PASS；TINYINT boolean |
| ExerciseAssignmentEntity | exercise_assignments | PASS |
| RehabPlanEntity | rehab_plans | PASS；DATE／Instant |
| RehabPlanItemEntity | rehab_plan_items | PASS |
| TrainingHistoryEntity | training_history | PASS；VARCHAR 字串、JSON LONGTEXT、DECIMAL 保留 |
| TrainingHistoryVideoEntity | training_history_video | PASS；LONGBLOB |
| TrainingSessionResultEntity | training_session_results | PASS；VARCHAR／LONGTEXT 原用途與 DECIMAL 不變 |
| UserAvatarEntity | user_avatars | PASS；LONGBLOB、enum VARCHAR、UTC |
| ResearchConsentEntity | research_consents | PASS |
| ResearchSampleEntity | research_samples | PASS；payload LONGTEXT、Instant UTC |
| ResearchAnnotationEntity | research_annotations | PASS；note／review LONGTEXT |
| ResearchAnnotationRevisionEntity | research_annotation_revisions | PASS；note／review LONGTEXT |
| ResearchAuditEntity | research_audit | PASS |
| ResearchGrantEntity | research_grants | PASS |
| ResearchReviewRequestEntity | research_review_requests | PASS |
| ResearchGrantAuditEntity | research_grant_audit | PASS |
| ResearchExportAuditEntity | research_export_audit | PASS |
| ResearchRetentionPolicyEntity | research_retention_policies | PASS |
| ResearchRetentionEventEntity | research_retention_events | PASS |

實際 MySQL metadata：

| 項目 | R3 結果 |
|---|---:|
| MySQL | 8.4.11 |
| Schema | rehab_r2_validation |
| Main / Research / total tables | 18 / 11 / 29 |
| Columns | 241（原 240 + V003 1） |
| Generated columns | 2 |
| FK | 33 |
| 明定鍵／索引 | 68 |
| 自動 FK 支援索引 | 7 |
| 實體鍵／索引總數 | 75 |
| Enforced CHECK | 16 |
| Non-expected drift | 0 |

`run-local.ps1 -Action Metadata` 讀取不可變 R2 validator，在記憶體的 column manifest 加入 V003，再逐欄比對 type／nullable／default／collation／generated、全部 table／FK／index／CHECK。所有 DRIFT／EXTRA 結果為空；UNEXPECTED_INDEX_REVIEW 僅原 7 個自動 FK 支援索引。實際 SHOW TABLES 逐名核對上述 29 表。

## 5. 本機權限與安全設定

- 已建立 `rehab_app@localhost`，SHOW GRANTS 實測只有全域 USAGE 與 rehab_r2_validation 的 SELECT／INSERT／UPDATE／DELETE；沒有 DDL 或其他 schema 權限。
- Migration 使用經批准的本機 root 互動登入；一般 App 使用限定 CRUD user，權限分離。
- 隨機 application 密碼以 Windows DPAPI PSCredential 保存於 Git 忽略的 `.local/mysql-app.credential.xml`；沒有 plaintext credential／API key／HMAC secret 被新增至 Git、SQL、設定或報告。
- 本機 helper 在 process environment 暫時設定 DB/HMAC；CLI 使用暫時 MYSQL_PWD，沒有 password 命令列參數，finally 移除。不可將本機 credential copy 給另一台機器／Render。
- Setup 有版本／29-table schema 關卡，existing credential 阻止覆寫；已有 user 而 credential 遺失時 CREATE USER 明確失敗，不自行 reset password。
- Research production 開關維持 `RESEARCH_COLLECTION_ENABLED=false`，沒有持久化正式管理者／consent／retention policy。

## 6. Service / Repository / 影片

- TrainingHistoryVideoService 所有 dbo.training_history_video 改為 MySQL table 名；保留 PreparedStatement.setBinaryStream、1-based binary SUBSTRING、最多 1 MiB Range chunk。
- 實測 upload 1 MiB+73 bytes、尾段 byte-for-byte、overwrite、完整小檔 download、206 Content-Range／Content-Length、416 invalid range、404 missing video、invalid token／non-owner 拒絕、history delete → video FK CASCADE。
- `max_allowed_packet=67108864`（64 MiB）不代表 streaming 可突破單一值上限。新預設 HTTP file 32 MiB、request 33 MiB、service 33554432 bytes；即使 override 到 100 MiB，service 額外查 packet limit 並保留 1 MiB metadata 空間，超限 413，不取消限制。
- 65 MiB packet rejection 使用偽 size 的 synthetic MultipartFile 驗證拒絕分支，**沒有實際傳輸 65 MiB 或 100 MiB**。最大實際成功影片 fixture 為 1 MiB+73 bytes；生產負載壓力測試 NOT RUN。
- 其餘 Repository 仍使用 JPQL／Spring Data，沒有機械改寫成 native SQL。
- AccountDataCleanupService 在既有 deleteAccount 交易內按 message → conversation → plan/items → history/video → session results 清理；結合原 assignment/custom/friend/binding/avatar 與研究 cleanup，再刪 users。
- 保留既有 Restrictive FK；只有原 3 個 CASCADE 生效，沒有新增「全部 CASCADE」。
- 實測患者含 chat／plan／history/video／result 的刪除、therapist-owned CUSTOM 與 assignment 清理、其他患者 history 保留、研究 payload 清除／minimal audit 保留、最後一位研究管理者刪除失敗及前段 binding 清理 rollback。
- Rollback 驗證需從交易外觀察，因此一項測試短暫提交兩個虛構帳號，再由 finally 只刪該 fixtures；最後精確 row count 確認沒有殘留。

## 7. poseMeasurementRules / V003

- 新檔 `mysql/V003__custom_pose_rules.sql` 已在本機隔離庫成功執行 **一次**：
  nullable LONGTEXT `custom_rehab_exercises.pose_measurement_rules_json`。
- 沒重跑 V001／V002；V003 不是 idempotent 重跑腳本，正式環境應由版本管理確保只執行一次。
- DTO JsonNode `poseMeasurementRules`；Entity JSON 欄位；Service serialize/read/edit 與 assignment patient load 完整通過。
- 契約取自 Flutter 真實 `PoseMeasurementRule.toJson()`：measurement、targetAngleDegrees、toleranceDegrees、feedbackTooLow／High；不是 measurementType。
- 支援目前 Flutter 8 種左右 elbow／knee／shoulder abduction/flexion rule；有限角度、0～180 range、positive tolerance、最多 32 條／feedback 1000 字 validation。
- legacy DB NULL → response []；legacy update 缺欄位 → 保留既有 rules；explicit [] → 清除。未改 legacy evaluationRules 或 keyframe/FK/人體演算法。
- 本機 Create → Read → Update → Assign → Patient Load PASS。

## 8. DEFAULT catalog

- 新增 opt-in `mysql/seed_default_exercises.sql`，不是 startup seeder。
- 來源：Flutter 429f200 的 lib/models/training_action.dart；assigned_default_exercise_page.dart 實際以 exact action name（移除空白）路由至 ActionListScreen。
- 11 個名稱：翻掌訓練、側捏訓練、站姿抬腳式訓練、畫圓訓練、伸手舉高訓練、雙手抬舉式、手肘屈伸訓練、翹手腕式、左右彎手腕式、坐站訓練、側跨步訓練。
- 全身骨架偵測是 detector test，未當成復健處方 seed。
- INSERT SELECT WHERE NOT EXISTS，不 overwrite existing row，DB AUTO_INCREMENT 產生新 ID；沒有宣稱重現 Azure ID，也不將 Flutter list index 當 DB ID。
- 已本機執行兩次，均相同 11 列／ID。新 DB 本輪分配 ID 9～19，因之前 rolled-back insert 消耗 auto-increment；不 reset counters，也不依賴此 ID 範圍。
- Plan item 的 exercise_id 原為 action string，仍不當成 exercise.id FK。

## 9. 測試證據與結果

| 實際命令／驗收 | 結果 | 證據 |
|---|---|---|
| 遷移前 mvn test | 229 tests / 2 failures / 0 errors / 0 skipped | target/r3-baseline.log |
| mvn -q -DskipTests compile | PASS | target/r3-compile.log |
| run-local.ps1 -Action Test | 20/20 PASS，真實 MySQL | target/r3-mysql-test.log、Surefire MySqlMigrationIntegrationTest |
| run-local.ps1 -Action FullTest（mvn test） | 249/249 PASS / 0 failure / 0 error / 0 skip | target/r3-full-test.log、target/surefire-reports |
| run-local.ps1 -Action Package（mvn package -DskipTests） | PASS，exit 0 | target/r3-package.log |
| Spring Boot actual start（18083） | PASS，Tomcat listening、Hibernate validate | target/r3-start.log |
| GET /api/exercise/list | 200，11 catalog items，包含站姿抬腳 | 本機 HTTP 實測 |
| POST forgot，虛構不存在帳號 | 200，generic response，未寄真實郵件 | 本機 HTTP 實測 |
| Metadata manifest / SHOW TABLES | PASS，29表逐名、241欄等 | target/r3-metadata.log |
| Seed 重跑 | PASS，11列／ID不變 | target/r3-seed.log、target/r3-seed-repeat.log |
| git diff --check | PASS（一般 repo 設定；LF/CRLF warning 非內容錯誤） | 實際命令 exit 0 |
| Flutter / Android / Render / Azure | NOT RUN／未修改或連線 | 明確非本輪部署 |

20 個真實 MySQL 測試涵蓋：29 Entity／metadata／UTC；患者 register/login/account ID；legacy BCrypt migration/null password；verified Google mocked boundary；password reset generated active key/hash/BCrypt；pose-rule round trip；DEFAULT assignment/result idempotency／UTC DB default；therapist register/bind/plan read/update；peer friend/THERAPIST chat/read receipt/avatar binary；video streaming/range/replace/cascade/auth；patient与therapist account cleanup；collection disabled/no-policy／unrelated scope；research grant/request/decision/audit；consent/upload dedupe/draft/submit/independent review/export/withdrawal/expiry idempotency；research deletion audit；failed cleanup rollback；packet override rejection。

Google token verifier 与 email delivery 为测试 mock，实际外部 Google OAuth／Resend E2E **NOT RUN**；现有 verifier／transport unit tests 在完整 suite 中通过，不能冒充外部服务验收。

### 原有两项安全失败

不是被忽略、跳过或放宽断言：

1. LegacyBindingControllerSecurityTest：原 legacy endpoint 未早期禁止 THERAPIST，返回 404 而非 403，且有效资料可能绕过 HMAC 建绑定／改角色。新增早期 403，指向既有 HMAC therapist API；FAMILY 流程保持。
2. UserJsonSecurityTest：原 User 序列化暴露 password／googleSubject。仅对两字段 JsonIgnore；不修改密码验证／Google登录逻辑或合法 login DTO。

最终完整 suite 两项均 PASS。失败基准仍在证据及本报告保留。

其餘過程失敗（皆已修正並重新測試）：BIT metadata mismatch；fixture 使用錯誤 measurementType／friend response id／不存在的 total_time 欄位；UTC LocalDateTime 偏移；fixture binding code 未大寫。沒有用 --force、FOREIGN_KEY_CHECKS=0、ddl-auto=update 或修改舊 migration 規避問題。

## 10. 最終本機資料與 artifact

- 精確 COUNT(*) 核對所有 29 表：exercise=11；其餘 28 表全部 0。沒有虛構研究政策、權限、同意、樣本、標註或帳號殘留。
- JAR：`target/trainingsystem-0.0.1-SNAPSHOT.jar`，55,581,800 bytes（本次 package）；包含 Connector/J，沒有 H2／mssql。
- 本機 HTTP app 已停止，未留下背景服務。
- 未提交 target log／JAR／credential／sample export；報告只保存非敏感結果。

## 11. Round 4 Flutter 契約交接

本輪沒有修改 Flutter：

1. 保留 DB actual ID 的 assignment/list 契約；新 catalog ID 不是舊 Azure ID，測試切换後需重新登入與重新讀取指派。
2. poseMeasurementRules 欄位完全沿用 measurement JSON，檢驗 Editor→save/load→assign→patient 的 Android UI E2E；旧 payload 缺欄位安全。
3. training/history LocalDateTime 現為 UTC（既有無 offset JSON shape 未改）；Flutter 的本地顯示與日期分組需核對，不可盲當作台灣 wall clock。plan_date 仍 Asia/Taipei 業務日期。
4. video 預設 32 MiB，需檢查 client file-size UX／413與 Range video_player；沒有 frontend 改動。
5. Google／Resend real external、手機 skeleton／計次／TV regression、本機→Flutter完整 E2E 均待人工驗收；不把 backend synthetic integration 当真机 PASS。
6. legacy THERAPIST binding 已禁止，Flutter 应使用既有 /api/therapist/patients/bind＋HMAC；其他 auth/REST 契约不变。

## 12. Round 5 部署待辦／限制

- Render 沒有部署；DB_URL 等正式環境尚未改動。不得直接啟動新 artifact 連舊 SQL Server。
- 本機 root／DPAPI credential 不適用雲端；雲端由管理者建立新 application CRUD user 與獨立 migration user，使用受控 TLS 網路與秘密管理。
- 新雲端 MySQL 先由已授權操作者套用 V001→V002→V003；已完成 R2.5 的本機庫 **只需已套用的 V003，不重跑舊 migration**。
- ddl-auto=validate 不會新增欄位，缺任何 table／V003 會啟動失敗；migration 在部署 artifact 前完成。
- catalog seed 是獨立 opt-in，停寫時執行，review names；不是 Azure資料搬移。
- 必要環境：DB_URL、DB_USERNAME、DB_PASSWORD、CUSTOM_EXERCISE_IDENTITY_SECRET、PASSWORD_RESET_SECRET；保留现有 Google/Resend 等环境；RESEARCH_COLLECTION_ENABLED=false。正式 DB_URL 配置 UTC session／utf8mb4／TLS；不要將 secret 写进此文件。
- 現有 legacy training/history upload／exercise 等部分端點仍沿用原授權模式，本輪沒有重構全部API安全；部署前另行做權限稽核，不宣稱全面安全認證。
- 限制：未搬移 Azure 舊資料；未測大容量影片壓力／雲端 TLS；沒有 Android 真機／正式 Google與郵件／Render/MySQL雲端 E2E，這些標記 NOT RUN。
- 真人研究蒐集維持關閉，尚不具備正式研究程序／保存政策批准／部署驗收條件，不能收集真人研究資料。

## 13. 可重複執行與續作

在後端 main，保留 DPAPI ignored credential，本機 MySQL84 running：

```powershell
.\docs\database-rebuild\mysql\run-local.ps1 -Action Test
.\docs\database-rebuild\mysql\run-local.ps1 -Action FullTest
.\docs\database-rebuild\mysql\run-local.ps1 -Action Metadata
.\docs\database-rebuild\mysql\run-local.ps1 -Action Package
```

**不要重新跑 Setup 或 V001/V002/V003。** Setup 只給尚未建 application user 的相同已驗證隔離環境；metadata／test 不執行 DDL。此測試類只有明確 local rehab_r2_validation 的 DB_URL 才啟用；一般無 DB 的 mvn test 會 skip 此 integration class，不能當真實 MySQL驗收。

本輪停止於 Round 4 前；下一輪先閱讀本文件、核對 Git HEAD/status 與 MySQL現況，不重做 Round 1～3。

## 14. 本輪新增／修改檔案完整清單

- `.gitignore`
- `application.properties`
- `pom.xml`
- `src/main/java/com/example/trainingsystems/TrainingSystemApplication.java`
- `src/main/java/com/example/trainingsystems/controller/BindingController.java`
- `src/main/java/com/example/trainingsystems/controller/TrainingHistoryController.java`
- `src/main/java/com/example/trainingsystems/dto/CustomRehabExerciseDto.java`
- `src/main/java/com/example/trainingsystems/entity/ChatConversationEntity.java`
- `src/main/java/com/example/trainingsystems/entity/CustomRehabExerciseEntity.java`
- `src/main/java/com/example/trainingsystems/entity/FriendRequest.java`
- `src/main/java/com/example/trainingsystems/entity/Friendship.java`
- `src/main/java/com/example/trainingsystems/entity/ResearchAnnotationEntity.java`
- `src/main/java/com/example/trainingsystems/entity/ResearchAnnotationRevisionEntity.java`
- `src/main/java/com/example/trainingsystems/entity/ResearchSampleEntity.java`
- `src/main/java/com/example/trainingsystems/entity/TrainingHistoryEntity.java`
- `src/main/java/com/example/trainingsystems/entity/TrainingHistoryVideoEntity.java`
- `src/main/java/com/example/trainingsystems/entity/TrainingSessionResultEntity.java`
- `src/main/java/com/example/trainingsystems/entity/User.java`
- `src/main/java/com/example/trainingsystems/entity/UserAvatarEntity.java`
- `src/main/java/com/example/trainingsystems/entity/UserBinding.java`
- `src/main/java/com/example/trainingsystems/service/AccountService.java`
- `src/main/java/com/example/trainingsystems/service/CustomRehabExerciseService.java`
- `src/main/java/com/example/trainingsystems/service/FriendService.java`
- `src/main/java/com/example/trainingsystems/service/TrainingHistoryVideoService.java`
- `src/main/java/com/example/trainingsystems/service/UserAvatarService.java`
- `src/main/resources/application.properties`
- `src/test/java/com/example/trainingsystems/service/AccountServiceTest.java`
- `src/test/java/com/example/trainingsystems/service/TrainingHistoryVideoServiceTest.java`
- `docs/database-rebuild/10_ROUND3_MYSQL_MIGRATION_REPORT.md`
- `docs/database-rebuild/mysql/V003__custom_pose_rules.sql`
- `docs/database-rebuild/mysql/run-local.ps1`
- `docs/database-rebuild/mysql/seed_default_exercises.sql`
- `src/main/java/com/example/trainingsystems/service/AccountDataCleanupService.java`
- `src/test/java/com/example/trainingsystems/service/MySqlMigrationIntegrationTest.java`
