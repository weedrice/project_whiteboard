UPDATE common_code_details
SET code_name = CASE
        WHEN type_code = 'ADMIN_ROLE' AND code_value = 'BOARD_MANAGER' THEN '스페이스 관리자'
        WHEN type_code = 'ACTION_TYPE' AND code_value = 'FAVORITE_BOARD' THEN '스페이스 즐겨찾기'
        ELSE code_name
    END,
    updated_at = NOW()
WHERE (type_code = 'ADMIN_ROLE' AND code_value = 'BOARD_MANAGER')
   OR (type_code = 'ACTION_TYPE' AND code_value = 'FAVORITE_BOARD');
