package com.nguyenvu.lopet.account.entity;

import java.util.LinkedHashSet;
import java.util.Set;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.SQLRestriction;

import com.nguyenvu.lopet.common.entity.BaseEntity;
import com.nguyenvu.lopet.profile.entity.Profile;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
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
@Table(name = "accounts")
@SQLRestriction("deletedAt is null")
public class Account extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "username", nullable = false, unique = true)
    private String username;

    /**
     * Bcrypt hash — bên TS cột này khai {@code select: false} nên mọi truy vấn thường không nạp nó.
     * JPA không có tương đương khai báo, vì vậy quy ước thay thế: KHÔNG DTO nào chứa trường này, và
     * chỉ luồng xác thực mới đọc entity Account đầy đủ.
     */
    @Column(name = "password", nullable = false)
    private String password;

    /** tinyint nullable, code cũ so sánh {@code isBanned == 1} nên không dùng boolean */
    @Builder.Default
    @Column(name = "isBanned", columnDefinition = "tinyint not null default 0")
    private Integer isBanned = 0;

    /** FK nằm ở phía accounts (accounts.profileId) đúng như {@code @JoinColumn} của TypeORM */
    @OneToOne(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinColumn(name = "profileId")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Profile profile;

    @Builder.Default
    @OneToMany(mappedBy = "account", fetch = FetchType.LAZY)
    private Set<AccountRole> accountRoles = new LinkedHashSet<>();
}
