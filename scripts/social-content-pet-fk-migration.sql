


SELECT  a.id                AS accountId,
        a.username,
        (SELECT COUNT(*) FROM posts         t WHERE t.account_id = a.id) AS soBaiViet,
        (SELECT COUNT(*) FROM post_likes    t WHERE t.account_id = a.id) AS soLuotThich,
        (SELECT COUNT(*) FROM comments      t WHERE t.account_id = a.id) AS soBinhLuan,
        (SELECT COUNT(*) FROM group_members t WHERE t.account_id = a.id) AS soThamGiaNhom
FROM    accounts a
WHERE   NOT EXISTS (SELECT 1 FROM pets p WHERE p.account_id = a.id)
  AND   ( EXISTS (SELECT 1 FROM posts         t WHERE t.account_id = a.id)
       OR EXISTS (SELECT 1 FROM post_likes    t WHERE t.account_id = a.id)
       OR EXISTS (SELECT 1 FROM comments      t WHERE t.account_id = a.id)
       OR EXISTS (SELECT 1 FROM group_members t WHERE t.account_id = a.id) );

SELECT  p.account_id                              AS accountId,
        COUNT(*)                                  AS soThuCung,
        MIN(p.id)                                 AS petSeNhanNoiDungCu,
        GROUP_CONCAT(p.id ORDER BY p.id)          AS tatCaPetIds
FROM    pets p
WHERE   p.deletedAt IS NULL
GROUP BY p.account_id
HAVING  COUNT(*) > 1;

SELECT  (SELECT COUNT(*) FROM posts         t WHERE t.account_id IS NOT NULL
         AND NOT EXISTS (SELECT 1 FROM accounts a WHERE a.id = t.account_id)) AS baiVietMoCoi,
        (SELECT COUNT(*) FROM post_likes    t WHERE t.account_id IS NOT NULL
         AND NOT EXISTS (SELECT 1 FROM accounts a WHERE a.id = t.account_id)) AS luotThichMoCoi,
        (SELECT COUNT(*) FROM comments      t WHERE t.account_id IS NOT NULL
         AND NOT EXISTS (SELECT 1 FROM accounts a WHERE a.id = t.account_id)) AS binhLuanMoCoi,
        (SELECT COUNT(*) FROM group_members t
         WHERE NOT EXISTS (SELECT 1 FROM accounts a WHERE a.id = t.account_id)) AS thanhVienMoCoi;



ALTER TABLE posts         ADD COLUMN pet_id int NULL AFTER id;
ALTER TABLE post_likes    ADD COLUMN pet_id int NULL AFTER post_id;
ALTER TABLE comments      ADD COLUMN pet_id int NULL AFTER post_id;
ALTER TABLE group_members ADD COLUMN pet_id int NULL AFTER group_id;



UPDATE posts t
SET    t.pet_id = COALESCE(
           (SELECT MIN(p.id) FROM pets p WHERE p.account_id = t.account_id AND p.deletedAt IS NULL),
           (SELECT MIN(p.id) FROM pets p WHERE p.account_id = t.account_id))
WHERE  t.account_id IS NOT NULL;

UPDATE post_likes t
SET    t.pet_id = COALESCE(
           (SELECT MIN(p.id) FROM pets p WHERE p.account_id = t.account_id AND p.deletedAt IS NULL),
           (SELECT MIN(p.id) FROM pets p WHERE p.account_id = t.account_id))
WHERE  t.account_id IS NOT NULL;

UPDATE comments t
SET    t.pet_id = COALESCE(
           (SELECT MIN(p.id) FROM pets p WHERE p.account_id = t.account_id AND p.deletedAt IS NULL),
           (SELECT MIN(p.id) FROM pets p WHERE p.account_id = t.account_id))
WHERE  t.account_id IS NOT NULL;

UPDATE group_members t
SET    t.pet_id = COALESCE(
           (SELECT MIN(p.id) FROM pets p WHERE p.account_id = t.account_id AND p.deletedAt IS NULL),
           (SELECT MIN(p.id) FROM pets p WHERE p.account_id = t.account_id))
WHERE  t.account_id IS NOT NULL;



DELETE FROM comments      WHERE pet_id IS NULL;
DELETE FROM post_likes    WHERE pet_id IS NULL;
DELETE FROM group_members WHERE pet_id IS NULL;



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



ALTER TABLE comments      MODIFY COLUMN pet_id int NOT NULL;
ALTER TABLE group_members MODIFY COLUMN pet_id int NOT NULL;

ALTER TABLE posts
    ADD CONSTRAINT fk_posts_pet         FOREIGN KEY (pet_id) REFERENCES pets (id) ON DELETE CASCADE;
ALTER TABLE post_likes
    ADD CONSTRAINT fk_post_likes_pet    FOREIGN KEY (pet_id) REFERENCES pets (id) ON DELETE CASCADE;
ALTER TABLE comments
    ADD CONSTRAINT fk_comments_pet      FOREIGN KEY (pet_id) REFERENCES pets (id) ON DELETE CASCADE;
ALTER TABLE group_members
    ADD CONSTRAINT fk_group_members_pet FOREIGN KEY (pet_id) REFERENCES pets (id) ON DELETE CASCADE;



SELECT  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
         AND TABLE_NAME = 'posts'         AND COLUMN_NAME = 'account_id')  AS conCotCu_posts,
        (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
         AND TABLE_NAME = 'post_likes'    AND COLUMN_NAME = 'account_id')  AS conCotCu_postLikes,
        (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
         AND TABLE_NAME = 'comments'      AND COLUMN_NAME = 'account_id')  AS conCotCu_comments,
        (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
         AND TABLE_NAME = 'group_members' AND COLUMN_NAME = 'account_id')  AS conCotCu_groupMembers,
        (SELECT COUNT(*) FROM comments      WHERE pet_id IS NULL)          AS binhLuanThieuPet,
        (SELECT COUNT(*) FROM group_members WHERE pet_id IS NULL)          AS thanhVienThieuPet,
        (SELECT COUNT(*) FROM post_likes    WHERE pet_id IS NULL)          AS luotThichThieuPet,
        (SELECT COUNT(*) FROM posts         WHERE pet_id IS NULL)          AS baiVietKhongCoTacGia;

SELECT  COLUMN_NAME, ORDINAL_POSITION
FROM    information_schema.KEY_COLUMN_USAGE
WHERE   TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'group_members'
  AND   CONSTRAINT_NAME = 'PRIMARY'
ORDER BY ORDINAL_POSITION;
