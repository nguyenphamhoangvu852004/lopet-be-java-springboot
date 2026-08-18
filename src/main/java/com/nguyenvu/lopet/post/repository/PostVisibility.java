package com.nguyenvu.lopet.post.repository;

/**
 * Nguồn sự thật duy nhất cho câu hỏi "người này có được xem bài viết đó không?".
 *
 * <p>Trước bản vá bên TS, cột {@code posts.postScope} được ghi ở mọi luồng tạo/sửa bài nhưng KHÔNG
 * một query nào đọc tới, đồng thời các route đọc bài lại không xác thực — nghĩa là mọi bài PRIVATE
 * và FRIEND của mọi tài khoản đều đọc được bởi khách vãng lai chỉ bằng {@code GET /v1/posts}.
 *
 * <p>Sáu nhánh, mặc định là ẨN:
 * <pre>
 *   A. Bài cá nhân, scope PUBLIC                      -> mọi người, kể cả khách
 *   B. Bài nhóm scope PUBLIC trong nhóm PUBLIC        -> mọi người, kể cả khách
 *   --- khách chưa đăng nhập dừng ở đây ---
 *   C. Bài do CHÍNH PET đang xem đăng                 -> mọi scope
 *   D. Bài trong nhóm mà PET đang xem là thành viên   -> mọi scope (chỉ ACTIVE, xem dưới)
 *   E. Bài của một pet khác CÙNG CHỦ với người xem    -> mọi scope
 *   F. Bài cá nhân scope FRIEND của bạn bè ACCEPTED   -> chỉ bạn bè
 * </pre>
 *
 * <p><b>Hai loại danh tính, không được lẫn.</b> Sau khi {@code posts.account_id} đổi thành
 * {@code posts.pet_id}, tác giả và thành viên nhóm là THÚ CƯNG, nên nhánh C và D so theo
 * {@code :viewerPetId} — pet đang thao tác, lấy từ {@code PetContext}. Còn đồ thị bạn bè vẫn nối
 * giữa các TÀI KHOẢN, nên nhánh F so theo {@code :viewerId} và quy bài về chủ của pet tác giả
 * ({@code ap.account.id}). Ép nhánh F chạy theo pet sẽ phải bịa ra một quan hệ bạn bè giữa pet mà
 * bảng {@code friend_ships} không hề có.
 *
 * <p><b>Nhánh E không thừa.</b> Một tài khoản có nhiều thú cưng và đổi qua lại liên tục; nếu chỉ có
 * nhánh C thì vừa đổi sang pet khác là bài PRIVATE của chính mình biến mất — kéo theo cả luồng sửa
 * và xoá bài, vì {@code PostAccessGuard} nạp bài bằng đúng bộ lọc này. Quyền sở hữu để sửa/xoá vẫn
 * được xét ở mức tài khoản, nên hai tầng phải nhất quán.
 *
 * <p>Cột mốc phân biệt bài nhóm / bài cá nhân là {@code group_id}, KHÔNG phải {@code postType}:
 * postType nullable và chỉ được gán qua {@code applyType()}, nên dữ liệu cũ có thể còn NULL.
 *
 * <p>Nhánh B đòi thêm {@code group.type = PUBLIC}: tính riêng tư của nhóm là ràng buộc mạnh hơn
 * scope của từng bài, nên bài PUBLIC trong nhóm PRIVATE vẫn không lọt ra ngoài. Ràng buộc đó nằm ở
 * tầng query nên không endpoint nào đi vòng qua được.
 *
 * <p><b>Nhánh D đòi {@code gm.status = ACTIVE}</b>, không chỉ "có hàng trong group_members". Từ khi
 * nhóm có luồng xin vào và mời, một hàng PENDING nằm sẵn ở đó cho pet vừa bấm xin vào nhóm PRIVATE
 * hoặc vừa được mời — cả hai đều là hành động của người NGOÀI nhóm. Bỏ điều kiện này thì chỉ cần gửi
 * yêu cầu là đọc được toàn bộ bài trong nhóm trước khi ai kịp duyệt, tức là PRIVATE không còn nghĩa
 * gì. Đây là chỗ duy nhất tính riêng tư của nhóm được thực thi ở đường đọc, nên điều kiện phải nằm
 * đúng trong hằng số này.
 *
 * <p>Nhánh F dùng {@code exists} thay vì nạp trước danh sách id bạn bè rồi {@code in (...)}: danh
 * sách bạn bè không có giới hạn trên, {@code in} với hàng nghìn phần tử sẽ phình query và không
 * dùng được index.
 */
public final class PostVisibility {

    /**
     * Truy vấn dùng mệnh đề này BẮT BUỘC phải khai {@code left join p.group g} và
     * {@code left join p.pet ap}.
     *
     * <p>Không được viết thẳng {@code p.group.type} hay {@code p.pet.account.id}: path expression
     * qua một quan hệ nullable sinh ra INNER JOIN ngầm, và inner join đó sẽ loại sạch bài cá nhân
     * (group_id NULL) hoặc bài chưa di trú xong (pet_id NULL) khỏi kết quả — tức là nhánh A biến
     * mất trong im lặng. Alias của LEFT JOIN thì không có tác dụng phụ đó.
     *
     * <p>{@code ap.id}, {@code ap.account.id} và {@code p.group.id} thì an toàn: Hibernate đọc thẳng
     * cột khoá ngoại trên bảng đã join chứ không join thêm.
     *
     * <p>Hai tham số, cả hai đều nhận {@code null} nghĩa là khách chưa đăng nhập: {@code :viewerId}
     * (tài khoản trong token) và {@code :viewerPetId} (pet trong header {@code X-Pet-Id}). Người đã
     * đăng nhập nhưng chưa chọn pet thì {@code :viewerPetId} là null và chỉ mất nhánh C/D — nhánh E
     * vẫn cho họ thấy nội dung của chính mình.
     */
    public static final String VISIBLE_TO = """
            (
              (p.group is null and p.postScope = com.nguyenvu.lopet.post.entity.PostScope.PUBLIC)
              or (p.group is not null
                  and p.postScope = com.nguyenvu.lopet.post.entity.PostScope.PUBLIC
                  and g.type = com.nguyenvu.lopet.group.entity.GroupType.PUBLIC)
              or (:viewerPetId is not null and (
                   ap.id = :viewerPetId
                   or (p.group is not null
                       and exists (
                            select 1 from GroupMember gm
                            where gm.groupId = p.group.id and gm.petId = :viewerPetId
                              and gm.status = com.nguyenvu.lopet.group.entity.GroupMemberStatus.ACTIVE))
              ))
              or (:viewerId is not null and (
                   ap.account.id = :viewerId
                   or (p.group is null
                       and p.postScope = com.nguyenvu.lopet.post.entity.PostScope.FRIEND
                       and exists (
                            select 1 from Friendship f
                            where f.status = com.nguyenvu.lopet.friendship.entity.FriendshipStatus.ACCEPTED
                              and ((f.sender.id = :viewerId and f.receiver.id = ap.account.id)
                                or (f.receiver.id = :viewerId and f.sender.id = ap.account.id))))
              ))
            )
            """;

    private PostVisibility() {
    }
}
