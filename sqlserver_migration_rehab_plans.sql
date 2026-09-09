IF OBJECT_ID(N'dbo.rehab_plans', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.rehab_plans (
        id BIGINT IDENTITY(1,1) NOT NULL,
        plan_id NVARCHAR(100) NOT NULL,
        patient_id BIGINT NOT NULL,
        created_by NVARCHAR(50) NOT NULL,
        plan_date DATE NOT NULL,
        condition_type NVARCHAR(20) NOT NULL,
        created_at DATETIME2(7) NOT NULL
            CONSTRAINT df_rehab_plans_created_at DEFAULT SYSUTCDATETIME(),
        updated_at DATETIME2(7) NOT NULL
            CONSTRAINT df_rehab_plans_updated_at DEFAULT SYSUTCDATETIME(),
        CONSTRAINT pk_rehab_plans PRIMARY KEY (id),
        CONSTRAINT uq_rehab_plans_plan_id UNIQUE (plan_id),
        CONSTRAINT uq_rehab_plans_patient_date UNIQUE (patient_id, plan_date),
        CONSTRAINT fk_rehab_plans_patient FOREIGN KEY (patient_id)
            REFERENCES dbo.users(id),
        CONSTRAINT ck_rehab_plans_condition
            CHECK (condition_type IN (N'fracture', N'stroke'))
    );
END;
GO

IF OBJECT_ID(N'dbo.rehab_plan_items', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.rehab_plan_items (
        id BIGINT IDENTITY(1,1) NOT NULL,
        rehab_plan_id BIGINT NOT NULL,
        exercise_id NVARCHAR(100) NOT NULL,
        item_order INT NOT NULL,
        sets INT NOT NULL,
        reps_per_set INT NOT NULL,
        done BIT NOT NULL
            CONSTRAINT df_rehab_plan_items_done DEFAULT (0),
        CONSTRAINT pk_rehab_plan_items PRIMARY KEY (id),
        CONSTRAINT uq_rehab_plan_items_plan_exercise
            UNIQUE (rehab_plan_id, exercise_id),
        CONSTRAINT fk_rehab_plan_items_plan FOREIGN KEY (rehab_plan_id)
            REFERENCES dbo.rehab_plans(id) ON DELETE CASCADE,
        CONSTRAINT ck_rehab_plan_items_order CHECK (item_order >= 0),
        CONSTRAINT ck_rehab_plan_items_sets CHECK (sets > 0),
        CONSTRAINT ck_rehab_plan_items_reps CHECK (reps_per_set > 0)
    );
END;
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = N'idx_rehab_plans_patient_date'
      AND object_id = OBJECT_ID(N'dbo.rehab_plans')
)
    CREATE INDEX idx_rehab_plans_patient_date
        ON dbo.rehab_plans(patient_id, plan_date);
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = N'idx_rehab_plan_items_plan_order'
      AND object_id = OBJECT_ID(N'dbo.rehab_plan_items')
)
    CREATE INDEX idx_rehab_plan_items_plan_order
        ON dbo.rehab_plan_items(rehab_plan_id, item_order);
GO
