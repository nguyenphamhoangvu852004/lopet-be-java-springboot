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
            + "'MESSAGE','FRIEND_REQUEST','FRIEND_ACCEPTED','POST') not null")
    private NotificationObjectType objectType;

    @Column(name = "objectId")
    private Integer objectId;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false,
            columnDefinition = "enum('SENT','DELIVERED','READ') not null default 'SENT'")
    private NotificationStatus status = NotificationStatus.SENT;
}
