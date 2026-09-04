package com.nguyenvu.lopet.post.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nguyenvu.lopet.post.entity.Post;

/**
 * Bài viết không còn phạm vi hiển thị: mọi bài đều công khai, nên không câu truy
 * vấn nào ở đây nhận `viewerId` hay lọc theo người xem nữa.
 */
public interface PostRepository extends JpaRepository<Post, Integer> {

    boolean existsByAccountId(Integer accountId);

    String READ_GRAPH = """
            select distinct p from Post p
            left join fetch p.account
            left join fetch p.postMedias
            """;

    @Query(READ_GRAPH + " where p.id = :id")
    Optional<Post> findDetailById(@Param("id") Integer id);

    @Query(READ_GRAPH + """
             where (:content is null or p.content like concat('%', :content, '%'))
             order by p.createdAt desc
            """)
    List<Post> findAllMatching(@Param("content") String content);

    @Query(READ_GRAPH + " where p.account.id = :authorId order by p.createdAt desc")
    List<Post> findByAuthor(@Param("authorId") Integer authorId);

    @Query("""
            select p.id from Post p
             where (:content is null or p.content like concat('%', :content, '%'))
             order by p.createdAt desc, p.id desc
            """)
    List<Integer> findIdsMatching(@Param("content") String content, Pageable pageable);

    @Query("""
            select count(p) from Post p
             where (:content is null or p.content like concat('%', :content, '%'))
            """)
    long countMatching(@Param("content") String content);

    @Query(READ_GRAPH + " where p.id in :ids order by p.createdAt desc, p.id desc")
    List<Post> findAllByIdsWithDetails(@Param("ids") List<Integer> ids);

    @Query(READ_GRAPH + """
            where (:cursor is null or p.id > :cursor)
                        order by p.id asc
            """)
    List<Post> fetchNextPage(@Param("cursor") Integer cursor, Pageable pageable);

    long countByDeletedAtIsNull();

    long countByCreatedAtGreaterThanEqual(LocalDateTime since);

}
