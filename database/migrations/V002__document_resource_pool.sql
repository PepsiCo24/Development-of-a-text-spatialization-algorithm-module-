BEGIN;

ALTER TABLE document ADD COLUMN IF NOT EXISTS original_name VARCHAR(255);
ALTER TABLE document ADD COLUMN IF NOT EXISTS content_type VARCHAR(150);

UPDATE document
SET original_name = COALESCE(original_name, name),
    content_type = COALESCE(content_type, 'application/octet-stream');

ALTER TABLE document ALTER COLUMN original_name SET NOT NULL;
ALTER TABLE document ALTER COLUMN content_type SET NOT NULL;

COMMIT;
