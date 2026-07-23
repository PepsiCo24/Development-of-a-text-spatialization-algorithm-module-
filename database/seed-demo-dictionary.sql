INSERT INTO dictionary(term_type, standard_name, aliases, description, enabled, create_time, update_time)
VALUES
  ('GEOLOGICAL_AGE', '第四系', '第四纪|Q', '新生代第四纪地层', TRUE, NOW(), NOW()),
  ('GEOLOGICAL_AGE', '下三叠统', '早三叠世|T1', '三叠系下部年代地层单位', TRUE, NOW(), NOW()),
  ('STRATUM', '大冶组', '大冶组地层|T1d', '鄂东南地区三叠系地层单位', TRUE, NOW(), NOW()),
  ('LITHOLOGY', '闪长玢岩', '闪长斑岩|闪长玢岩体', '中性浅成侵入岩', TRUE, NOW(), NOW())
ON CONFLICT(term_type, standard_name) DO UPDATE SET
  aliases = EXCLUDED.aliases,
  description = EXCLUDED.description,
  enabled = TRUE,
  update_time = NOW();
