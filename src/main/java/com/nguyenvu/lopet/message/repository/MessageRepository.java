package com.nguyenvu.lopet.message.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nguyenvu.lopet.message.entity.Message;

public interface MessageRepository extends JpaRepository<Message, Integer> {

    @Query("""
            select m from Message m
            left join fetch m.sender
            left join fetch m.receiver
            where m.id = :id
            """)
    Optional<Message> findDetailById(@Param("id") Integer id);

    /** Hội thoại hai chiều giữa hai tài khoản, mới nhất trước */
    @Query("""
            select m from Message m
            left join fetch m.sender
            left join fetch m.receiver
            where (m.sender.id = :a and m.receiver.id = :b)
               or (m.sender.id = :b and m.receiver.id = :a)
            order by m.createdAt desc
            """)
    List<Message> findConversation(@Param("a") Integer accountIdA, @Param("b") Integer accountIdB);
}
