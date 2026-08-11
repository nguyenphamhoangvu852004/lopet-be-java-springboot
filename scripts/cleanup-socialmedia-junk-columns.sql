-- Dọn 42 cột thừa trong database `socialmedia`.
--
-- Nguồn gốc: một lần chạy thử backend Java TRƯỚC khi cấu hình naming strategy. Spring Boot mặc
-- định đổi tên cột đã khai tường minh sang snake_case (`createdAt` -> `created_at`), nên
-- ddl-auto=update đã THÊM một bộ cột song song bên cạnh bộ cột camelCase mà TypeORM tạo ra.
-- Lỗi cấu hình đã được sửa (application.yml: physical-strategy=PhysicalNamingStrategyStandardImpl),
-- các cột này chỉ còn là rác.
--
-- ĐÃ KIỂM: cả 42 cột đều rỗng hoàn toàn (0 giá trị non-null) — không mất dữ liệu nào.
-- Dữ liệu thật nằm ở các cột camelCase và không bị đụng tới.
--
-- Chạy: docker exec -i mysql-docker mysql -uroot -pnguyenvu socialmedia < cleanup-socialmedia-junk-columns.sql

ALTER TABLE `account_role` DROP COLUMN `deleted_at`;
ALTER TABLE `account_role` DROP COLUMN `granted_at`;
ALTER TABLE `accounts` DROP COLUMN `deleted_at`;
ALTER TABLE `accounts` DROP COLUMN `is_banned`;
ALTER TABLE `accounts` DROP COLUMN `profile_id`;
ALTER TABLE `advertisements` DROP COLUMN `created_at`;
ALTER TABLE `advertisements` DROP COLUMN `deleted_at`;
ALTER TABLE `advertisements` DROP COLUMN `image_url`;
ALTER TABLE `advertisements` DROP COLUMN `link_reference`;
ALTER TABLE `advertisements` DROP COLUMN `updated_at`;
ALTER TABLE `advertiser_profiles` DROP COLUMN `company_name`;
ALTER TABLE `advertiser_profiles` DROP COLUMN `daily_limit`;
ALTER TABLE `advertiser_profiles` DROP COLUMN `deleted_at`;
ALTER TABLE `comments` DROP COLUMN `deleted_at`;
ALTER TABLE `comments` DROP COLUMN `parent_id`;
ALTER TABLE `friend_ships` DROP COLUMN `deleted_at`;
ALTER TABLE `group_members` DROP COLUMN `deleted_at`;
ALTER TABLE `group_members` DROP COLUMN `joined_at`;
ALTER TABLE `groups` DROP COLUMN `cover_url`;
ALTER TABLE `groups` DROP COLUMN `deleted_at`;
ALTER TABLE `messages` DROP COLUMN `deleted_at`;
ALTER TABLE `messages` DROP COLUMN `media_url`;
ALTER TABLE `notifications` DROP COLUMN `actor_id`;
ALTER TABLE `notifications` DROP COLUMN `deleted_at`;
ALTER TABLE `notifications` DROP COLUMN `receptor_id`;
ALTER TABLE `permissions` DROP COLUMN `deleted_at`;
ALTER TABLE `post_medias` DROP COLUMN `deleted_at`;
ALTER TABLE `post_medias` DROP COLUMN `media_type`;
ALTER TABLE `post_medias` DROP COLUMN `media_url`;
ALTER TABLE `posts` DROP COLUMN `deleted_at`;
ALTER TABLE `posts` DROP COLUMN `post_scope`;
ALTER TABLE `posts` DROP COLUMN `post_type`;
ALTER TABLE `profiles` DROP COLUMN `avatar_url`;
ALTER TABLE `profiles` DROP COLUMN `cover_url`;
ALTER TABLE `profiles` DROP COLUMN `date_of_birth`;
ALTER TABLE `profiles` DROP COLUMN `deleted_at`;
ALTER TABLE `profiles` DROP COLUMN `full_name`;
ALTER TABLE `profiles` DROP COLUMN `phone_number`;
ALTER TABLE `reports` DROP COLUMN `deleted_at`;
ALTER TABLE `reports` DROP COLUMN `target_id`;
ALTER TABLE `reports` DROP COLUMN `target_type`;
ALTER TABLE `roles` DROP COLUMN `deleted_at`;
