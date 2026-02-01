CREATE TABLE parent_invites
(
    id         UUID                  DEFAULT gen_random_uuid() PRIMARY KEY,
    student_id UUID         NOT NULL REFERENCES students (id) ON DELETE CASCADE,
    code_hash  VARCHAR(128) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by UUID         NOT NULL REFERENCES users (id),
    used_by    UUID REFERENCES users (id),
    used_at    TIMESTAMP WITH TIME ZONE,
    status     VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_parent_invites_student_id ON parent_invites (student_id);
CREATE INDEX idx_parent_invites_status ON parent_invites (status);
CREATE INDEX idx_parent_invites_code_hash ON parent_invites (code_hash);

CREATE TABLE parent_student_links
(
    id             UUID                              DEFAULT gen_random_uuid() PRIMARY KEY,
    parent_user_id UUID                     NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    student_id     UUID                     NOT NULL REFERENCES students (id) ON DELETE CASCADE,
    status         VARCHAR(20)              NOT NULL DEFAULT 'ACTIVE',
    created_by     UUID                     NOT NULL REFERENCES users (id),
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at     TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_parent_links_parent_id ON parent_student_links (parent_user_id);
CREATE INDEX idx_parent_links_student_id ON parent_student_links (student_id);
