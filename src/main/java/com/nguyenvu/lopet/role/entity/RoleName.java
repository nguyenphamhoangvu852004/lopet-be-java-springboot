package com.nguyenvu.lopet.role.entity;

/**
 * Platform role — CHỈ dành cho nhân sự vận hành.
 *
 * <p>Không có USER: "user thường" = tài khoản đã xác thực, được cấp tập permission baseline và
 * KHÔNG có bản ghi role nào. Không có ADS: tư cách nhà quảng cáo là capability lưu ở
 * {@code advertiser_profiles}, vì role không mang được trạng thái duyệt.
 */
public enum RoleName {
    ADMIN,
    MODERATOR,
    SUPPORT
}
