-- noviis:migration-phase expand

ALTER TABLE boards
    ADD COLUMN is_listed varchar(1) DEFAULT 'Y';

UPDATE boards
SET is_listed = 'Y'
WHERE is_listed IS NULL;

UPDATE boards
SET is_listed = 'N'
WHERE is_public = 'N';

ALTER TABLE boards
    ALTER COLUMN is_listed SET NOT NULL;

ALTER TABLE boards
    ADD CONSTRAINT ck_boards_is_listed CHECK (is_listed IN ('Y', 'N'));
