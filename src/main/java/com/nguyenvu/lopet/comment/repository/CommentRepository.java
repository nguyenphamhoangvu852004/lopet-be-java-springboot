package com.nguyenvu.lopet.comment.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nguyenvu.lopet.comment.entity.Comment;

public interface CommentRepository extends JpaRepository<Comment, Integer> {

    @Query("""
            select distinct c from Comment c
            left join fetch c.account ap
            left join fetch ap.accountProfile
            left join fetch c.parent
            where c.post.id = :postId
            order by c.createdAt desc
            """)
    List<Comment> findAllByPostId(@Param("postId") Integer postId);

    @Query("""
            select c from Comment c
            left join fetch c.account ap
            left join fetch ap.accountProfile
            left join fetch c.post
            where c.id = :id
            """)
    Optional<Comment> findDetailById(@Param("id") Integer id);
}
