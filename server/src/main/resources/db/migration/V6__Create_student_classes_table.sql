CREATE TABLE student_classes
(
    student_id      UUID                     NOT NULL REFERENCES students (id) ON DELETE CASCADE,
    school_class_id UUID                     NOT NULL REFERENCES school_classes (id) ON DELETE CASCADE,
    valid_from      TIMESTAMP WITH TIME ZONE NOT NULL,
    valid_till      TIMESTAMP WITH TIME ZONE,
    version         BIGINT                   NOT NULL DEFAULT 1,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (student_id, school_class_id, valid_from)
);

CREATE INDEX idx_student_classes_student_id ON student_classes (student_id);
CREATE INDEX idx_student_classes_school_class_id ON student_classes (school_class_id);
