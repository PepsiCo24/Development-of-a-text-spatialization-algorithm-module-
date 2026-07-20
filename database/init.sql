BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS postgis;

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
    entity_status   VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    entity_progress INTEGER NOT NULL DEFAULT 0 CHECK (entity_progress BETWEEN 0 AND 100),
    entity_error    TEXT,
    entity_count    INTEGER NOT NULL DEFAULT 0,
    entity_extracted_at TIMESTAMPTZ,
    knowledge_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    knowledge_progress INTEGER NOT NULL DEFAULT 0 CHECK (knowledge_progress BETWEEN 0 AND 100),
    knowledge_error TEXT,
    attribute_count INTEGER NOT NULL DEFAULT 0,
    relation_count INTEGER NOT NULL DEFAULT 0,
    normalized_count INTEGER NOT NULL DEFAULT 0,
    knowledge_extracted_at TIMESTAMPTZ,
    spatial_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    spatial_progress INTEGER NOT NULL DEFAULT 0 CHECK (spatial_progress BETWEEN 0 AND 100),
    spatial_error TEXT,
    spatial_warnings TEXT,
    spatial_object_count INTEGER NOT NULL DEFAULT 0,
    spatial_extracted_at TIMESTAMPTZ,
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

CREATE TABLE IF NOT EXISTS dictionary (
    id              BIGSERIAL PRIMARY KEY,
    term_type       VARCHAR(64) NOT NULL,
    standard_name   VARCHAR(255) NOT NULL,
    aliases         TEXT,
    description     TEXT,
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    create_time     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(term_type, standard_name)
);

CREATE TABLE IF NOT EXISTS entity (
    id              BIGSERIAL PRIMARY KEY,
    document_id     BIGINT NOT NULL REFERENCES document(id) ON DELETE CASCADE,
    chunk_id        BIGINT NOT NULL REFERENCES document_chunk(id) ON DELETE CASCADE,
    entity_name     VARCHAR(255) NOT NULL,
    entity_type     VARCHAR(64) NOT NULL,
    confidence      NUMERIC(5,4) NOT NULL CHECK (confidence BETWEEN 0 AND 1),
    source_text     TEXT NOT NULL,
    page            INTEGER NOT NULL,
    source_start    INTEGER,
    source_end      INTEGER,
    provider        VARCHAR(32) NOT NULL,
    model           VARCHAR(128) NOT NULL,
    dictionary_id   BIGINT REFERENCES dictionary(id) ON DELETE SET NULL,
    standard_name   VARCHAR(255),
    normalization_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    create_time     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_entity_document ON entity(document_id, page);
CREATE INDEX IF NOT EXISTS idx_entity_type_name ON entity(entity_type, entity_name);
CREATE INDEX IF NOT EXISTS idx_entity_chunk ON entity(chunk_id);

CREATE TABLE IF NOT EXISTS entity_attribute (
    id BIGSERIAL PRIMARY KEY, document_id BIGINT NOT NULL REFERENCES document(id) ON DELETE CASCADE,
    entity_id BIGINT NOT NULL REFERENCES entity(id) ON DELETE CASCADE, attribute_type VARCHAR(64) NOT NULL,
    original_value VARCHAR(500) NOT NULL, confidence NUMERIC(5,4) NOT NULL CHECK (confidence BETWEEN 0 AND 1),
    source_text TEXT NOT NULL, page INTEGER NOT NULL, provider VARCHAR(32) NOT NULL, model VARCHAR(128) NOT NULL,
    create_time TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_entity_attribute_document ON entity_attribute(document_id, entity_id);

CREATE TABLE IF NOT EXISTS entity_relation (
    id BIGSERIAL PRIMARY KEY, document_id BIGINT NOT NULL REFERENCES document(id) ON DELETE CASCADE,
    source_entity_id BIGINT NOT NULL REFERENCES entity(id) ON DELETE CASCADE,
    target_entity_id BIGINT NOT NULL REFERENCES entity(id) ON DELETE CASCADE, relation_type VARCHAR(64) NOT NULL,
    confidence NUMERIC(5,4) NOT NULL CHECK (confidence BETWEEN 0 AND 1), source_text TEXT NOT NULL, page INTEGER NOT NULL,
    provider VARCHAR(32) NOT NULL, model VARCHAR(128) NOT NULL, create_time TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (source_entity_id <> target_entity_id)
);
CREATE INDEX IF NOT EXISTS idx_entity_relation_document ON entity_relation(document_id, relation_type);

CREATE TABLE IF NOT EXISTS spatial_object (
    id BIGSERIAL PRIMARY KEY, document_id BIGINT NOT NULL REFERENCES document(id) ON DELETE CASCADE,
    entity_id BIGINT REFERENCES entity(id) ON DELETE SET NULL, chunk_id BIGINT REFERENCES document_chunk(id) ON DELETE SET NULL,
    name VARCHAR(255) NOT NULL, object_type VARCHAR(64) NOT NULL, geometry_type VARCHAR(32) NOT NULL,
    geojson JSONB NOT NULL, geometry geometry(Geometry,4326) NOT NULL,
    confidence NUMERIC(5,4) NOT NULL CHECK (confidence BETWEEN 0 AND 1), source_text TEXT NOT NULL, page INTEGER NOT NULL,
    geocoding_source VARCHAR(128), provider VARCHAR(32) NOT NULL, model VARCHAR(128) NOT NULL,
    create_time TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_spatial_object_document ON spatial_object(document_id,object_type);
CREATE INDEX IF NOT EXISTS idx_spatial_object_geometry ON spatial_object USING GIST(geometry);

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

INSERT INTO dictionary (term_type, standard_name, aliases, description) VALUES
('STRATUM','寒武系','寒武纪地层|寒武纪','中国年代地层单位'),
('STRATUM','奥陶系','奥陶纪地层|O','中国年代地层单位'),
('LITHOLOGY','花岗岩','花岗质岩|花岗岩类','酸性侵入岩'),
('LITHOLOGY','灰岩','石灰岩|碳酸钙岩','碳酸盐岩'),
('MINERAL','铁','铁矿|Fe','金属矿种'),
('MINERAL','铜','铜矿|Cu','金属矿种'),
('GEOLOGICAL_AGE','燕山期','燕山运动期|燕山时代','中生代构造岩浆活动期')
ON CONFLICT (term_type, standard_name) DO NOTHING;

COMMIT;
