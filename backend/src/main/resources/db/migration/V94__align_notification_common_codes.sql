-- noviis:migration-phase expand

INSERT INTO common_codes (type_code, type_name, description, created_at, modified_at) VALUES
('NOTIFICATION_TYPE', '알림 유형', '사용자 알림 설정 화면에 노출할 알림 유형', NOW(), NOW())
ON CONFLICT (type_code) DO UPDATE SET
    type_name = EXCLUDED.type_name,
    description = EXCLUDED.description,
    modified_at = NOW();

INSERT INTO common_code_details (
    type_code,
    code_value,
    code_name,
    sort_order,
    is_active,
    created_at,
    modified_at
) VALUES
('NOTIFICATION_TYPE', 'LIKE', '좋아요 알림', 10, 'Y', NOW(), NOW()),
('NOTIFICATION_TYPE', 'COMMENT', '댓글 알림', 20, 'Y', NOW(), NOW()),
('NOTIFICATION_TYPE', 'REPLY', '답글 알림', 30, 'Y', NOW(), NOW()),
('NOTIFICATION_TYPE', 'MENTION', '멘션 알림', 40, 'Y', NOW(), NOW()),
('NOTIFICATION_TYPE', 'MESSAGE', '쪽지 알림', 50, 'Y', NOW(), NOW()),
('NOTIFICATION_TYPE', 'SYSTEM', '시스템 알림', 60, 'Y', NOW(), NOW()),
('NOTIFICATION_TYPE', 'SANCTION', '제재 알림', 70, 'Y', NOW(), NOW()),
('NOTIFICATION_TYPE', 'KEYWORD', '키워드 알림', 80, 'Y', NOW(), NOW()),
('NOTIFICATION_TYPE', 'BADGE', '뱃지 알림', 90, 'Y', NOW(), NOW()),
('NOTIFICATION_TYPE', 'INQUIRY', '문의 알림', 100, 'Y', NOW(), NOW())
ON CONFLICT (type_code, code_value) DO UPDATE SET
    code_name = EXCLUDED.code_name,
    sort_order = EXCLUDED.sort_order,
    is_active = EXCLUDED.is_active,
    modified_at = NOW();

UPDATE common_code_details
SET is_active = 'N',
    modified_at = NOW()
WHERE type_code = 'NOTIFICATION_TYPE'
  AND code_value IN ('POST_COMMENT', 'REPLY_COMMENT', 'USER_MENTION', 'ADMIN_NOTICE');
