


SELECT  a.profileId,
        COUNT(*)                      AS soTaiKhoanTro,
        GROUP_CONCAT(a.id ORDER BY a.id)       AS accountIds,
        GROUP_CONCAT(a.username ORDER BY a.id) AS usernames
FROM    accounts a
WHERE   a.profileId IS NOT NULL
GROUP BY a.profileId
HAVING  COUNT(*) > 1;

SELECT  COUNT(*) AS soHoSoMoCoi
FROM    profiles p
WHERE   NOT EXISTS (SELECT 1 FROM accounts a WHERE a.profileId = p.id);

SELECT  COUNT(*) AS soTaiKhoanChuaCoHoSo
FROM    accounts
WHERE   profileId IS NULL;



CREATE TEMPORARY TABLE tmp_ke_chiem_hoso AS
SELECT  a.id AS accountId, a.username
FROM    accounts a
JOIN   (SELECT profileId, MIN(id) AS chuThatSu
        FROM   accounts
        WHERE  profileId IS NOT NULL
        GROUP BY profileId
        HAVING COUNT(*) > 1) d
  ON    a.profileId = d.profileId
 AND    a.id <> d.chuThatSu;

INSERT INTO profiles (fullName, phoneNumber, bio, sex, dateOfBirth, hometown, avatarUrl, coverUrl,
                      createdAt, updatedAt, deletedAt)
SELECT  t.username, '', CONCAT('Xin chào, mình là ', t.username), NULL, NULL, '', '', '',
        NOW(), NOW(), NULL
FROM    tmp_ke_chiem_hoso t;

UPDATE  accounts a
JOIN    tmp_ke_chiem_hoso t ON t.accountId = a.id
JOIN   (SELECT p.id AS profileId, p.fullName
        FROM   profiles p
        WHERE  NOT EXISTS (SELECT 1 FROM accounts x WHERE x.profileId = p.id)) moi
  ON    moi.fullName = t.username
SET     a.profileId = moi.profileId;

DROP TEMPORARY TABLE tmp_ke_chiem_hoso;



DELETE  p
FROM    profiles p
WHERE   NOT EXISTS (SELECT 1 FROM accounts a WHERE a.profileId = p.id);



INSERT INTO profiles (fullName, phoneNumber, bio, sex, dateOfBirth, hometown, avatarUrl, coverUrl,
                      createdAt, updatedAt, deletedAt)
SELECT  a.username, '', CONCAT('Xin chào, mình là ', a.username), NULL, NULL, '', '', '',
        NOW(), NOW(), NULL
FROM    accounts a
WHERE   a.profileId IS NULL;

UPDATE  accounts a
JOIN   (SELECT p.id AS profileId, p.fullName
        FROM   profiles p
        WHERE  NOT EXISTS (SELECT 1 FROM accounts x WHERE x.profileId = p.id)) moi
  ON    moi.fullName = a.username
SET     a.profileId = moi.profileId
WHERE   a.profileId IS NULL;



ALTER TABLE accounts ADD CONSTRAINT uq_accounts_profileId UNIQUE (profileId);



SELECT  (SELECT COUNT(*) FROM (SELECT profileId FROM accounts WHERE profileId IS NOT NULL
                               GROUP BY profileId HAVING COUNT(*) > 1) x) AS conTrung,
        (SELECT COUNT(*) FROM profiles p
         WHERE NOT EXISTS (SELECT 1 FROM accounts a WHERE a.profileId = p.id))  AS conMoCoi,
        (SELECT COUNT(*) FROM accounts WHERE profileId IS NULL)                 AS conThieuHoSo;
