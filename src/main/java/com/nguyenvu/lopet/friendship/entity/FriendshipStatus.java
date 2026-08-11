package com.nguyenvu.lopet.friendship.entity;

/**
 * {@code BLOCKED} tồn tại trong schema nhưng KHÔNG endpoint nào của lopet-be tạo ra trạng thái này.
 * Giữ lại để không đổi định nghĩa cột.
 */
public enum FriendshipStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    BLOCKED
}
