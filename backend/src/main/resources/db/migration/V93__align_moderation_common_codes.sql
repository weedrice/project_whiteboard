-- noviis:migration-phase expand

INSERT INTO common_codes (type_code, type_name, description, created_at, modified_at) VALUES
('SANCTION_TYPE', '제재 유형 코드', '신규 제재 등록에 사용할 수 있는 제재 유형', NOW(), NOW()),
('REPORT_STATUS', '신고 처리 상태', '신고 목록의 처리 상태', NOW(), NOW()),
('TARGET_TYPE', '신고 대상 타입', '신고 대상 객체 타입', NOW(), NOW())
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
('SANCTION_TYPE', 'WARNING', '경고', 10, 'Y', NOW(), NOW()),
('SANCTION_TYPE', 'MUTE', '활동 제한', 20, 'Y', NOW(), NOW()),
('SANCTION_TYPE', 'BAN', '이용 정지', 30, 'Y', NOW(), NOW()),
('REPORT_STATUS', 'PENDING', '대기', 10, 'Y', NOW(), NOW()),
('REPORT_STATUS', 'RESOLVED', '처리 완료', 20, 'Y', NOW(), NOW()),
('REPORT_STATUS', 'REJECTED', '반려', 30, 'Y', NOW(), NOW()),
('TARGET_TYPE', 'POST', '게시글', 10, 'Y', NOW(), NOW()),
('TARGET_TYPE', 'COMMENT', '댓글', 20, 'Y', NOW(), NOW()),
('TARGET_TYPE', 'USER', '사용자', 30, 'Y', NOW(), NOW())
ON CONFLICT (type_code, code_value) DO UPDATE SET
    code_name = EXCLUDED.code_name,
    sort_order = EXCLUDED.sort_order,
    is_active = EXCLUDED.is_active,
    modified_at = NOW();

UPDATE common_code_details
SET is_active = 'N',
    modified_at = NOW()
WHERE (type_code = 'SANCTION_TYPE'
       AND code_value IN ('TEMP_BAN', 'PERM_BAN', 'POST_DELETE', 'COMMENT_HIDE'))
   OR (type_code = 'REPORT_STATUS'
       AND code_value = 'PROCESSING');
