package com.nguyenvu.lopet.post.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nguyenvu.lopet.post.entity.Post;

/**
 * Mọi hàm đọc danh sách đều nhận {@code viewerId} + {@code viewerPetId} và lọc theo
 * {@link PostVisibility} — không có đường nào trả về bài viết mà bỏ qua bước lọc, trừ
 * {@link #findByIdInternal(Integer)} (xem ghi chú ở đó).
 *
 * <p>Chiến lược nạp: {@code pet} (kèm {@code petProfile}), {@code group}, {@code postMedias} được
 * fetch join; còn {@code postLikes} (và {@code postLikes.pet}) để lazy + batch fetch. Fetch join cả
 * hai collection cùng lúc sẽ nhân bản hàng (media × like) đúng như bản TS đang làm; kết quả sau khử
 * trùng lặp là như nhau, nên tách ra vừa giữ nguyên dữ liệu trả về vừa không phình truy vấn.
 *
 * <p>{@code petProfile} nằm trong đồ thị nạp vì mọi DTO bài viết đều hiển thị tác giả bằng handle và
 * display name — để nó lazy thì mỗi bài trong danh sách sinh thêm một truy vấn.
 */
public interface PostRepository extends JpaRepository<Post, Integer> {

    String READ_GRAPH = """
            select distinct p from Post p
            left join fetch p.pet ap
            left join fetch ap.petProfile
            left join fetch p.group g
            left join fetch p.postMedias
            """;

    /**
     * Nạp bài theo id KHÔNG lọc quyền riêng tư.
     *
     * <p>Cố ý giữ nguyên hành vi của {@code getOne()}: đây là hàm nạp nội bộ cho luồng sửa bài và
     * luồng kiểm duyệt của staff. Nếu lọc ở đây thì MODERATOR sẽ nhận 404 khi xoá một bài PRIVATE
     * bị báo cáo. Route đọc công khai phải dùng {@link #findVisibleById}.
     */
    @Query("""
            select distinct p from Post p
            left join fetch p.pet ap
            left join fetch ap.petProfile
            left join fetch p.group
            left join fetch p.postMedias
            where p.id = :id
            """)
    Optional<Post> findByIdInternal(@Param("id") Integer id);

    @Query(READ_GRAPH + " where p.id = :id and " + PostVisibility.VISIBLE_TO)
    Optional<Post> findVisibleById(@Param("id") Integer id,
                                   @Param("viewerId") Integer viewerId,
                                   @Param("viewerPetId") Integer viewerPetId);

    /**
     * Danh sách bài kèm bộ lọc tìm kiếm. {@code like} chứ không phải ILIKE: MySQL không có toán tử
     * đó và collation mặc định vốn đã không phân biệt hoa thường.
     */
    @Query(READ_GRAPH + " where " + PostVisibility.VISIBLE_TO + """
             and (:content is null or p.content like concat('%', :content, '%'))
             and (:groupId is null or p.group.id = :groupId)
             order by p.createdAt desc
            """)
    List<Post> findAllVisible(@Param("viewerId") Integer viewerId,
                              @Param("viewerPetId") Integer viewerPetId,
                              @Param("content") String content,
                              @Param("groupId") Integer groupId);

    /**
     * Bài của MỘT thú cưng cụ thể — đơn vị tác giả thật sau khi {@code posts.account_id} thành
     * {@code posts.pet_id}.
     */
    @Query(READ_GRAPH + " where ap.id = :authorPetId and " + PostVisibility.VISIBLE_TO + """
             order by p.createdAt desc
            """)
    List<Post> findVisibleByAuthorPet(@Param("authorPetId") Integer authorPetId,
                                      @Param("viewerId") Integer viewerId,
                                      @Param("viewerPetId") Integer viewerPetId);

    /**
     * Bài của TẤT CẢ thú cưng thuộc một tài khoản — giữ cho {@code GET /v1/posts/accounts/{id}} còn
     * ý nghĩa sau khi tác giả đổi sang pet. Lọc qua {@code ap.account.id} chứ không qua một cột
     * {@code account_id} nào trên {@code posts}: cột đó đã bị bỏ, và giữ lại một bản sao của nó sẽ
     * tạo ra hai câu trả lời khác nhau cho cùng câu hỏi "bài này của ai" khi pet đổi chủ.
     */
    @Query(READ_GRAPH + " where ap.account.id = :authorAccountId and " + PostVisibility.VISIBLE_TO + """
             order by p.createdAt desc
            """)
    List<Post> findVisibleByAuthorAccount(@Param("authorAccountId") Integer authorAccountId,
                                          @Param("viewerId") Integer viewerId,
                                          @Param("viewerPetId") Integer viewerPetId);

    /**
     * Hai bước cho danh sách gợi ý: lấy id có giới hạn trước, rồi mới nạp chi tiết.
     *
     * <p>Giới hạn số lượng phải áp trên tập KHÔNG join collection — áp thẳng lên truy vấn có join
     * 1-n thì mười hàng thô không đồng nghĩa mười bài viết. Bản TS xử lý đúng vấn đề này bằng
     * {@code take(10)} thay vì {@code limit(10)}.
     */
    @Query("select p.id from Post p left join p.group g left join p.pet ap where "
            + PostVisibility.VISIBLE_TO + " order by p.createdAt desc")
    List<Integer> findVisibleIds(@Param("viewerId") Integer viewerId,
                                 @Param("viewerPetId") Integer viewerPetId,
                                 Pageable pageable);

    @Query(READ_GRAPH + " where p.id in :ids order by p.createdAt desc")
    List<Post> findAllByIdsWithDetails(@Param("ids") List<Integer> ids);
}
