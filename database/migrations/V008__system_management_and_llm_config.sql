ALTER TABLE system_log ADD COLUMN IF NOT EXISTS provider VARCHAR(32);
ALTER TABLE system_log ADD COLUMN IF NOT EXISTS model VARCHAR(128);
ALTER TABLE system_log ADD COLUMN IF NOT EXISTS function_name VARCHAR(128);

CREATE TABLE IF NOT EXISTS llm_config (
    id BIGSERIAL PRIMARY KEY,
    provider VARCHAR(32) NOT NULL UNIQUE,
    base_url VARCHAR(500) NOT NULL,
    model_name VARCHAR(128) NOT NULL,
    api_key TEXT,
    temperature NUMERIC(3,2) NOT NULL DEFAULT 0.10 CHECK (temperature BETWEEN 0 AND 2),
    prompt_template TEXT,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    create_time TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO llm_config(provider,base_url,model_name,temperature,enabled) VALUES
('deepseek','https://api.deepseek.com/v1','deepseek-chat',0.10,FALSE),
('qwen','https://dashscope.aliyuncs.com/compatible-mode/v1','qwen-plus',0.10,FALSE)
ON CONFLICT (provider) DO NOTHING;
