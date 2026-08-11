package com.nguyenvu.lopet.notification.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nguyenvu.lopet.notification.entity.Notification;

/**
 * Entity {@code Notification} cố ý không gắn {@code @SQLRestriction}, vì {@link #findDetailById}
 * phải đọc được cả bản ghi đã xoá mềm (bản TS dùng {@code withDeleted: true}). Bộ lọc xoá mềm vì
 * vậy được viết tường minh ở truy vấn danh sách.
 */
public interface NotificationRepository extends JpaRepository<Notification, Integer> {

    @Query("""
            select n from Notification n
            left join fetch n.actor
            left join fetch n.receptor
            where n.id = :id
            """)
    Optional<Notification> findDetailById(@Param("id") Integer id);

    @Query("""
            select n from Notification n
            left join fetch n.actor
            left join fetch n.receptor
            where n.receptor.id = :accountId and n.deletedAt is null
            order by n.createdAt desc
            """)
    List<Notification> findByReceptor(@Param("accountId") Integer accountId);
}
