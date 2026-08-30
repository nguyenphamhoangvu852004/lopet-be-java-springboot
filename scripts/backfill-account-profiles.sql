


SELECT  (SELECT COUNT(*) FROM accounts)                                AS soTaiKhoan,
        (SELECT COUNT(*) FROM profiles)                                AS soHoSoCu,
        (SELECT COUNT(*) FROM account_profiles)                        AS soHoSoMoi,
        (SELECT COUNT(*) FROM accounts WHERE profileId IS NOT NULL)    AS coTroToiHoSoCu,
        (SELECT COUNT(*) FROM accounts WHERE account_profile_id IS NOT NULL) AS coTroToiHoSoMoi;

SELECT  a.id, a.username
FROM    accounts a
WHERE   a.profileId IS NULL AND a.account_profile_id IS NULL;



INSERT INTO account_profiles
        (id, createdAt, updatedAt, deletedAt, fullName, phoneNumber, bio, sex, dateOfBirth,
         hometown, avatarUrl, coverUrl)
SELECT   id, createdAt, updatedAt, deletedAt, fullName, phoneNumber, bio, sex, dateOfBirth,
         hometown, avatarUrl, coverUrl
FROM     profiles;



UPDATE  accounts
SET     account_profile_id = profileId
WHERE   profileId IS NOT NULL AND account_profile_id IS NULL;



SET @fk := (SELECT CONSTRAINT_NAME FROM information_schema.KEY_COLUMN_USAGE
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'accounts'
              AND COLUMN_NAME = 'profileId' AND REFERENCED_TABLE_NAME IS NOT NULL LIMIT 1);
SET @sql := IF(@fk IS NULL, 'SELECT ''accounts: khong con FK profileId''',
                CONCAT('ALTER TABLE accounts DROP FOREIGN KEY `', @fk, '`'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

ALTER TABLE accounts DROP COLUMN profileId;
DROP TABLE IF EXISTS profiles;



SELECT  a.id AS accountIdConThieuHoSo, a.username
FROM    accounts a
WHERE   a.account_profile_id IS NULL;



SELECT  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
         AND TABLE_NAME = 'accounts' AND COLUMN_NAME = 'profileId')     AS conCotProfileIdCu,
        (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE()
         AND TABLE_NAME = 'profiles')                                   AS conBangProfilesCu,
        (SELECT COUNT(*) FROM accounts WHERE account_profile_id IS NULL) AS taiKhoanThieuHoSo,
        (SELECT COUNT(*) FROM accounts)                                 AS soTaiKhoan,
        (SELECT COUNT(*) FROM account_profiles)                         AS soHoSoMoi;
