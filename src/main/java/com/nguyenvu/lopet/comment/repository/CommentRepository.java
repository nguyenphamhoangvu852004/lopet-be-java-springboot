package com.nguyenvu.lopet.comment.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nguyenvu.lopet.comment.entity.Comment;

public interface CommentRepository extends JpaRepository<Comment, Integer> {

    /**
     * Nạp kèm {@code account.accountProfile}: mọi bình luận hiển thị hồ sơ của tác giả, nên để hồ sơ
     * lazy là quay lại đúng N+1 mà bản TS mắc phải.
     */
    @Query("""
            select distinct c from Comment c
            left join fetch c.account ap
            left join fetch ap.accountProfile
            left join fetch c.parent
            where c.post.id = :postId
            order by c.createdAt desc
            """)
    List<Comment> findAllByPostId(@Param("postId") Integer postId);

    /**
     * Nạp kèm {@code post}: tầng service phải đối chiếu bình luận cha thuộc đúng bài nào trước khi
     * cho trả lời, và quyền xem luôn được quyết theo BÀI chứ không theo bản thân bình luận.
     */
    @Query("""
            select c from Comment c
            left join fetch c.account ap
            left join fetch ap.accountProfile
            left join fetch c.post
            where c.id = :id
            """)
    Optional<Comment> findDetailById(@Param("id") Integer id);
}
