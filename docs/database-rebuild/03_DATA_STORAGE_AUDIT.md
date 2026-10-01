# 03 — 資料實際儲存位置盤點

此為程式碼路徑盤點，不是 Azure 現場資料盤點。`main` 與研究功能分支差異見 01；App 本機資料不會因搬遷 SQL Server 而自動出現在 MySQL。

| 資料 | 後端／雲端 | Flutter 本機／GitHub 原始碼 | 搬遷判斷 |
|---|---|---|---|
| 預設復健動作與 ID | `exercise` 表存 `BIGINT` ID、名稱、描述；`ExerciseRepository.findAll()` 與 `UnifiedExerciseAssignmentService.parseDefaultId()` 使用 DB ID | `lib/models/training_action.dart` 的 `kTrainingActions` 是**另一本地動作定義**；`assigned_default_exercise_page.dart` 用名稱比對導向 ActionListScreen；3D 示範清單／GLB 資產在 Flutter Git | 須匯出 `exercise` 真實列及 ID；不能由 Flutter 清單推算原 DB ID。名稱比對耦合與未匹配項須測 |
| MediaPipe / RTMPose 判定規則與角度閾值 | 未見獨立閾值表 | `lib/actions/*.dart`、`lib/features/pose_measurement/evaluation/pose_measurement_rule_resolver.dart`、各 evaluator；模型 `assets/rtmdet.onnx`、`assets/rtmpose_wholebody.onnx`、`assets/models/`；Android native MediaPipe 配置另屬 app 程式/資產 | 不應把程式常數誤建成 SQL 資料。預設動作的 MediaPipe 規則只為特定動作 hardcode，並非完整 DB 可配置規則 |
| 3D 自訂動作／Keyframe | 正式治療師 repository 走遠端；`custom_rehab_exercises.keyframes_json`、`evaluation_rules_json` 與 repetitions/sets/hold/rest/duration 欄；指派在 `custom_exercise_assignments` | `LocalCustomExerciseRepository` 仍可將整個 JSON 存 SharedPreferences key `custom_rehab_exercises_v1`，但 `custom_exercise_repository_selection.dart` 明言正式流程不自動同步這批舊本機資料；GLB/viewer 資產在 Git | 遷移遠端表不等於移入每支手機的本機草稿；需另列本機資料搬遷策略。Flutter 有 `poseMeasurementRules`，後端 DTO/Entity 未見對應持久欄，需測 round-trip |
| 動作評分規則 | 舊 `exercise_result`、`training_history` 分數與模板欄、`training_session_results` 存**結果**；CUSTOM `evaluation_rules_json` 存治療師規則 | `lib/models/evaluation_rule.dart`、`pose_measurement` 與 `custom_exercise/training` 的演算法/閾值在程式；`PoseMeasurementRule` 在 Flutter JSON | 規則與結果不得混淆；確認 CUSTOM pose rules 在遠端是否實際保存，避免 MySQL 搬遷時以為有欄 |
| AI 動作模板 | `training_history.template_*` 僅存歷史評分、模板 ID/名稱/摘要；未見獨立模板 Entity/表 | `LocalMotionTemplateRepository` 存 App documents `/templates/template_*.json`；`BodyMotionTemplate`/Analyzer 程式在 Git；預設影片與示範模型在 assets | 模板原件主要本機，搬 DB 不會複製到其他裝置。AI 聊天透過 `chat_repository.dart` 的 Cloudflare Worker→Gemini；聊天內容另看 `chat_*` 路徑，不是動作模板表 |
| 患者／治療師帳號 | `users`；角色為單一 `role`；email/password hash 或 Google subject、binding/friend/account code；重設碼 hash 在 `password_reset_requests` | `AppSession`/個人偏好在 SharedPreferences；Google 身分 Token 不應作 DB 密碼搬遷 | 必須保留 users.id、雜湊、Google subject、代碼與唯一性/大小寫規則；不能從本機 session 重建帳號 |
| 綁定／好友／聊天室 | `user_bindings`、`friend_requests`、`friendships`、`chat_conversations`、`chat_messages` | Chat UI 可有本機/記憶體抽象，但正式 REST 功能讀雲端表 | FK 關係與原 ID 須保留；已讀時間在 `chat_messages.read_at` |
| 復健計畫／指派 | `rehab_plans`、`rehab_plan_items`、`exercise_assignments`、`custom_exercise_assignments` | Flutter 顯示/暫存；不等於權威資料 | `rehab_plan_items.exercise_id` 是文字且無 exercise FK；必須依實際值對照目錄，不可轉成 BIGINT |
| 訓練歷史／結果／影片 | `training_history`、`training_session_results`、舊 `exercise_result`；上傳影片位元組在 `training_history_video.video_data`，不是 Render filesystem | `LocalHistoryRepository` 用 SharedPreferences，含待同步紀錄；本機 video path 可能指裝置檔案。`HistoryService` 明確上傳歷史與影片 | 須分辨已上傳／待上傳；不能把手機絕對路徑當作影片內容；大 BLOB 搬遷需串流/校驗 |
| 自訂／Google 頭像 | `user_avatars.image_data` 可存自訂或 Google 來源圖片；與 users 分表 | `LocalUserAvatarRepository` 保留 App documents 圖檔路徑與 SharedPreferences、Google URL/initials fallback | DB 圖片與每台裝置本機檔非同一份；須核對 `source_type`，不可搬本機路徑作 binary |
| ML 研究骨架樣本 | **僅研究分支預期**：`research_samples.payload_json`、consents、annotation/revision、grant/audit、retention；未證實已部署 | `MlSampleRepository` 存 App documents `/rehab_ml_samples/{id}.json`；`MlResearchSync` 以 per-user SharedPreferences 保存 pending/synced ID；`ml/train.py`、資料格式/特徵程式在 Git | 本機收集同意與雲端同步同意分離；不應自動上傳歷史本機檔。正式收集開關預設 false，且未設核准保留政策不可視為永久保存。訓練腳本/範本不是已訓練模型或 DB 資料 |

## 交叉比對的 API／Repository 路徑

- 後端 `ExerciseController`/`ExerciseService`、`UnifiedExerciseAssignmentService`、`CustomRehabExerciseService`、`RehabPlanService`、`TrainingHistoryVideoService`、`ResearchDataService` 分別對應上表的主域。
- Flutter `RemoteCustomExerciseRepository` 是正式治療師自訂動作來源，`LocalCustomExerciseRepository` 是保留的本機實作；不會自動互傳。
- Flutter 歷史 `LocalHistoryRepository` + `HistoryService.uploadPendingRecords` 與後端歷史 API 是雙層資料；SQL Server 搬遷只涵蓋已到雲端的資料。
- Flutter ML `MlSampleRepository` + `MlResearchSync` 與後端 `ResearchDataService` 是明確 opt-in 的雙層資料；不能從 SQL 表是否存在推論患者同意。

## 必須向現場確認

1. Azure 每表實際列數、最大 ID、是否有遺留未宣告欄或 migration 未執行；本輪沒有查詢。
2. 各手機的未同步歷史、CUSTOM 本機 JSON、模板、影片、ML 樣本是否需另行搬遷；資料庫 dump 無法涵蓋。
3. `poseMeasurementRules` 遠端 round-trip；目前 Flutter/後端合約表面不一致。
4. 預設 `exercise.id` 實值與 Flutter 名稱映射，不能自行產生新 ID 替代。
5. 匯出/備份中的研究資料與保留政策；刪除主庫不代表其他副本已清除。
