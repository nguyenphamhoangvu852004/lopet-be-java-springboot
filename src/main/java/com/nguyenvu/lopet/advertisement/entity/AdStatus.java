package com.nguyenvu.lopet.advertisement.entity;

/**
 * Cột {@code advertisements.status} tồn tại và có default DRAFT, nhưng KHÔNG endpoint nào của
 * lopet-be đọc hay ghi nó (quyền {@code ads:review} cũng được seed mà chưa route nào dùng).
 * Giữ nguyên để không đổi schema.
 */
public enum AdStatus {
    DRAFT,
    REVIEW,
    ACTIVE,
    REJECTED
}
