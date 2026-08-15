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

    /**
     * Một tin nhắn sắp đổi trạng thái, kèm người cần được báo. Các luồng đánh dấu đều chọn ứng viên
     * TRƯỚC rồi mới cập nhật theo đúng danh sách id đó — nhờ vậy biết chính xác tin nào thực sự đổi
     * và phải bắn sự kiện về cho ai, thay vì chỉ nhận lại một con số đếm từ câu UPDATE.
     */
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

    /**
     * Trong lô id client vừa ack, lọc ra những tin THẬT SỰ gửi cho người này và chưa được đánh dấu.
     * Điều kiện {@code receiver.id = :receiverId} là chốt an ninh, không phải tối ưu: thiếu nó,
     * client chỉ cần đoán id là đổi được trạng thái tin nhắn của hai người xa lạ.
     */
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

    /**
     * Mọi tin đang chờ ack "đã nhận" của một người, không giới hạn hội thoại nào.
     *
     * <p>Dành cho lúc client vừa online trở lại: tin đến trong khi họ đăng xuất không được socket
     * nào chuyển tới, nên không có ack nào từng được phát và chúng nằm lại ở SENT vĩnh viễn. Không
     * có câu này thì "đã nhận" chỉ hoạt động cho người đang mở sẵn ứng dụng.
     *
     * <p>Dùng chung index {@code idx_messages_receiver_sender_status}: cột {@code receiver_id} đứng
     * đầu nên tiền tố (receiver_id) vẫn được dùng, dù index sinh ra cho truy vấn ba cột.
     */
    @Query("""
            select m.id as id, m.sender.id as senderId
            from Message m
            where m.receiver.id = :receiverId
              and m.status = :status
            """)
    List<StatusTarget> findAwaitingDelivery(@Param("receiverId") Integer receiverId,
                                            @Param("status") MessageStatus status);

    /** Mọi tin của một hội thoại mà người đọc chưa xem — dùng cho "đánh dấu đã xem cả hội thoại" */
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

    /**
     * Cập nhật hàng loạt, KHÔNG đi qua persistence context.
     *
     * <p>Hai hệ quả đã được xử lý ngay trong câu lệnh, đừng bỏ đi:
     * <ul>
     *   <li>{@code @SQLRestriction} của entity không áp cho bulk update, nên điều kiện
     *       {@code deletedAt is null} phải viết tay — thiếu nó là hồi sinh tin đã xoá mềm;</li>
     *   <li>callback {@code @PreUpdate} của BaseEntity cũng không chạy, nên {@code updatedAt} phải
     *       tự gán.</li>
     * </ul>
     *
     * <p>{@code coalesce} giữ mốc CŨ khi đã có: một tin được ack hai lần (client kết nối lại và gửi
     * lại) không được phép dời thời điểm nhận về hiện tại.
     */
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

    /** Đã xem thì đương nhiên đã nhận — điền luôn {@code deliveredAt} cho tin nhảy thẳng SENT → READ */
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
