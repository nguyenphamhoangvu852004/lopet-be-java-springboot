package com.nguyenvu.lopet.report.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
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
@Table(name = "reports")
@SQLRestriction("deletedAt is null")
public class Report extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Account reporter;

    @Column(name = "reason", nullable = false, columnDefinition = "text")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, columnDefinition = "enum('USER','GROUP','POST') not null")
    private ReportType targetType;

    /** int chứ không phải tinyint — tinyint tràn ở id > 127 */
    @Column(name = "target_id", nullable = false)
    private Integer targetId;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false,
            columnDefinition = "enum('PENDING','CANCELLED','APPROVED') not null default 'PENDING'")
    private ReportAction action = ReportAction.PENDING;

    /** Staff đã xử lý báo cáo này — bắt buộc để audit hành động kiểm duyệt */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private Account resolvedBy;

    @Column(name = "resolved_at", columnDefinition = "datetime")
    private LocalDateTime resolvedAt;
}
