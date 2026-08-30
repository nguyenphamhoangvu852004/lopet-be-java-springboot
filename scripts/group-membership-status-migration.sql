


SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'group_members' AND column_name = 'status') = 0,
    'ALTER TABLE `group_members`
        ADD COLUMN `status` enum(''PENDING'',''ACTIVE'') NOT NULL DEFAULT ''ACTIVE'' AFTER `role`',
    'SELECT ''Bước 1: cột status đã tồn tại, bỏ qua''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;



SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'group_members' AND column_name = 'invited_by') = 0,
    'ALTER TABLE `group_members` ADD COLUMN `invited_by` int NULL AFTER `status`',
    'SELECT ''Bước 2: cột invited_by đã tồn tại, bỏ qua''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;



ALTER TABLE `notifications`
    MODIFY COLUMN `objectType` enum('POST_LIKE','POST_COMMENT','MESSAGE','FRIEND_REQUEST',
                                    'FRIEND_ACCEPTED','POST',
                                    'GROUP_JOIN_REQUESTED','GROUP_JOIN_APPROVED',
                                    'GROUP_INVITED','GROUP_INVITE_ACCEPTED') NOT NULL;



SELECT  status, COUNT(*) AS rows_count
FROM    `group_members`
GROUP BY status;

SELECT  COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT
FROM    information_schema.columns
WHERE   table_schema = DATABASE()
  AND   table_name = 'group_members'
  AND   COLUMN_NAME IN ('status', 'invited_by');
