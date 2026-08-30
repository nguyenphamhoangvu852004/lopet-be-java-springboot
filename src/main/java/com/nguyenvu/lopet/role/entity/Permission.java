package com.nguyenvu.lopet.role.entity;

import org.hibernate.annotations.SQLRestriction;

import com.nguyenvu.lopet.common.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
@Table(name = "permissions")
@SQLRestriction("deletedAt is null")
public class Permission extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "code", nullable = false, unique = true)
    private String code;

    @Column(name = "resource", nullable = false)
    private String resource;

    @Column(name = "action", nullable = false)
    private String action;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, columnDefinition = "enum('ANY','OWN') not null default 'ANY'")
    private PermissionScope scope;

    @Column(name = "description")
    private String description;
}
