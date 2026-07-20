BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS app_user (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(64) NOT NULL UNIQUE,
    password_hash   VARCHAR(100) NOT NULL,
    display_name    VARCHAR(100) NOT NULL,
    role            VARCHAR(32) NOT NULL DEFAULT 'USER',
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    create_time     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS document (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    type            VARCHAR(32) NOT NULL,
    region          VARCHAR(128),
    year            SMALLINT,
    keyword         TEXT,
    summary         TEXT,
    file_path       VARCHAR(1024) NOT NULL,
    original_name   VARCHAR(255) NOT NULL,
    content_type    VARCHAR(150) NOT NULL,
    status          VARCHAR(32) NOT NULL DEFAULT 'UPLOADED',
    parse_progress  INTEGER NOT NULL DEFAULT 0 CHECK (parse_progress BETWEEN 0 AND 100),
    error_message   TEXT,
    page_count      INTEGER NOT NULL DEFAULT 0,
    chunk_count     INTEGER NOT NULL DEFAULT 0,
    parsed_at       TIMESTAMPTZ,
    file_size       BIGINT NOT NULL DEFAULT 0,
    created_by      BIGINT REFERENCES app_user(id),
    create_time     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_document_status ON document(status);
CREATE INDEX IF NOT EXISTS idx_document_region_year ON document(region, year);

CREATE TABLE IF NOT EXISTS document_chunk (
    id              BIGSERIAL PRIMARY KEY,
    document_id     BIGINT NOT NULL REFERENCES document(id) ON DELETE CASCADE,
    chunk_index     INTEGER NOT NULL,
    chapter_title   VARCHAR(255),
    content         TEXT NOT NULL,
    page_start      INTEGER NOT NULL,
    page_end        INTEGER NOT NULL,
    char_count      INTEGER NOT NULL,
    create_time     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(document_id, chunk_index)
);

CREATE INDEX IF NOT EXISTS idx_document_chunk_document ON document_chunk(document_id, chunk_index);
CREATE INDEX IF NOT EXISTS idx_document_chunk_content_fts ON document_chunk USING GIN (to_tsvector('simple', content));

CREATE TABLE IF NOT EXISTS system_log (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT REFERENCES app_user(id),
    module          VARCHAR(64) NOT NULL,
    action          VARCHAR(128) NOT NULL,
    request_method  VARCHAR(10),
    request_path    VARCHAR(512),
    client_ip       VARCHAR(64),
    status          VARCHAR(20) NOT NULL,
    error_message   TEXT,
    elapsed_ms      INTEGER,
    create_time     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_system_log_create_time ON system_log(create_time DESC);
CREATE INDEX IF NOT EXISTS idx_system_log_module ON system_log(module);

INSERT INTO app_user (username, password_hash, display_name, role)
VALUES ('admin', crypt('admin123', gen_salt('bf')), '系统管理员', 'ADMIN')
ON CONFLICT (username) DO NOTHING;

COMMIT;
