-- Rename user-facing common code labels without modifying an already-applied seed migration.
UPDATE common_code_details
SET code_name = CASE
        WHEN type_code = 'ADMIN_ROLE' AND code_value = 'BOARD_MANAGER' THEN '노드 관리자'
        WHEN type_code = 'ACTION_TYPE' AND code_value = 'FAVORITE_BOARD' THEN '노드 즐겨찾기'
        ELSE code_name
    END,
    modified_at = NOW()
WHERE (type_code, code_value) IN (
    ('ADMIN_ROLE', 'BOARD_MANAGER'),
    ('ACTION_TYPE', 'FAVORITE_BOARD')
);
