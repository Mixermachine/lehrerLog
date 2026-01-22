-- PostgreSQL: Enable pgcrypto extension for gen_random_uuid()
-- H2: gen_random_uuid() is natively supported in PostgreSQL mode
-- CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE schools (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    code VARCHAR(50) NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_schools_code ON schools(code);
