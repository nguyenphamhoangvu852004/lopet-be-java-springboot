


SELECT  po.pet_id,
        COUNT(*)                                   AS soChuSoHuu,
        GROUP_CONCAT(po.user_id ORDER BY po.user_id) AS accountIds
FROM    pet_ownerships po
GROUP BY po.pet_id
HAVING  COUNT(*) > 1;

SELECT  COUNT(*) AS soThuCungMoCoi
FROM    pets p
WHERE   NOT EXISTS (SELECT 1 FROM pet_ownerships po WHERE po.pet_id = p.id);

SELECT  COUNT(*) AS soThieuPrimaryOwner
FROM    pets p
WHERE   EXISTS (SELECT 1 FROM pet_ownerships po WHERE po.pet_id = p.id)
  AND   NOT EXISTS (SELECT 1 FROM pet_ownerships po
                    WHERE po.pet_id = p.id AND po.ownership_type = 'PRIMARY_OWNER');



RENAME TABLE profiles TO account_profiles;



SELECT  CONSTRAINT_NAME
FROM    information_schema.KEY_COLUMN_USAGE
WHERE   TABLE_SCHEMA = DATABASE()
  AND   TABLE_NAME   = 'accounts'
  AND   COLUMN_NAME  = 'profileId'
  AND   REFERENCED_TABLE_NAME IS NOT NULL;


ALTER TABLE accounts CHANGE COLUMN profileId account_profile_id INT NULL;

ALTER TABLE accounts
    ADD CONSTRAINT uq_accounts_account_profile_id UNIQUE (account_profile_id),
    ADD CONSTRAINT fk_accounts_account_profile
        FOREIGN KEY (account_profile_id) REFERENCES account_profiles (id) ON DELETE CASCADE;



ALTER TABLE pets ADD COLUMN account_id INT NULL;

UPDATE  pets p
JOIN   (SELECT  po.pet_id,
                COALESCE(
                    MIN(CASE WHEN po.ownership_type = 'PRIMARY_OWNER' THEN po.user_id END),
                    MIN(po.user_id)
                ) AS ownerId
        FROM    pet_ownerships po
        GROUP BY po.pet_id) chu
  ON    chu.pet_id = p.id
SET     p.account_id = chu.ownerId;



DELETE FROM pets WHERE account_id IS NULL;

ALTER TABLE pets MODIFY COLUMN account_id INT NOT NULL;

ALTER TABLE pets
    ADD CONSTRAINT fk_pets_account
        FOREIGN KEY (account_id) REFERENCES accounts (id) ON DELETE CASCADE;

CREATE INDEX idx_pets_account_id ON pets (account_id);

DROP TABLE pet_ownerships;



CREATE TABLE pet_profiles (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    pet_id        INT          NOT NULL,
    handle        VARCHAR(30)  NOT NULL,
    display_name  VARCHAR(50)  NOT NULL,
    avatar_url    TEXT         NULL,
    cover_url     TEXT         NULL,
    bio           VARCHAR(500) NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    visibility    VARCHAR(20)  NOT NULL DEFAULT 'PUBLIC',
    createdAt     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updatedAt     DATETIME(6)  NULL,
    deletedAt     DATETIME(6)  NULL,
    CONSTRAINT uq_pet_profiles_pet_id UNIQUE (pet_id),
    CONSTRAINT uq_pet_profiles_handle UNIQUE (handle),
    CONSTRAINT fk_pet_profiles_pet FOREIGN KEY (pet_id) REFERENCES pets (id) ON DELETE CASCADE
);



INSERT INTO pet_profiles (pet_id, handle, display_name, avatar_url, cover_url, bio,
                          status, visibility, createdAt, updatedAt, deletedAt)
SELECT  p.id,
        CONCAT('pet_', p.id),
        p.name,
        '',
        '',
        COALESCE(p.bio, ''),
        CASE WHEN p.deletedAt IS NULL THEN 'ACTIVE' ELSE 'DEACTIVATED' END,
        COALESCE(p.visibility, 'PUBLIC'),
        NOW(6),
        NOW(6),
        p.deletedAt
FROM    pets p;



UPDATE pets SET status = 'DEACTIVATED' WHERE status = 'ARCHIVED';

ALTER TABLE pets DROP COLUMN bio;
ALTER TABLE pets DROP COLUMN visibility;



SELECT  (SELECT COUNT(*) FROM pets WHERE account_id IS NULL)                       AS petThieuChu,
        (SELECT COUNT(*) FROM pets p
         WHERE NOT EXISTS (SELECT 1 FROM pet_profiles pp WHERE pp.pet_id = p.id))  AS petThieuHoSo,
        (SELECT COUNT(*) FROM pet_profiles pp
         WHERE NOT EXISTS (SELECT 1 FROM pets p WHERE p.id = pp.pet_id))           AS hoSoMoCoi,
        (SELECT COUNT(*) FROM pets WHERE status = 'ARCHIVED')                      AS conTrangThaiCu,
        (SELECT COUNT(*) FROM information_schema.TABLES
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pet_ownerships')        AS conBangNoi;
