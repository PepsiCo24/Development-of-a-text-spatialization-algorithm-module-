BEGIN;

CREATE EXTENSION IF NOT EXISTS postgis;

ALTER TABLE document ADD COLUMN IF NOT EXISTS spatial_status VARCHAR(32) NOT NULL DEFAULT 'PENDING';
ALTER TABLE document ADD COLUMN IF NOT EXISTS spatial_progress INTEGER NOT NULL DEFAULT 0;
ALTER TABLE document ADD COLUMN IF NOT EXISTS spatial_error TEXT;
ALTER TABLE document ADD COLUMN IF NOT EXISTS spatial_warnings TEXT;
ALTER TABLE document ADD COLUMN IF NOT EXISTS spatial_object_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE document ADD COLUMN IF NOT EXISTS spatial_extracted_at TIMESTAMPTZ;

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

COMMIT;
