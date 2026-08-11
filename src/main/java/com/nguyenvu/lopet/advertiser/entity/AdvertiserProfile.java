package com.nguyenvu.lopet.advertiser.entity;

import java.math.BigDecimal;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Account capability, không phải role bảo mật: mang trạng thái duyệt, số dư và hạn mức chi tiêu —
 * những thứ bảng roles không chứa được. Advertiser vẫn giữ nguyên toàn bộ quyền của user thường.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "advertiser_profiles")
@SQLRestriction("deletedAt is null")
public class AdvertiserProfile extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, unique = true)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Account account;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false,
            columnDefinition = "enum('PENDING','APPROVED','SUSPENDED') not null default 'PENDING'")
    private AdvertiserStatus status = AdvertiserStatus.PENDING;

    @Column(name = "company_name")
    private String companyName;

    @Builder.Default
    @Column(name = "balance", precision = 12, scale = 2, nullable = false)
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(name = "daily_limit", precision = 12, scale = 2)
    private BigDecimal dailyLimit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private Account approvedBy;

    @Column(name = "approved_at", columnDefinition = "datetime")
    private LocalDateTime approvedAt;
}
