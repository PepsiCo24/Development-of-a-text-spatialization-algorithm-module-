BEGIN;

ALTER TABLE document ADD COLUMN IF NOT EXISTS knowledge_status VARCHAR(32) NOT NULL DEFAULT 'PENDING';
ALTER TABLE document ADD COLUMN IF NOT EXISTS knowledge_progress INTEGER NOT NULL DEFAULT 0;
ALTER TABLE document ADD COLUMN IF NOT EXISTS knowledge_error TEXT;
ALTER TABLE document ADD COLUMN IF NOT EXISTS attribute_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE document ADD COLUMN IF NOT EXISTS relation_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE document ADD COLUMN IF NOT EXISTS normalized_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE document ADD COLUMN IF NOT EXISTS knowledge_extracted_at TIMESTAMPTZ;

CREATE TABLE IF NOT EXISTS dictionary (
    id BIGSERIAL PRIMARY KEY, term_type VARCHAR(64) NOT NULL, standard_name VARCHAR(255) NOT NULL,
    aliases TEXT, description TEXT, enabled BOOLEAN NOT NULL DEFAULT TRUE,
    create_time TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, update_time TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(term_type, standard_name)
);

ALTER TABLE entity ADD COLUMN IF NOT EXISTS dictionary_id BIGINT REFERENCES dictionary(id) ON DELETE SET NULL;
ALTER TABLE entity ADD COLUMN IF NOT EXISTS standard_name VARCHAR(255);
ALTER TABLE entity ADD COLUMN IF NOT EXISTS normalization_status VARCHAR(32) NOT NULL DEFAULT 'PENDING';

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

INSERT INTO dictionary (term_type, standard_name, aliases, description) VALUES
('STRATUM','寒武系','寒武纪地层|寒武纪','中国年代地层单位'),('STRATUM','奥陶系','奥陶纪地层|奥陶纪','中国年代地层单位'),
('LITHOLOGY','花岗岩','花岗质岩|花岗岩类','酸性侵入岩'),('LITHOLOGY','灰岩','石灰岩|碳酸钙岩','碳酸盐岩'),
('MINERAL','铁','铁矿|Fe','金属矿种'),('MINERAL','铜','铜矿|Cu','金属矿种'),
('GEOLOGICAL_AGE','燕山期','燕山运动期|燕山时代','中生代构造岩浆活动期')
ON CONFLICT (term_type, standard_name) DO NOTHING;

COMMIT;
