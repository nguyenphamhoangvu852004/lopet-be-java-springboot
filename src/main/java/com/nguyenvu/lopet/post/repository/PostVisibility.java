package com.nguyenvu.lopet.post.repository;

/**
 * Nguồn sự thật duy nhất cho câu hỏi "người này có được xem bài viết đó không?" — bản dịch của
 * {@code src/modules/post/repositories/postVisibility.ts}.
 *
 * <p>Trước bản vá bên TS, cột {@code posts.postScope} được ghi ở mọi luồng tạo/sửa bài nhưng KHÔNG
 * một query nào đọc tới, đồng thời các route đọc bài lại không xác thực — nghĩa là mọi bài PRIVATE
 * và FRIEND của mọi tài khoản đều đọc được bởi khách vãng lai chỉ bằng {@code GET /v1/posts}.
 *
 * <p>Năm nhánh, mặc định là ẨN:
 * <pre>
 *   A. Bài cá nhân, scope PUBLIC                      -> mọi người, kể cả khách
 *   B. Bài nhóm scope PUBLIC trong nhóm PUBLIC        -> mọi người, kể cả khách
 *   --- khách chưa đăng nhập dừng ở đây ---
 *   C. Bài của chính mình                             -> mọi scope
 *   D. Bài cá nhân scope FRIEND của bạn bè ACCEPTED   -> chỉ bạn bè
 *   E. Bài trong nhóm mà mình là thành viên           -> mọi scope
 * </pre>
 *
 * <p>Cột mốc phân biệt bài nhóm / bài cá nhân là {@code group_id}, KHÔNG phải {@code postType}:
 * postType nullable và chỉ được gán qua {@code applyType()}, nên dữ liệu cũ có thể còn NULL.
 *
 * <p>Nhánh B đòi thêm {@code group.type = PUBLIC}: tính riêng tư của nhóm là ràng buộc mạnh hơn
 * scope của từng bài, nên bài PUBLIC trong nhóm PRIVATE vẫn không lọt ra ngoài. Ràng buộc đó nằm ở
 * tầng query nên không endpoint nào đi vòng qua được.
 *
 * <p>Nhánh D dùng {@code exists} thay vì nạp trước danh sách id bạn bè rồi {@code in (...)}: danh
 * sách bạn bè không có giới hạn trên, {@code in} với hàng nghìn phần tử sẽ phình query và không
 * dùng được index.
 */
public final class PostVisibility {

    /**
     * Truy vấn dùng mệnh đề này BẮT BUỘC phải khai {@code left join p.group g}.
     *
     * <p>Không được viết thẳng {@code p.group.type}: path expression qua một quan hệ nullable sinh
     * ra INNER JOIN ngầm, và inner join đó sẽ loại sạch bài cá nhân (group_id NULL) khỏi kết quả —
     * tức là nhánh A biến mất trong im lặng. Alias của LEFT JOIN thì không có tác dụng phụ đó.
     *
     * <p>{@code p.account.id} và {@code p.group.id} thì an toàn: Hibernate đọc thẳng cột khoá ngoại
     * chứ không join.
     *
     * <p>Tham số {@code :viewerId} nhận {@code null} nghĩa là khách chưa đăng nhập.
     *
     * <p><b>Nhánh thành viên nhóm đòi {@code gm.status = ACTIVE}</b>, không chỉ "có hàng trong
     * group_members". Từ khi nhóm có luồng xin vào và mời, một hàng PENDING nằm sẵn ở đó cho người
     * vừa bấm xin vào nhóm PRIVATE hoặc vừa được mời — cả hai đều là hành động của người NGOÀI nhóm.
     * Bỏ điều kiện này thì chỉ cần gửi yêu cầu là đọc được toàn bộ bài trong nhóm trước khi ai kịp
     * duyệt, tức là PRIVATE không còn nghĩa gì. Đây là chỗ DUY NHẤT tính riêng tư của nhóm được thực
     * thi ở đường đọc, nên điều kiện phải nằm đúng trong hằng số này.
     */
    public static final String VISIBLE_TO = """
            (
              (p.group is null and p.postScope = com.nguyenvu.lopet.post.entity.PostScope.PUBLIC)
              or (p.group is not null
                  and p.postScope = com.nguyenvu.lopet.post.entity.PostScope.PUBLIC
                  and g.type = com.nguyenvu.lopet.group.entity.GroupType.PUBLIC)
              or (:viewerId is not null and (
                   p.account.id = :viewerId
                   or (p.group is null
                       and p.postScope = com.nguyenvu.lopet.post.entity.PostScope.FRIEND
                       and exists (
                            select 1 from Friendship f
                            where f.status = com.nguyenvu.lopet.friendship.entity.FriendshipStatus.ACCEPTED
                              and ((f.sender.id = :viewerId and f.receiver.id = p.account.id)
                                or (f.receiver.id = :viewerId and f.sender.id = p.account.id))))
                   or (p.group is not null
                       and exists (
                            select 1 from GroupMember gm
                            where gm.groupId = p.group.id and gm.accountId = :viewerId
                              and gm.status = com.nguyenvu.lopet.group.entity.GroupMemberStatus.ACTIVE))
              ))
            )
            """;

    private PostVisibility() {
    }
}
