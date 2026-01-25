CREATE TABLE task_files (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    object_key TEXT NOT NULL,
    size_bytes BIGINT NOT NULL,
    mime_type TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_task_files_task_id ON task_files(task_id);

CREATE TABLE submission_files (
    id UUID PRIMARY KEY,
    submission_id UUID NOT NULL REFERENCES task_submissions(id) ON DELETE CASCADE,
    object_key TEXT NOT NULL,
    size_bytes BIGINT NOT NULL,
    mime_type TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_submission_files_submission_id ON submission_files(submission_id);

CREATE TABLE storage_plans (
    id UUID PRIMARY KEY,
    name TEXT NOT NULL,
    max_total_bytes BIGINT NOT NULL,
    max_file_bytes BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE storage_subscriptions (
    id UUID PRIMARY KEY,
    owner_type TEXT NOT NULL,
    owner_id UUID NOT NULL,
    plan_id UUID NOT NULL REFERENCES storage_plans(id),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    starts_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ends_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_storage_subscriptions_owner ON storage_subscriptions(owner_type, owner_id);

CREATE TABLE storage_usage (
    owner_type TEXT NOT NULL,
    owner_id UUID NOT NULL,
    used_total_bytes BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (owner_type, owner_id)
);

ALTER TABLE storage_subscriptions
    ADD CONSTRAINT storage_subscriptions_owner_type_check
        CHECK (owner_type IN ('SCHOOL', 'TEACHER'));

ALTER TABLE storage_usage
    ADD CONSTRAINT storage_usage_owner_type_check
        CHECK (owner_type IN ('SCHOOL', 'TEACHER'));

INSERT INTO storage_plans (id, name, max_total_bytes, max_file_bytes)
SELECT '00000000-0000-0000-0000-000000000001', 'Default', 104857600, 5242880
WHERE NOT EXISTS (
    SELECT 1 FROM storage_plans WHERE id = '00000000-0000-0000-0000-000000000001'
);

INSERT INTO storage_subscriptions (id, owner_type, owner_id, plan_id, active, starts_at)
SELECT s.id, 'SCHOOL', s.id, '00000000-0000-0000-0000-000000000001', TRUE, CURRENT_TIMESTAMP
FROM schools s
WHERE NOT EXISTS (
    SELECT 1 FROM storage_subscriptions ss WHERE ss.id = s.id
);

INSERT INTO storage_usage (owner_type, owner_id, used_total_bytes, updated_at)
SELECT 'SCHOOL', s.id, 0, CURRENT_TIMESTAMP
FROM schools s
WHERE NOT EXISTS (
    SELECT 1 FROM storage_usage su WHERE su.owner_type = 'SCHOOL' AND su.owner_id = s.id
);
