INSERT INTO global_configs (config_key, config_value, description, created_at, modified_at)
VALUES
    ('POINT_SIGNUP_BONUS', '500', '회원가입 축하 포인트', NOW(), NOW()),
    ('POINT_BOARD_CREATE_COST', '500', '스페이스 생성 포인트 비용', NOW(), NOW()),
    ('POINT_POST_CREATE_REWARD', '50', '게시글 작성 적립 포인트', NOW(), NOW()),
    ('POINT_COMMENT_CREATE_REWARD', '10', '댓글 작성 적립 포인트', NOW(), NOW())
ON CONFLICT (config_key) DO NOTHING;

UPDATE global_configs
SET description = '신규 노비콘 상품 기본 가격 (기존 상품 가격에는 적용되지 않음)',
    modified_at = NOW()
WHERE config_key = 'NOBICON_PRICE'
  AND description = 'Default Noviicon purchase price';
