BEGIN;

CREATE TEMP TABLE demo_spatial_targets ON COMMIT DROP AS
SELECT id FROM document
WHERE name IN ('大冶矿区钻孔记录（标准演示）', '大冶矿区钻孔记录（演示）');

UPDATE document_chunk
SET content = replace(replace(content, '114.9300', '114.9384'), '30.1100', '30.0840')
WHERE document_id IN (SELECT id FROM demo_spatial_targets);

UPDATE entity
SET entity_name = replace(replace(entity_name, '114.9300', '114.9384'), '30.1100', '30.0840'),
    standard_name = replace(replace(standard_name, '114.9300', '114.9384'), '30.1100', '30.0840'),
    source_text = replace(replace(source_text, '114.9300', '114.9384'), '30.1100', '30.0840')
WHERE document_id IN (SELECT id FROM demo_spatial_targets);

UPDATE entity_attribute
SET source_text = replace(replace(source_text, '114.9300', '114.9384'), '30.1100', '30.0840')
WHERE document_id IN (SELECT id FROM demo_spatial_targets);

UPDATE entity_relation
SET source_text = replace(replace(source_text, '114.9300', '114.9384'), '30.1100', '30.0840')
WHERE document_id IN (SELECT id FROM demo_spatial_targets);

UPDATE spatial_object
SET geojson = '{"type":"Point","coordinates":[114.9384,30.0840]}',
    geometry = ST_SetSRID(ST_MakePoint(114.9384, 30.0840), 4326),
    source_text = replace(replace(source_text, '114.9300', '114.9384'), '30.1100', '30.0840')
WHERE document_id IN (SELECT id FROM demo_spatial_targets)
  AND geometry_type = 'Point';

COMMIT;
