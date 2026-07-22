BEGIN;

ALTER TABLE entity ADD COLUMN IF NOT EXISTS review_status VARCHAR(32) NOT NULL DEFAULT 'PENDING';
ALTER TABLE entity_attribute ADD COLUMN IF NOT EXISTS review_status VARCHAR(32) NOT NULL DEFAULT 'PENDING';
ALTER TABLE entity_relation ADD COLUMN IF NOT EXISTS review_status VARCHAR(32) NOT NULL DEFAULT 'PENDING';

CREATE INDEX IF NOT EXISTS idx_entity_review_status ON entity(document_id, review_status, confidence);
CREATE INDEX IF NOT EXISTS idx_entity_attribute_review_status ON entity_attribute(document_id, review_status);
CREATE INDEX IF NOT EXISTS idx_entity_relation_review_status ON entity_relation(document_id, review_status);

COMMIT;
