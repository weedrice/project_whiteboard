-- noviis:migration-phase expand

-- 롤백 호환 기간의 구버전 writer는 이 컬럼을 생략하므로 NULL을 허용한다.
-- 새 애플리케이션은 공개 NULL을 기존 공개 상태(목록 노출)로 해석한다.
ALTER TABLE boards
    ADD COLUMN is_listed varchar(1);

UPDATE boards
SET is_listed = CASE WHEN is_public = 'N' THEN 'N' ELSE 'Y' END
WHERE is_listed IS NULL;

ALTER TABLE boards
    ADD CONSTRAINT ck_boards_is_listed CHECK (is_listed IN ('Y', 'N'));
