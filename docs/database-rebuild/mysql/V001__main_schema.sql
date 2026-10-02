-- Round 2 fresh rebuild: 18 MAIN tables, MySQL 8.4 LTS / InnoDB.
-- Select a NEW EMPTY schema explicitly before execution. No seeds, no Azure data.
-- Run once, in order, without --force. DDL implicitly commits; not replay-safe.
-- Record SHA-256 externally. Later changes require a new V003+ migration.
SET NAMES utf8mb4 COLLATE utf8mb4_0900_as_cs;
SET SESSION time_zone = '+00:00';

CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    email VARCHAR(255) COLLATE utf8mb4_0900_as_ci NOT NULL,
    password VARCHAR(255) NULL,
    name VARCHAR(255) NULL,
    goal VARCHAR(255) NULL,
    role VARCHAR(20) NOT NULL,
    binding_code VARCHAR(12) NULL,
    friend_code VARCHAR(12) NULL,
    account_id VARCHAR(50) COLLATE utf8mb4_0900_as_ci NULL,
    account_id_normalized VARCHAR(50) COLLATE utf8mb4_0900_as_ci
        GENERATED ALWAYS AS (LOWER(account_id)) STORED,
    google_subject VARCHAR(255) COLLATE utf8mb4_0900_bin NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_users_email (email),
    UNIQUE KEY uq_users_friend_code (friend_code),
    UNIQUE KEY uq_users_account_id_normalized (account_id_normalized),
    UNIQUE KEY uq_users_google_subject (google_subject)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE exercise (
    id BIGINT NOT NULL AUTO_INCREMENT,
    exercise_name VARCHAR(255) NOT NULL,
    description VARCHAR(255) NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE exercise_result (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    exercise_id BIGINT NOT NULL,
    rep_count INT NOT NULL,
    accuracy DECIMAL(5,2) NULL,
    progress DECIMAL(5,2) NULL,
    speed_state VARCHAR(255) NULL,
    is_complete TINYINT(1) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    PRIMARY KEY (id),
    CONSTRAINT fk_exercise_result_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_exercise_result_exercise FOREIGN KEY (exercise_id) REFERENCES exercise(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_exercise_result_complete CHECK (is_complete IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE user_bindings (
    id BIGINT NOT NULL AUTO_INCREMENT,
    patient_id BIGINT NOT NULL,
    linked_user_id BIGINT NOT NULL,
    relationship VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_user_binding (patient_id, linked_user_id),
    CONSTRAINT fk_user_bindings_patient FOREIGN KEY (patient_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_user_bindings_linked FOREIGN KEY (linked_user_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE friend_requests (
    id BIGINT NOT NULL AUTO_INCREMENT,
    sender_id BIGINT NOT NULL,
    receiver_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    responded_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_friend_request (sender_id, receiver_id),
    CONSTRAINT fk_friend_requests_sender FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_friend_requests_receiver FOREIGN KEY (receiver_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE friendships (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_low_id BIGINT NOT NULL,
    user_high_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_friendship (user_low_id, user_high_id),
    CONSTRAINT fk_friendships_low FOREIGN KEY (user_low_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_friendships_high FOREIGN KEY (user_high_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE password_reset_requests (
    id VARCHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    code_hash VARCHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    failed_attempts INT NOT NULL DEFAULT 0,
    consumed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    version BIGINT NULL,
    -- VIRTUAL: a STORED generated column based on user_id would forbid its FK CASCADE.
    -- Consumed rows produce NULL, so many historical rows coexist; one active per user.
    active_user_id BIGINT GENERATED ALWAYS AS
        (CASE WHEN consumed_at IS NULL THEN user_id ELSE NULL END) VIRTUAL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_password_reset_active_user (active_user_id),
    KEY ix_password_reset_user_created (user_id, created_at),
    KEY ix_password_reset_expires_at (expires_at),
    CONSTRAINT fk_password_reset_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE chat_conversations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    participant_one_id BIGINT NOT NULL,
    participant_two_id BIGINT NOT NULL,
    conversation_type VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    last_message_text VARCHAR(2000) NULL,
    last_message_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_chat_conversation_participants_type (participant_one_id, participant_two_id, conversation_type),
    KEY idx_chat_conversation_one (participant_one_id),
    KEY idx_chat_conversation_two (participant_two_id),
    -- Omit action clauses on CHECK operands; MySQL default NO ACTION is RESTRICT in InnoDB.
    CONSTRAINT fk_chat_conversation_one FOREIGN KEY (participant_one_id) REFERENCES users(id),
    CONSTRAINT fk_chat_conversation_two FOREIGN KEY (participant_two_id) REFERENCES users(id),
    CONSTRAINT ck_chat_participant_order CHECK (participant_one_id < participant_two_id),
    CONSTRAINT ck_chat_conversation_type CHECK (conversation_type IN ('THERAPIST','PEER'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE chat_messages (
    id BIGINT NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT NOT NULL,
    sender_id BIGINT NOT NULL,
    text VARCHAR(2000) NOT NULL,
    sent_at DATETIME(6) NOT NULL,
    read_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    KEY idx_chat_message_sent (conversation_id, sent_at),
    KEY idx_chat_message_read (conversation_id, read_at),
    CONSTRAINT fk_chat_message_conversation FOREIGN KEY (conversation_id) REFERENCES chat_conversations(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_chat_message_sender FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE custom_rehab_exercises (
    id VARCHAR(128) NOT NULL,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(2000) NOT NULL,
    created_by_therapist_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    repetitions INT NOT NULL,
    sets INT NOT NULL,
    hold_seconds DOUBLE NOT NULL,
    rest_seconds DOUBLE NOT NULL,
    duration DOUBLE NOT NULL,
    keyframes_json LONGTEXT NOT NULL,
    evaluation_rules_json LONGTEXT NOT NULL,
    PRIMARY KEY (id),
    KEY idx_custom_rehab_exercises_therapist (created_by_therapist_id),
    CONSTRAINT fk_custom_exercise_therapist FOREIGN KEY (created_by_therapist_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE custom_exercise_assignments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    custom_exercise_id VARCHAR(128) NOT NULL,
    patient_id BIGINT NOT NULL,
    assigned_by_therapist_id BIGINT NOT NULL,
    assigned_at DATETIME(6) NOT NULL,
    is_active TINYINT(1) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_custom_exercise_assignment (custom_exercise_id, patient_id),
    KEY idx_custom_exercise_assignments_patient_active (patient_id, is_active),
    KEY idx_custom_exercise_assignments_therapist (assigned_by_therapist_id),
    CONSTRAINT fk_custom_assignment_exercise FOREIGN KEY (custom_exercise_id) REFERENCES custom_rehab_exercises(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_custom_assignment_patient FOREIGN KEY (patient_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_custom_assignment_therapist FOREIGN KEY (assigned_by_therapist_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_custom_assignment_active CHECK (is_active IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE exercise_assignments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    exercise_id BIGINT NOT NULL,
    patient_id BIGINT NOT NULL,
    assigned_by_therapist_id BIGINT NOT NULL,
    assigned_at DATETIME(6) NOT NULL,
    is_active TINYINT(1) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_exercise_assignment (exercise_id, patient_id),
    KEY idx_exercise_assignments_patient_active (patient_id, is_active),
    KEY idx_exercise_assignments_therapist (assigned_by_therapist_id),
    CONSTRAINT fk_exercise_assignment_exercise FOREIGN KEY (exercise_id) REFERENCES exercise(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_exercise_assignment_patient FOREIGN KEY (patient_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_exercise_assignment_therapist FOREIGN KEY (assigned_by_therapist_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_exercise_assignment_active CHECK (is_active IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE rehab_plans (
    id BIGINT NOT NULL AUTO_INCREMENT,
    plan_id VARCHAR(100) NOT NULL,
    patient_id BIGINT NOT NULL,
    created_by VARCHAR(50) NOT NULL,
    plan_date DATE NOT NULL,
    condition_type VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    PRIMARY KEY (id),
    UNIQUE KEY uq_rehab_plans_plan_id (plan_id),
    UNIQUE KEY uq_rehab_plans_patient_date (patient_id, plan_date),
    KEY idx_rehab_plans_patient_date (patient_id, plan_date),
    CONSTRAINT fk_rehab_plan_patient FOREIGN KEY (patient_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_rehab_plan_condition CHECK (condition_type IN ('fracture','stroke'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE rehab_plan_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    rehab_plan_id BIGINT NOT NULL,
    -- String action identifier, NOT a foreign key to exercise.id.
    exercise_id VARCHAR(100) NOT NULL,
    item_order INT NOT NULL,
    sets INT NOT NULL,
    reps_per_set INT NOT NULL,
    done TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_rehab_plan_items_plan_exercise (rehab_plan_id, exercise_id),
    KEY idx_rehab_plan_items_plan_order (rehab_plan_id, item_order),
    CONSTRAINT fk_rehab_plan_item_plan FOREIGN KEY (rehab_plan_id) REFERENCES rehab_plans(id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT ck_rehab_plan_item_order CHECK (item_order >= 0),
    CONSTRAINT ck_rehab_plan_item_sets CHECK (sets > 0),
    CONSTRAINT ck_rehab_plan_item_reps CHECK (reps_per_set > 0),
    CONSTRAINT ck_rehab_plan_item_done CHECK (done IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE training_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    session_id VARCHAR(100) NULL,
    action_name VARCHAR(100) NOT NULL,
    difficulty INT NOT NULL,
    duration_seconds INT NOT NULL,
    completed_reps INT NOT NULL DEFAULT 0,
    target_reps INT NOT NULL,
    mistake_count INT NOT NULL,
    mistake_logs LONGTEXT NULL,
    body_score DECIMAL(5,2) NULL,
    body_rep_scores LONGTEXT NULL,
    template_score DECIMAL(5,2) NULL,
    template_id VARCHAR(160) NULL,
    template_name VARCHAR(200) NULL,
    template_valid_rep_count INT NOT NULL DEFAULT 0,
    template_rep_scores LONGTEXT NULL,
    template_difference_summary LONGTEXT NULL,
    client_timestamp VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_training_history_user_ts (user_id, client_timestamp),
    KEY idx_training_history_user_created (user_id, created_at DESC),
    CONSTRAINT fk_training_history_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE training_history_video (
    history_id BIGINT NOT NULL,
    file_name VARCHAR(255) NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    video_data LONGBLOB NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (history_id),
    CONSTRAINT fk_training_video_history FOREIGN KEY (history_id) REFERENCES training_history(id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT ck_training_video_size CHECK (file_size > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE training_session_results (
    id BIGINT NOT NULL AUTO_INCREMENT,
    session_id VARCHAR(36) NOT NULL,
    patient_id BIGINT NOT NULL,
    exercise_type VARCHAR(16) NOT NULL,
    -- DEFAULT numeric / CUSTOM string ID: polymorphic, intentionally no FK.
    exercise_id VARCHAR(128) NOT NULL,
    exercise_name VARCHAR(255) NOT NULL,
    completed_sets INT NOT NULL,
    completed_reps INT NOT NULL,
    target_sets INT NOT NULL,
    target_reps INT NOT NULL,
    started_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NOT NULL,
    duration_seconds BIGINT NOT NULL,
    completion_status VARCHAR(24) NOT NULL,
    score DECIMAL(5,2) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_training_session_results_session_id (session_id),
    KEY idx_training_results_patient_completed (patient_id, completed_at),
    CONSTRAINT fk_training_session_patient FOREIGN KEY (patient_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE user_avatars (
    user_id BIGINT NOT NULL,
    mime_type VARCHAR(32) NOT NULL,
    image_data LONGBLOB NOT NULL,
    source_type VARCHAR(16) NOT NULL,
    updated_at DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    PRIMARY KEY (user_id),
    CONSTRAINT fk_user_avatar_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_user_avatar_source CHECK (source_type IN ('CUSTOM','GOOGLE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
