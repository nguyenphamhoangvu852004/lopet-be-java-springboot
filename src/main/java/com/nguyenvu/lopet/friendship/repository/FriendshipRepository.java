package com.nguyenvu.lopet.friendship.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nguyenvu.lopet.friendship.entity.Friendship;
import com.nguyenvu.lopet.friendship.entity.FriendshipStatus;

public interface FriendshipRepository extends JpaRepository<Friendship, Integer> {

    /**
     * Chỉ dò ĐÚNG chiều gửi→nhận. Bản TS cũng vậy, nên hai bản ghi ngược chiều giữa cùng một cặp
     * tài khoản có thể cùng tồn tại — không được "sửa" thành dò hai chiều ở đây.
     */
    @Query("select f from Friendship f where f.sender.id = :senderId and f.receiver.id = :receiverId")
    Optional<Friendship> findBySenderAndReceiver(@Param("senderId") Integer senderId,
                                                 @Param("receiverId") Integer receiverId);

    /** Đã là bạn bè (ACCEPTED) hay chưa, xét cả hai chiều */
    @Query("""
            select count(f) from Friendship f
            where f.status = com.nguyenvu.lopet.friendship.entity.FriendshipStatus.ACCEPTED
              and ((f.sender.id = :a and f.receiver.id = :b) or (f.sender.id = :b and f.receiver.id = :a))
            """)
    long countAcceptedBetween(@Param("a") Integer accountIdA, @Param("b") Integer accountIdB);

    @Query("select f from Friendship f where f.sender.id = :senderId and f.status = :status")
    List<Friendship> findBySenderAndStatus(@Param("senderId") Integer senderId,
                                           @Param("status") FriendshipStatus status);

    @Query("select f from Friendship f where f.receiver.id = :receiverId and f.status = :status")
    List<Friendship> findByReceiverAndStatus(@Param("receiverId") Integer receiverId,
                                             @Param("status") FriendshipStatus status);

    @Query("""
            select f from Friendship f
            where (f.sender.id = :accountId or f.receiver.id = :accountId)
              and f.status = com.nguyenvu.lopet.friendship.entity.FriendshipStatus.ACCEPTED
            """)
    List<Friendship> findAcceptedOf(@Param("accountId") Integer accountId);

    /** Mọi quan hệ (bất kể trạng thái) của một tập tài khoản — dùng dựng danh sách tài khoản */
    @Query("select f from Friendship f where f.sender.id in :ids or f.receiver.id in :ids")
    List<Friendship> findAllInvolving(@Param("ids") List<Integer> accountIds);
}
