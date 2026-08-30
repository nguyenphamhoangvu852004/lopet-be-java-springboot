package com.nguyenvu.lopet.message.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nguyenvu.lopet.message.entity.Message;
import com.nguyenvu.lopet.message.entity.MessageStatus;

public interface MessageRepository extends JpaRepository<Message, Integer> {

    interface StatusTarget {
        Integer getId();

        Integer getSenderId();
    }

    @Query("""
            select m from Message m
            left join fetch m.sender
            left join fetch m.receiver
            where m.id = :id
            """)
    Optional<Message> findDetailById(@Param("id") Integer id);

    @Query("""
            select m from Message m
            left join fetch m.sender
            left join fetch m.receiver
            where (m.sender.id = :a and m.receiver.id = :b)
               or (m.sender.id = :b and m.receiver.id = :a)
            order by m.createdAt desc
            """)
    List<Message> findConversation(@Param("a") Integer accountIdA, @Param("b") Integer accountIdB);

    @Query("""
            select m.id as id, m.sender.id as senderId
            from Message m
            where m.id in :ids
              and m.receiver.id = :receiverId
              and m.status = :status
            """)
    List<StatusTarget> findPendingDelivery(@Param("ids") Collection<Integer> ids,
                                           @Param("receiverId") Integer receiverId,
                                           @Param("status") MessageStatus status);

    @Query("""
            select m.id as id, m.sender.id as senderId
            from Message m
            where m.receiver.id = :receiverId
              and m.status = :status
            """)
    List<StatusTarget> findAwaitingDelivery(@Param("receiverId") Integer receiverId,
                                            @Param("status") MessageStatus status);

    @Query("""
            select m.id as id, m.sender.id as senderId
            from Message m
            where m.receiver.id = :receiverId
              and m.sender.id = :senderId
              and m.status <> :read
            """)
    List<StatusTarget> findPendingRead(@Param("receiverId") Integer receiverId,
                                       @Param("senderId") Integer senderId,
                                       @Param("read") MessageStatus read);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Message m
            set m.status = :status,
                m.deliveredAt = coalesce(m.deliveredAt, :now),
                m.updatedAt = :now
            where m.id in :ids and m.deletedAt is null
            """)
    int applyDelivered(@Param("ids") Collection<Integer> ids,
                       @Param("status") MessageStatus status,
                       @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Message m
            set m.status = :status,
                m.readAt = coalesce(m.readAt, :now),
                m.deliveredAt = coalesce(m.deliveredAt, :now),
                m.updatedAt = :now
            where m.id in :ids and m.deletedAt is null
            """)
    int applyRead(@Param("ids") Collection<Integer> ids,
                  @Param("status") MessageStatus status,
                  @Param("now") LocalDateTime now);

    @Query("""
            select count(m) from Message m
            where m.receiver.id = :accountId and m.status <> :read
            """)
    long countUnread(@Param("accountId") Integer accountId, @Param("read") MessageStatus read);
}
