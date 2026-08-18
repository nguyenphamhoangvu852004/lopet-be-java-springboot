package com.nguyenvu.lopet.post.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nguyenvu.lopet.post.entity.PostLike;

public interface PostLikeRepository extends JpaRepository<PostLike, Integer> {

    /**
     * Tính duy nhất của một lượt thích tính theo PET, không theo tài khoản: hai thú cưng cùng chủ
     * thả tim cùng một bài là hai lượt thích khác nhau, đúng như hai người dùng khác nhau.
     */
    @Query("select l from PostLike l where l.pet.id = :petId and l.post.id = :postId")
    Optional<PostLike> findByPetAndPost(@Param("petId") Integer petId, @Param("postId") Integer postId);
}
