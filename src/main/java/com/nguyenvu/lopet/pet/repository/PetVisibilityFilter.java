package com.nguyenvu.lopet.pet.repository;

/**
 * Nguồn sự thật duy nhất cho câu hỏi "người này có được xem hồ sơ thú cưng đó không?" — đối xứng
 * với {@link com.nguyenvu.lopet.post.repository.PostVisibility} của module bài viết.
 *
 * <p>Ba nhánh, mặc định là ẨN:
 * <pre>
 *   A. visibility = PUBLIC                    -> mọi người, kể cả khách
 *   --- khách chưa đăng nhập dừng ở đây ---
 *   B. người xem có mặt trong pet_ownerships  -> mọi visibility, mọi ownership_type
 * </pre>
 *
 * <p><b>FOLLOWERS hiện hành xử y hệt PRIVATE</b> — chỉ chủ sở hữu xem được. Đồ thị follow chưa tồn
 * tại (Pet Follow nằm ngoài phạm vi), nên không có cách nào trả lời "ai đang theo dõi thú cưng
 * này". Chọn fail CLOSED: coi FOLLOWERS là công khai cho tới khi có bảng follow sẽ làm rò rỉ đúng
 * những hồ sơ mà người dùng đã chủ động thu hẹp phạm vi. Khi bảng follow ra đời, thêm một nhánh
 * {@code exists} vào ĐÂY là đủ, không endpoint nào phải sửa.
 *
 * <p>Chủ sở hữu được xác định qua {@code exists} trên {@code PetOwnership} chứ không phải cột trên
 * {@code pets}: quan hệ sở hữu là n-n (một thú cưng có 1 PRIMARY_OWNER và nhiều CO_OWNER), và
 * {@code exists} không nhân bản hàng như join.
 *
 * <p>Không cần thêm điều kiện {@code deletedAt is null}: entity {@code Pet} mang
 * {@code @SQLRestriction} nên Hibernate tự chèn vào mọi truy vấn có Pet làm gốc.
 */
public final class PetVisibilityFilter {

    /** Tham số {@code :viewerId} nhận {@code null} nghĩa là khách chưa đăng nhập */
    public static final String VISIBLE_TO = """
            (
              p.visibility = com.nguyenvu.lopet.pet.entity.PetVisibility.PUBLIC
              or (:viewerId is not null and exists (
                    select 1 from PetOwnership po
                    where po.petId = p.id and po.userId = :viewerId))
            )
            """;

    private PetVisibilityFilter() {
    }
}
