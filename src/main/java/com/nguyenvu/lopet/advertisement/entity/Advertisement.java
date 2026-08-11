package com.nguyenvu.lopet.advertisement.entity;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.SQLRestriction;

import com.nguyenvu.lopet.advertiser.entity.AdvertiserProfile;
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
@Table(name = "advertisements")
@SQLRestriction("deletedAt is null")
public class Advertisement extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /**
     * Chủ sở hữu trỏ tới {@code advertiser_profiles} chứ không phải {@code accounts}, vì quyền
     * tạo/sửa quảng cáo phụ thuộc trạng thái duyệt của hồ sơ đó. Đây cũng là cột mà tầng ownership
     * đối chiếu ở mọi thao tác update/delete (qua {@code advertiser.account.id}).
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "advertiser_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private AdvertiserProfile advertiser;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;

    @Column(name = "image_url", nullable = false, columnDefinition = "text")
    private String imageUrl;

    @Column(name = "link_reference", nullable = false, columnDefinition = "text")
    private String linkReference;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false,
            columnDefinition = "enum('DRAFT','REVIEW','ACTIVE','REJECTED') not null default 'DRAFT'")
    private AdStatus status = AdStatus.DRAFT;
}
