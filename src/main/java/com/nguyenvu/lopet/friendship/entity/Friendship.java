package com.nguyenvu.lopet.friendship.entity;

import org.hibernate.annotations.SQLRestriction;

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
 * KHÔNG có UNIQUE(sender_id, receiver_id): hai bản ghi ngược chiều giữa cùng một cặp tài khoản có
 * thể cùng tồn tại, đúng như dữ liệu hiện tại cho phép.
 *
 * <p>Hai quan hệ khai EAGER vì bên TS đặt {@code eager: true} — nhiều luồng đọc thẳng
 * {@code friendship.sender.id} ngay sau khi query mà không join tay.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "friend_ships")
@SQLRestriction("deletedAt is null")
public class Friendship extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "sender_id")
    private Account sender;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "receiver_id")
    private Account receiver;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false,
            columnDefinition = "enum('PENDING','ACCEPTED','REJECTED','BLOCKED') not null default 'PENDING'")
    private FriendshipStatus status = FriendshipStatus.PENDING;
}
