package com.nguyenvu.lopet.message.entity;

import java.time.LocalDateTime;

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

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "messages")
@SQLRestriction("deletedAt is null")
public class Message extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id")
    private Account sender;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id")
    private Account receiver;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "mediaUrl", nullable = false, columnDefinition = "text")
    private String mediaUrl;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false,
            columnDefinition = "enum('SENT','DELIVERED','READ') not null default 'SENT'")
    private MessageStatus status = MessageStatus.SENT;

    @Column(name = "deliveredAt")
    private LocalDateTime deliveredAt;

    @Column(name = "readAt")
    private LocalDateTime readAt;
}
