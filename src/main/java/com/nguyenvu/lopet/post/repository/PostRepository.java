package com.nguyenvu.lopet.post.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nguyenvu.lopet.post.entity.Post;
import org.springframework.data.web.PageableArgumentResolver;

public interface PostRepository extends JpaRepository<Post, Integer> {

    boolean existsByAccountId(Integer accountId);

    String READ_GRAPH = """
            select distinct p from Post p
            left join fetch p.account
            left join fetch p.postMedias
            """;

    @Query("""
            select distinct p from Post p
            left join fetch p.account
            left join fetch p.postMedias
            where p.id = :id
            """)
    Optional<Post> findByIdInternal(@Param("id") Integer id);

    @Query(READ_GRAPH + " where p.id = :id and " + PostVisibility.VISIBLE_TO)
    Optional<Post> findVisibleById(@Param("id") Integer id, @Param("viewerId") Integer viewerId);

    @Query(READ_GRAPH + " where " + PostVisibility.VISIBLE_TO + """
             and (:content is null or p.content like concat('%', :content, '%'))
             order by p.createdAt desc
            """)
    List<Post> findAllVisible(@Param("viewerId") Integer viewerId, @Param("content") String content);

    @Query(READ_GRAPH + " where p.account.id = :authorId and " + PostVisibility.VISIBLE_TO + """
             order by p.createdAt desc
            """)
    List<Post> findVisibleByAuthor(@Param("authorId") Integer authorId, @Param("viewerId") Integer viewerId);

    @Query("select p.id from Post p where " + PostVisibility.VISIBLE_TO + """
             and (:content is null or p.content like concat('%', :content, '%'))
             order by p.createdAt desc, p.id desc
            """)
    List<Integer> findVisibleIds(@Param("viewerId") Integer viewerId, @Param("content") String content,
                                 Pageable pageable);

    @Query("select count(p) from Post p where " + PostVisibility.VISIBLE_TO + """
             and (:content is null or p.content like concat('%', :content, '%'))
            """)
    long countVisible(@Param("viewerId") Integer viewerId, @Param("content") String content);

    @Query(READ_GRAPH + " where p.id in :ids order by p.createdAt desc, p.id desc")
    List<Post> findAllByIdsWithDetails(@Param("ids") List<Integer> ids);

    @Query(READ_GRAPH + """
            where (:cursor is null or p.id > :cursor)
                        order by p.id asc 
            """)
    List<Post> fetchNextPage(@Param("cursor")  Integer cursor, Pageable pageable);

}
