package com.nguyenvu.lopet.petprofile.repository;

/**
 * Nguồn sự thật duy nhất cho câu hỏi "người này có được xem hồ sơ thú cưng đó không?" — đối xứng
 * với {@link com.nguyenvu.lopet.post.repository.PostVisibility} của module bài viết.
 *
 * <p>Hai nhánh, mặc định là ẨN:
 * <pre>
 *   A. visibility = PUBLIC        -> mọi người, kể cả khách chưa đăng nhập
 *   B. người xem là CHỦ của pet   -> mọi visibility
 * </pre>
 *
 * <p><b>FOLLOWERS hiện hành xử y hệt PRIVATE</b> — chỉ chủ sở hữu xem được. Đồ thị follow giữa các
 * pet chưa tồn tại, nên không có cách nào trả lời "ai đang theo dõi thú cưng này". Chọn fail CLOSED:
 * coi FOLLOWERS là công khai cho tới khi có bảng follow sẽ làm rò rỉ đúng những hồ sơ mà người dùng
 * đã chủ động thu hẹp phạm vi. Khi bảng follow ra đời, thêm một nhánh {@code exists} vào ĐÂY là đủ,
 * không endpoint nào phải sửa.
 *
 * <p>Chủ sở hữu tra bằng {@code p.account.id} — Hibernate đọc thẳng cột khoá ngoại {@code account_id}
 * nên nhánh B không sinh thêm join nào.
 *
 * <p>Không cần thêm điều kiện {@code deletedAt is null}: cả {@code Pet} lẫn {@code PetProfile} đều
 * mang {@code @SQLRestriction} nên Hibernate tự chèn vào mọi truy vấn có chúng làm gốc.
 */
public final class PetProfileVisibilityFilter {

    /** Tham số {@code :viewerId} nhận {@code null} nghĩa là khách chưa đăng nhập */
    public static final String VISIBLE_TO = """
            (
              pp.visibility = com.nguyenvu.lopet.petprofile.entity.PetVisibility.PUBLIC
              or (:viewerId is not null and p.account.id = :viewerId)
            )
            """;

    private PetProfileVisibilityFilter() {
    }
}
