-- noviis:migration-phase expand

INSERT INTO common_codes (type_code, type_name, description, created_at, modified_at) VALUES
('VERSION_TYPE', '게시글 버전 유형', '게시글 버전 이력에 저장되는 변경 유형', NOW(), NOW()),
('ADMIN_ROLE', '관리자 역할 코드', '관리자 배정 API에서 선택 가능한 역할', NOW(), NOW()),
('RANKING_TYPE', '랭킹 유형', '인기 게시글 집계가 생성하는 랭킹 기간', NOW(), NOW()),
('ACTION_TYPE', '활동 기록 유형', '레거시 사용자 활동 코드이며 현재 런타임 로그와 연결되지 않음', NOW(), NOW())
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
('VERSION_TYPE', 'CREATE', '최초 작성', 10, 'Y', NOW(), NOW()),
('VERSION_TYPE', 'MODIFY', '수정', 20, 'Y', NOW(), NOW()),
('VERSION_TYPE', 'DELETE', '삭제', 30, 'Y', NOW(), NOW()),
('ADMIN_ROLE', 'BOARD_ADMIN', '스페이스 관리자', 10, 'Y', NOW(), NOW()),
('ADMIN_ROLE', 'MODERATOR', '운영자', 20, 'Y', NOW(), NOW()),
('RANKING_TYPE', 'DAILY', '일간 랭킹', 10, 'Y', NOW(), NOW()),
('RANKING_TYPE', 'WEEKLY', '주간 랭킹', 20, 'Y', NOW(), NOW())
ON CONFLICT (type_code, code_value) DO UPDATE SET
    code_name = EXCLUDED.code_name,
    sort_order = EXCLUDED.sort_order,
    is_active = EXCLUDED.is_active,
    modified_at = NOW();

UPDATE common_code_details
SET is_active = 'N',
    modified_at = NOW()
WHERE (type_code = 'VERSION_TYPE' AND code_value = 'UPDATE')
   OR (type_code = 'ADMIN_ROLE'
       AND code_value IN ('GLOBAL_ADMIN', 'BOARD_MANAGER', 'SANCTION_STAFF'))
   OR (type_code = 'RANKING_TYPE'
       AND code_value IN ('MONTHLY', 'REALTIME'))
   OR (type_code = 'ACTION_TYPE'
       AND code_value IN (
           'LOGIN',
           'POST_CREATE',
           'POST_DELETE',
           'COMMENT_WRITE',
           'VIEW_POST',
           'LOGOUT',
           'POST_UPDATE',
           'COMMENT_UPDATE',
           'POST_LIKE',
           'COMMENT_LIKE',
           'SCRAP',
           'FAVORITE_BOARD',
           'POINT_EARN',
           'ITEM_PURCHASE',
           'FILE_UPLOAD',
           'USER_BLOCK',
           'PROFILE_UPDATE'
       ));
