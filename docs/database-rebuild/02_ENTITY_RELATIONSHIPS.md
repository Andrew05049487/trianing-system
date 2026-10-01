# 02 — Entity 關係、PK/FK 與建表順序

資料來源與 `M`/`E` 定義見 [01_DATABASE_INVENTORY.md](01_DATABASE_INVENTORY.md)。本文件描述**原始碼預期**，不是已驗證的 Azure FK 清單。MySQL 建表前須導出正式 `sys.foreign_keys`、`sys.key_constraints`、`sys.indexes`，確認所有舊資料可滿足約束。除特別註明外，SQL migration 未寫 `ON DELETE`；MySQL 應先採 `RESTRICT/NO ACTION`，不可任意改為 CASCADE。

## `main`：18 表的鍵與關係

| 表（PK） | FK 欄 → 目標 | UNIQUE / INDEX | 刪除行為來源 |
|---|---|---|---|
| users (`id`) | 無 | email、friend_code；filtered google_subject、computed account_id_normalized 見 01 | 父表；刪除受子表限制 |
| exercise (`id`) | 無 | 未宣告 | 若有結果/指派，不可直接刪 |
| exercise_result (`id`) | user_id→users.id；exercise_id→exercise.id | 未宣告 | E，無 cascade |
| user_bindings (`id`) | patient_id→users.id；linked_user_id→users.id | `(patient_id,linked_user_id)` | E，無 cascade；AccountService 清理 |
| friend_requests (`id`) | sender_id→users.id；receiver_id→users.id | `(sender_id,receiver_id)` | E，無 cascade；AccountService 清理 |
| friendships (`id`) | user_low_id→users.id；user_high_id→users.id | `(user_low_id,user_high_id)` | E，無 cascade；AccountService 清理 |
| password_reset_requests (`id`) | user_id→users.id | `(user_id,created_at)`、`expires_at`、active-user filtered UNIQUE | M，user 刪除 CASCADE |
| chat_conversations (`id`) | participant_one_id→users.id；participant_two_id→users.id | `(participant_one_id,participant_two_id,conversation_type)`；兩個 participant index | M，無 cascade |
| chat_messages (`id`) | conversation_id→chat_conversations.id；sender_id→users.id | `(conversation_id,sent_at)`、`(conversation_id,read_at)` | M，無 cascade |
| custom_rehab_exercises (`id`) | created_by_therapist_id→users.id | therapist index | E，無 cascade；AccountService 刪其指派後刪動作 |
| custom_exercise_assignments (`id`) | custom_exercise_id→custom_rehab_exercises.id；patient_id→users.id；assigned_by_therapist_id→users.id | `(custom_exercise_id,patient_id)`；patient-active/therapist index | E，無 cascade；AccountService 手動清理 |
| exercise_assignments (`id`) | exercise_id→exercise.id；patient_id→users.id；assigned_by_therapist_id→users.id | `(exercise_id,patient_id)`；patient-active/therapist index | E，無 cascade；AccountService 手動清理 |
| rehab_plans (`id`) | patient_id→users.id | plan_id；`(patient_id,plan_date)`；patient-date index | M，無 cascade；Entity 本身對 items 有 orphanRemoval |
| rehab_plan_items (`id`) | rehab_plan_id→rehab_plans.id；**exercise_id 非 FK** | `(rehab_plan_id,exercise_id)`；plan-order index | M，隨 plan CASCADE |
| training_history (`id`) | user_id→users.id | `(user_id,client_timestamp)`；user-created index | E，無 cascade |
| training_history_video (`history_id`) | history_id→training_history.id | PK 即 FK | M，隨 history CASCADE |
| training_session_results (`id`) | patient_id→users.id；exercise_id 是多型文字鍵非 FK | session_id；patient-completed index | E，無 cascade |
| user_avatars (`user_id`) | user_id→users.id | PK 即 FK | M，無 cascade；AccountService 刪 avatar |

`AccountService.deleteAccount` 實際清理 assignment、owned CUSTOM、舊 `exercise_result`、binding、friend、research 與 avatar；**未見完整清理 chat、rehab_plans、training_history、training_session_results**。若這些有 FK，帳號刪除可能被阻止。Round 2 不可假設既有刪除功能可在新 MySQL FK 下成功，須用測試資料驗證，並另決定法定保留/清除政策。

## 功能分支：11 個研究表的鍵與關係

| 表（PK） | 實際 SQL FK | UNIQUE / INDEX | 刪除行為 |
|---|---|---|---|
| research_consents (`user_id`) | user_id→users.id | subject_id UNIQUE | 無 cascade；服務清理 |
| research_samples (`id`) | participant_user_id→users.id | `(participant_user_id,client_sample_id)`；participant-captured；expires_at filtered index | 無 cascade；subject_id 是邏輯鍵，不是 FK |
| research_annotations (`sample_id`) | sample_id→research_samples.id；therapist_user_id→users.id | PK 即 sample_id | 無 cascade；reviewer_user_id 無 FK |
| research_annotation_revisions (`id`) | sample_id→research_samples.id | `(sample_id,revision)` index（**非唯一**） | 無 cascade |
| research_audit (`id`) | 無 | 無 | 審計以文字 ID 保留，不能一律 cascade |
| research_grants (`id`) | user_id→users.id | `(user_id,study_id)` | 無 cascade；granted_by_user_id 無 FK |
| research_review_requests (`id`) | user_id→users.id | `(user_id,study_id)` | 無 cascade；decided_by_user_id 無 FK |
| research_grant_audit (`id`) | 無 | 無 | 保留授權事件 |
| research_export_audit (`id`) | 無 | 無 | 保留匯出事件 |
| research_retention_policies (`id`) | 無 | `(study_id,policy_version)` | 版本紀錄不與使用者硬綁 FK |
| research_retention_events (`id`) | 無 | 無 | 樣本刪除後保留最小處理事件 |

研究 cleanup 在 `ResearchAccountCleanupService` 手動先刪 revisions/annotations 再刪 samples，並清理 review request/grant；審計與 retention event 留存。MySQL 需與現有服務清理次序一致，不可憑欄名自行補 FK，尤其審計表與 `subject_id`。研究表只在功能分支，正式庫存在性待確認。

## 建議依賴順序（先建父表，後建子表）

1. `users`, `exercise`。
2. `user_bindings`, `friend_requests`, `friendships`, `password_reset_requests`, `user_avatars`, `custom_rehab_exercises`, `exercise_result`, `exercise_assignments`, `rehab_plans`, `training_history`, `training_session_results`, `chat_conversations`。
3. `rehab_plan_items`, `training_history_video`, `custom_exercise_assignments`, `chat_messages`。
4. **僅研究版**：`research_consents`, `research_samples`, `research_grants`, `research_review_requests`, `research_audit`, `research_grant_audit`, `research_export_audit`, `research_retention_policies`, `research_retention_events`（其中 samples 需 users）。
5. **僅研究版**：`research_annotations`, `research_annotation_revisions`（依賴 samples；annotations 另依賴 users）。

建表後再建立各 UNIQUE、CHECK、普通索引及 MySQL 版 filtered-index 替代方案；先校驗資料是否重複/違反 CHECK。MySQL FK 索引長度與 collation 必須和父鍵一致。匯入資料時保留原 `id`，校正 AUTO_INCREMENT 下一值，並核對所有 FK/列數/雜湊；不要使用 `FOREIGN_KEY_CHECKS=0` 當永久修復。
