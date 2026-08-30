


SELECT  (SELECT COUNT(*) FROM posts)         AS soBaiViet,
        (SELECT COUNT(*) FROM post_likes)    AS soLuotThich,
        (SELECT COUNT(*) FROM comments)      AS soBinhLuan,
        (SELECT COUNT(*) FROM group_members) AS soThanhVienNhom,
        (SELECT COUNT(*) FROM pets)          AS soThuCung;



ALTER TABLE pets DROP COLUMN visibility;
ALTER TABLE pets DROP COLUMN bio;



SET @fk := (SELECT CONSTRAINT_NAME FROM information_schema.KEY_COLUMN_USAGE
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'posts'
              AND COLUMN_NAME = 'account_id' AND REFERENCED_TABLE_NAME IS NOT NULL LIMIT 1);
SET @sql := IF(@fk IS NULL, 'SELECT ''posts: khong con FK account_id''',
                CONCAT('ALTER TABLE posts DROP FOREIGN KEY `', @fk, '`'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @fk := (SELECT CONSTRAINT_NAME FROM information_schema.KEY_COLUMN_USAGE
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'post_likes'
              AND COLUMN_NAME = 'account_id' AND REFERENCED_TABLE_NAME IS NOT NULL LIMIT 1);
SET @sql := IF(@fk IS NULL, 'SELECT ''post_likes: khong con FK account_id''',
                CONCAT('ALTER TABLE post_likes DROP FOREIGN KEY `', @fk, '`'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @fk := (SELECT CONSTRAINT_NAME FROM information_schema.KEY_COLUMN_USAGE
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'comments'
              AND COLUMN_NAME = 'account_id' AND REFERENCED_TABLE_NAME IS NOT NULL LIMIT 1);
SET @sql := IF(@fk IS NULL, 'SELECT ''comments: khong con FK account_id''',
                CONCAT('ALTER TABLE comments DROP FOREIGN KEY `', @fk, '`'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @fk := (SELECT CONSTRAINT_NAME FROM information_schema.KEY_COLUMN_USAGE
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'group_members'
              AND COLUMN_NAME = 'account_id' AND REFERENCED_TABLE_NAME IS NOT NULL LIMIT 1);
SET @sql := IF(@fk IS NULL, 'SELECT ''group_members: khong con FK account_id''',
                CONCAT('ALTER TABLE group_members DROP FOREIGN KEY `', @fk, '`'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;



ALTER TABLE group_members
    DROP PRIMARY KEY,
    ADD PRIMARY KEY (group_id, pet_id);



ALTER TABLE posts         DROP COLUMN account_id;
ALTER TABLE post_likes    DROP COLUMN account_id;
ALTER TABLE comments      DROP COLUMN account_id;
ALTER TABLE group_members DROP COLUMN account_id;



DROP TABLE IF EXISTS pet_ownerships;



SELECT  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
         AND TABLE_NAME = 'pets'          AND COLUMN_NAME = 'visibility')  AS petsVisibility,
        (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
         AND TABLE_NAME = 'pets'          AND COLUMN_NAME = 'bio')         AS petsBio,
        (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
         AND TABLE_NAME = 'posts'         AND COLUMN_NAME = 'account_id')  AS postsAccountId,
        (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
         AND TABLE_NAME = 'post_likes'    AND COLUMN_NAME = 'account_id')  AS likesAccountId,
        (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
         AND TABLE_NAME = 'comments'      AND COLUMN_NAME = 'account_id')  AS commentsAccountId,
        (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
         AND TABLE_NAME = 'group_members' AND COLUMN_NAME = 'account_id')  AS membersAccountId;

SELECT  COLUMN_NAME, ORDINAL_POSITION
FROM    information_schema.KEY_COLUMN_USAGE
WHERE   TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'group_members'
  AND   CONSTRAINT_NAME = 'PRIMARY'
ORDER BY ORDINAL_POSITION;
