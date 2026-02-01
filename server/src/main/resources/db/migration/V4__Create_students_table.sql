CREATE TABLE students
(
    id         UUID                  DEFAULT gen_random_uuid() PRIMARY KEY,
    school_id  UUID         NOT NULL REFERENCES schools (id) ON DELETE CASCADE,
    first_name VARCHAR(100) NOT NULL,
    last_name  VARCHAR(100) NOT NULL,
    created_by UUID         NOT NULL REFERENCES users (id),
    version    BIGINT       NOT NULL DEFAULT 1,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_students_school_id ON students (school_id);

