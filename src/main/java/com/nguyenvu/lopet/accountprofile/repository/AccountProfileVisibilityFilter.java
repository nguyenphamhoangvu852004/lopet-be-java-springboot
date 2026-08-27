package com.nguyenvu.lopet.accountprofile.repository;

/**
 * Nguồn sự thật duy nhất cho câu hỏi "người này có được xem hồ sơ đó không?" — đối xứng với
 * {@link com.nguyenvu.lopet.post.repository.PostVisibility} của module bài viết.
 *
 * <p>Ba nhánh, mặc định là ẨN:
 * <pre>
 *   A. visibility = PUBLIC              -&gt; mọi người, kể cả khách chưa đăng nhập
 *   B. người xem là CHÍNH CHỦ           -&gt; mọi visibility
 *   C. visibility = FRIEND và hai bên đã ACCEPTED -&gt; chỉ bạn bè
 * </pre>
 *
 * <p><b>Vì sao nhánh giữa là FRIEND chứ không phải FOLLOWERS.</b> Bản dành cho thú cưng trước đây
 * phải để {@code FOLLOWERS} hành xử y hệt {@code PRIVATE} vì đồ thị follow chưa từng tồn tại, tức là
 * một giá trị enum mà không truy vấn nào trả lời được. Ở đây {@code friend_ships} đã có sẵn nên
 * nhánh C cài đặt được thật, và người dùng chọn "bạn bè" thì nhận đúng thứ họ chọn.
 *
 * <p>Nhánh C dùng {@code exists} thay vì nạp trước danh sách id bạn bè rồi {@code in (...)}: danh
 * sách bạn bè không có giới hạn trên, {@code in} với hàng nghìn phần tử sẽ phình query và không dùng
 * được index. Quan hệ bạn bè hai chiều nên phải so cả {@code sender} lẫn {@code receiver} —
 * {@code friend_ships} không có ràng buộc UNIQUE theo cặp, xem {@code DATABASE_MIGRATION_NOTES.md}.
 *
 * <p>Không cần thêm điều kiện {@code deletedAt is null}: {@code AccountProfile} và {@code Account}
 * đều mang {@code @SQLRestriction} nên Hibernate tự chèn vào mọi truy vấn có chúng làm gốc.
 */
public final class AccountProfileVisibilityFilter {

    /**
     * Truy vấn dùng mệnh đề này BẮT BUỘC phải khai {@code p} là alias của {@code AccountProfile} và
     * {@code left join p.account a}.
     *
     * <p>Không được viết thẳng {@code p.account.id}: path expression qua một quan hệ nullable sinh
     * ra INNER JOIN ngầm, và hồ sơ chưa gắn tài khoản nào sẽ biến mất khỏi kết quả trong im lặng.
     *
     * <p>Tham số {@code :viewerId} nhận {@code null} nghĩa là khách chưa đăng nhập — khi đó chỉ
     * nhánh A còn đúng.
     */
    public static final String VISIBLE_TO = """
            (
              p.visibility = com.nguyenvu.lopet.accountprofile.entity.ProfileVisibility.PUBLIC
              or (:viewerId is not null and (
                   a.id = :viewerId
                   or (p.visibility = com.nguyenvu.lopet.accountprofile.entity.ProfileVisibility.FRIEND
                       and exists (
                            select 1 from Friendship f
                            where f.status = com.nguyenvu.lopet.friendship.entity.FriendshipStatus.ACCEPTED
                              and ((f.sender.id = :viewerId and f.receiver.id = a.id)
                                or (f.receiver.id = :viewerId and f.sender.id = a.id))))
              ))
            )
            """;

    private AccountProfileVisibilityFilter() {
    }
}
