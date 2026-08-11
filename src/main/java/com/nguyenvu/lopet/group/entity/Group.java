package com.nguyenvu.lopet.group.entity;

import java.util.LinkedHashSet;
import java.util.Set;

import org.hibernate.annotations.SQLRestriction;

import com.nguyenvu.lopet.common.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Tên bảng {@code groups} là từ khoá dành riêng của MySQL 8.0.2+ (window function) nên phải quote
 * bằng backtick — Hibernate tự dịch sang ký tự quote của dialect đang dùng.
 *
 * <p>Không có cột {@code owner}: chủ nhóm là bản ghi {@code group_members} có role = OWNER, nhờ đó
 * một nhóm có thể có nhiều co-admin thay vì đúng một chủ duy nhất.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "`groups`")
@SQLRestriction("deletedAt is null")
public class Group extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, columnDefinition = "enum('PUBLIC','PRIVATE') not null")
    private GroupType type;

    @Column(name = "bio")
    private String bio;

    @Column(name = "coverUrl", nullable = false, columnDefinition = "text")
    private String coverUrl;

    @Builder.Default
    @OneToMany(mappedBy = "group", fetch = FetchType.LAZY)
    private Set<GroupMember> members = new LinkedHashSet<>();
}
