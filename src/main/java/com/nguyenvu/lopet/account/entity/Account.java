package com.nguyenvu.lopet.account.entity;

import java.util.LinkedHashSet;
import java.util.Set;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.SQLRestriction;

import com.nguyenvu.lopet.accountprofile.entity.AccountProfile;
import com.nguyenvu.lopet.common.entity.BaseEntity;

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

    @Column(name = "password", nullable = false)
    private String password;

    @Builder.Default
    @Column(name = "isBanned", columnDefinition = "tinyint not null default 0")
    private Integer isBanned = 0;

    @OneToOne(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinColumn(name = "account_profile_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private AccountProfile accountProfile;

    @Builder.Default
    @OneToMany(mappedBy = "account", fetch = FetchType.LAZY)
    private Set<AccountRole> accountRoles = new LinkedHashSet<>();
}
