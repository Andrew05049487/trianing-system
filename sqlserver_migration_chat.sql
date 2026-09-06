IF OBJECT_ID(N'dbo.chat_conversations', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.chat_conversations (
        id BIGINT IDENTITY(1,1) NOT NULL,
        participant_one_id BIGINT NOT NULL,
        participant_two_id BIGINT NOT NULL,
        conversation_type VARCHAR(20) NOT NULL,
        created_at DATETIME2(7) NOT NULL,
        updated_at DATETIME2(7) NOT NULL,
        last_message_text NVARCHAR(2000) NULL,
        last_message_at DATETIME2(7) NULL,
        CONSTRAINT pk_chat_conversations PRIMARY KEY (id),
        CONSTRAINT fk_chat_conversations_participant_one
            FOREIGN KEY (participant_one_id) REFERENCES dbo.users(id),
        CONSTRAINT fk_chat_conversations_participant_two
            FOREIGN KEY (participant_two_id) REFERENCES dbo.users(id),
        CONSTRAINT ck_chat_conversations_canonical
            CHECK (participant_one_id < participant_two_id),
        CONSTRAINT ck_chat_conversations_type
            CHECK (conversation_type IN ('THERAPIST', 'PEER')),
        CONSTRAINT uq_chat_conversation_participants_type
            UNIQUE (participant_one_id, participant_two_id, conversation_type)
    );

    CREATE INDEX ix_chat_conversations_participant_one
        ON dbo.chat_conversations(participant_one_id);
    CREATE INDEX ix_chat_conversations_participant_two
        ON dbo.chat_conversations(participant_two_id);
END;

IF OBJECT_ID(N'dbo.chat_messages', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.chat_messages (
        id BIGINT IDENTITY(1,1) NOT NULL,
        conversation_id BIGINT NOT NULL,
        sender_id BIGINT NOT NULL,
        text NVARCHAR(2000) NOT NULL,
        sent_at DATETIME2(7) NOT NULL,
        read_at DATETIME2(7) NULL,
        CONSTRAINT pk_chat_messages PRIMARY KEY (id),
        CONSTRAINT fk_chat_messages_conversation
            FOREIGN KEY (conversation_id)
            REFERENCES dbo.chat_conversations(id),
        CONSTRAINT fk_chat_messages_sender
            FOREIGN KEY (sender_id) REFERENCES dbo.users(id)
    );

    CREATE INDEX ix_chat_messages_conversation_sent_at
        ON dbo.chat_messages(conversation_id, sent_at);
    CREATE INDEX ix_chat_messages_conversation_read_at
        ON dbo.chat_messages(conversation_id, read_at);
END;
