BEGIN;

ALTER TABLE document ADD COLUMN IF NOT EXISTS entity_status VARCHAR(32) NOT NULL DEFAULT 'PENDING';
ALTER TABLE document ADD COLUMN IF NOT EXISTS entity_progress INTEGER NOT NULL DEFAULT 0;
ALTER TABLE document ADD COLUMN IF NOT EXISTS entity_error TEXT;
ALTER TABLE document ADD COLUMN IF NOT EXISTS entity_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE document ADD COLUMN IF NOT EXISTS entity_extracted_at TIMESTAMPTZ;

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
    create_time     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_entity_document ON entity(document_id, page);
CREATE INDEX IF NOT EXISTS idx_entity_type_name ON entity(entity_type, entity_name);
CREATE INDEX IF NOT EXISTS idx_entity_chunk ON entity(chunk_id);

COMMIT;
