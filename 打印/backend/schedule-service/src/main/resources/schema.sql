CREATE TABLE IF NOT EXISTS jw_accounts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    student_id VARCHAR(64) NOT NULL,
    jw_password VARCHAR(1024) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_jw_accounts_user ON jw_accounts(user_id);
CREATE INDEX IF NOT EXISTS idx_jw_accounts_student ON jw_accounts(student_id);

CREATE TABLE IF NOT EXISTS course_schedules (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    student_id VARCHAR(64) NOT NULL,
    xnm VARCHAR(16) NOT NULL,
    xqm VARCHAR(16) NOT NULL,
    kcmc VARCHAR(255) NOT NULL,
    xqj VARCHAR(32),
    jcs VARCHAR(64),
    cdmc VARCHAR(255),
    xm VARCHAR(128),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_course_user_term ON course_schedules(user_id, xnm, xqm);
CREATE INDEX IF NOT EXISTS idx_course_student_term ON course_schedules(student_id, xnm, xqm);
