BEGIN;

ALTER TABLE document ADD COLUMN IF NOT EXISTS parse_progress INTEGER NOT NULL DEFAULT 0;
ALTER TABLE document ADD COLUMN IF NOT EXISTS error_message TEXT;
ALTER TABLE document ADD COLUMN IF NOT EXISTS page_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE document ADD COLUMN IF NOT EXISTS chunk_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE document ADD COLUMN IF NOT EXISTS parsed_at TIMESTAMPTZ;

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

COMMIT;
