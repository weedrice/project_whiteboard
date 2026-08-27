-- noviis:migration-phase expand

INSERT INTO common_codes (type_code, type_name, description, created_at, modified_at) VALUES
('REPORT_REASON', '신고 사유 코드', '게시글, 댓글 등에 대한 신고 이유', NOW(), NOW()),
('POINT_CHANGE_TYPE', '포인트 변동 유형', '포인트 획득/사용/만료/제재 구분', NOW(), NOW())
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
('REPORT_REASON', 'SPAM', '광고/도배', 10, 'Y', NOW(), NOW()),
('REPORT_REASON', 'ABUSE', '욕설 및 괴롭힘', 20, 'Y', NOW(), NOW()),
('REPORT_REASON', 'ADULT', '성인 콘텐츠', 30, 'Y', NOW(), NOW()),
('REPORT_REASON', 'ETC', '기타', 40, 'Y', NOW(), NOW()),
('POINT_CHANGE_TYPE', 'EARN', '포인트 획득', 10, 'Y', NOW(), NOW()),
('POINT_CHANGE_TYPE', 'SPEND', '포인트 사용', 20, 'Y', NOW(), NOW()),
('POINT_CHANGE_TYPE', 'EXPIRE', '포인트 만료', 30, 'Y', NOW(), NOW()),
('POINT_CHANGE_TYPE', 'PENALTY', '포인트 제재 차감', 40, 'Y', NOW(), NOW()),
('POINT_CHANGE_TYPE', 'REWARD_REVERSAL', '포인트 보상 회수', 50, 'Y', NOW(), NOW())
ON CONFLICT (type_code, code_value) DO UPDATE SET
    code_name = EXCLUDED.code_name,
    sort_order = EXCLUDED.sort_order,
    is_active = EXCLUDED.is_active,
    modified_at = NOW();

UPDATE common_code_details
SET is_active = 'N',
    modified_at = NOW()
WHERE (type_code = 'REPORT_REASON'
       AND code_value IN ('HATE_SPEECH', 'PORNOGRAPHY', 'ILLEGAL_ADS', 'PERSONAL_INFO'))
   OR (type_code = 'POINT_CHANGE_TYPE'
       AND code_value = 'ADMIN_ADJ');
