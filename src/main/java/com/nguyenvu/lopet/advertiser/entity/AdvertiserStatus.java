package com.nguyenvu.lopet.advertiser.entity;

/**
 * Vòng đời tư cách nhà quảng cáo: đăng ký → PENDING → (staff duyệt) → APPROVED → (vi phạm) →
 * SUSPENDED. Chỉ APPROVED mới tạo được quảng cáo — một enum role không mang được chuỗi trạng thái
 * này, đó là lý do nó là bảng riêng chứ không phải role.
 */
public enum AdvertiserStatus {
    PENDING,
    APPROVED,
    SUSPENDED
}
