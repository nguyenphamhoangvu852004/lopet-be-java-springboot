package com.nguyenvu.lopet.post.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nguyenvu.lopet.post.entity.PostLike;

public interface PostLikeRepository extends JpaRepository<PostLike, Integer> {

    @Query("select l from PostLike l where l.account.id = :accountId and l.post.id = :postId")
    Optional<PostLike> findByAccountAndPost(@Param("accountId") Integer accountId, @Param("postId") Integer postId);
}
