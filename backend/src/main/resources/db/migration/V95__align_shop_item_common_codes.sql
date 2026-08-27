-- noviis:migration-phase expand

INSERT INTO common_codes (type_code, type_name, description, created_at, modified_at) VALUES
('ITEM_TYPE', '상점 아이템 유형', '상점 아이템 목록의 유형 필터', NOW(), NOW())
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
('ITEM_TYPE', 'EMOTICON', '이모티콘', 10, 'Y', NOW(), NOW())
ON CONFLICT (type_code, code_value) DO UPDATE SET
    code_name = EXCLUDED.code_name,
    sort_order = EXCLUDED.sort_order,
    is_active = EXCLUDED.is_active,
    modified_at = NOW();

UPDATE common_code_details
SET is_active = 'N',
    modified_at = NOW()
WHERE type_code = 'ITEM_TYPE'
  AND code_value IN ('ICON', 'TITLE_BADGE', 'NAME_COLOR', 'TICKET');
