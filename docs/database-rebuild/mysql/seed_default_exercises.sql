-- Opt-in catalog initialization; NOT historical Azure IDs.
-- Verified Flutter feat/rehab-ml-poc 429f200, lib/models/training_action.dart.
-- DB allocates IDs; patient routing matches exact action names, never list indexes.
-- bodyTest / 全身骨架偵測 is a detector test, intentionally not a prescription.
-- Run with no concurrent catalog writer; existing rows are never overwritten.
SET NAMES utf8mb4;
START TRANSACTION;
INSERT INTO exercise (exercise_name, description)
SELECT '翻掌訓練', '依治療師指派進行訓練'
WHERE NOT EXISTS (SELECT 1 FROM exercise WHERE exercise_name='翻掌訓練');
INSERT INTO exercise (exercise_name, description)
SELECT '側捏訓練', '依治療師指派進行訓練'
WHERE NOT EXISTS (SELECT 1 FROM exercise WHERE exercise_name='側捏訓練');
INSERT INTO exercise (exercise_name, description)
SELECT '站姿抬腳式訓練', '依治療師指派進行訓練'
WHERE NOT EXISTS (SELECT 1 FROM exercise WHERE exercise_name='站姿抬腳式訓練');
INSERT INTO exercise (exercise_name, description)
SELECT '畫圓訓練', '依治療師指派進行訓練'
WHERE NOT EXISTS (SELECT 1 FROM exercise WHERE exercise_name='畫圓訓練');
INSERT INTO exercise (exercise_name, description)
SELECT '伸手舉高訓練', '依治療師指派進行訓練'
WHERE NOT EXISTS (SELECT 1 FROM exercise WHERE exercise_name='伸手舉高訓練');
INSERT INTO exercise (exercise_name, description)
SELECT '雙手抬舉式', '依治療師指派進行訓練'
WHERE NOT EXISTS (SELECT 1 FROM exercise WHERE exercise_name='雙手抬舉式');
INSERT INTO exercise (exercise_name, description)
SELECT '手肘屈伸訓練', '依治療師指派進行訓練'
WHERE NOT EXISTS (SELECT 1 FROM exercise WHERE exercise_name='手肘屈伸訓練');
INSERT INTO exercise (exercise_name, description)
SELECT '翹手腕式', '依治療師指派進行訓練'
WHERE NOT EXISTS (SELECT 1 FROM exercise WHERE exercise_name='翹手腕式');
INSERT INTO exercise (exercise_name, description)
SELECT '左右彎手腕式', '依治療師指派進行訓練'
WHERE NOT EXISTS (SELECT 1 FROM exercise WHERE exercise_name='左右彎手腕式');
INSERT INTO exercise (exercise_name, description)
SELECT '坐站訓練', '依治療師指派進行訓練'
WHERE NOT EXISTS (SELECT 1 FROM exercise WHERE exercise_name='坐站訓練');
INSERT INTO exercise (exercise_name, description)
SELECT '側跨步訓練', '依治療師指派進行訓練'
WHERE NOT EXISTS (SELECT 1 FROM exercise WHERE exercise_name='側跨步訓練');
COMMIT;
SELECT id, exercise_name FROM exercise ORDER BY id;
