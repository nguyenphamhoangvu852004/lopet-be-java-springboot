package com.nguyenvu.lopet.post.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nguyenvu.lopet.post.entity.PostMedia;

public interface PostMediaRepository extends JpaRepository<PostMedia, Integer> {

    List<PostMedia> findByPostId(Integer postId);

    /**
     * Hai biến thể tương ứng đúng hai nhánh của {@code deleteNotIn()} bên TS: mệnh đề
     * {@code NOT IN} chỉ được thêm khi danh sách giữ lại không rỗng, còn danh sách rỗng nghĩa là
     * xoá sạch media của bài.
     */
    @Modifying
    @Query("delete from PostMedia m where m.post.id = :postId")
    void deleteAllByPostId(@Param("postId") Integer postId);

    @Modifying
    @Query("delete from PostMedia m where m.post.id = :postId and m.id not in :keepIds")
    void deleteByPostIdAndIdNotIn(@Param("postId") Integer postId, @Param("keepIds") List<Integer> keepIds);
}
