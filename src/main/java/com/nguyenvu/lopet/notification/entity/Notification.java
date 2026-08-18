package com.nguyenvu.lopet.notification.entity;

import com.nguyenvu.lopet.account.entity.Account;
import com.nguyenvu.lopet.common.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entity DUY NHẤT không gắn {@code @SQLRestriction("deletedAt is null")}.
 *
 * <p>Lý do: {@code NotificationsRepoImpl.findById} bên TS gọi với {@code withDeleted: true}, tức là
 * đọc được cả bản ghi đã xoá mềm. Gắn restriction ở tầng entity sẽ chặn luôn luồng đó. Bộ lọc xoá
 * mềm cho các truy vấn còn lại được viết tường minh trong repository.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "notifications")
public class Notification extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actorId", nullable = false)
    private Account actor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receptorId", nullable = false)
    private Account receptor;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "objectType", nullable = false, columnDefinition = "enum('POST_LIKE','POST_COMMENT',"
            + "'MESSAGE','FRIEND_REQUEST','FRIEND_ACCEPTED','POST',"
            + "'GROUP_JOIN_REQUESTED','GROUP_JOIN_APPROVED','GROUP_INVITED',"
            + "'GROUP_INVITE_ACCEPTED') not null")
    private NotificationObjectType objectType;

    /**
     * Id của đối tượng mà thông báo nói tới — bài viết, tin nhắn, hoặc người liên quan, tuỳ
     * {@link NotificationObjectType}.
     *
     * <p>Nullable vì hai lý do, đừng siết thành NOT NULL: bản ghi cũ sinh ra trước khi có cột này
     * không có gì để backfill, và loại {@link NotificationObjectType#POST} cũ vốn không trỏ tới đâu.
     *
     * <p>Không đặt khoá ngoại: cột trỏ tới ba bảng khác nhau tuỳ loại, nên không có một bảng đích cố
     * định để tham chiếu. Đổi lại, đối tượng bị xoá thì thông báo vẫn còn và frontend phải chịu được
     * việc bấm vào một đích không còn tồn tại.
     */
    @Column(name = "objectId")
    private Integer objectId;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false,
            columnDefinition = "enum('SENT','DELIVERED','READ') not null default 'SENT'")
    private NotificationStatus status = NotificationStatus.SENT;
}
