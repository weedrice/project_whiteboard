INSERT INTO global_configs (config_key, config_value, description, created_at, modified_at)
VALUES ('EMOTICON_IMAGE_MAX_COUNT', '20', '이모티콘 팩당 최대 이미지 개수', NOW(), NOW())
ON CONFLICT (config_key) DO NOTHING;
