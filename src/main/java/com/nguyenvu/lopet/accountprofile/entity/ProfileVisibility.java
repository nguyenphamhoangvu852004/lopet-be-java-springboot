package com.nguyenvu.lopet.accountprofile.entity;

/**
 * Phạm vi hiển thị của một hồ sơ tài khoản.
 *
 * <p>Ba giá trị trùng tên với {@code PostScope} là CỐ Ý: người dùng đã hiểu ba mức đó ở ô chọn khi
 * đăng bài, nên dùng lại đúng bộ từ cho hồ sơ thì không phải học lại. Hai thứ vẫn độc lập với nhau —
 * hồ sơ PRIVATE không làm bài PUBLIC biến mất, và ngược lại.
 *
 * <p>Nhánh giữa là {@code FRIEND} chứ không phải {@code FOLLOWERS} như bản dành cho thú cưng trước
 * đây: đồ thị follow chưa từng tồn tại nên {@code FOLLOWERS} buộc phải hành xử y hệt {@code PRIVATE},
 * còn {@code friend_ships} thì đã có sẵn nên nhánh này cài đặt được thật. Xem
 * {@link com.nguyenvu.lopet.accountprofile.repository.AccountProfileVisibilityFilter}.
 */
public enum ProfileVisibility {
    PUBLIC,
    FRIEND,
    PRIVATE
}
