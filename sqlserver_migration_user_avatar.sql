IF OBJECT_ID(N'dbo.user_avatars', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.user_avatars (
        user_id BIGINT NOT NULL,
        mime_type VARCHAR(32) NOT NULL,
        image_data VARBINARY(MAX) NOT NULL,
        source_type VARCHAR(16) NOT NULL,
        updated_at DATETIME2(3) NOT NULL
            CONSTRAINT df_user_avatars_updated_at DEFAULT SYSUTCDATETIME(),
        CONSTRAINT pk_user_avatars PRIMARY KEY (user_id),
        CONSTRAINT ck_user_avatars_source_type
            CHECK (source_type IN ('CUSTOM', 'GOOGLE')),
        CONSTRAINT fk_user_avatars_users FOREIGN KEY (user_id)
            REFERENCES dbo.users(id)
    );
END;
