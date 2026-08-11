package com.nguyenvu.lopet.account.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.SQLRestriction;

import com.nguyenvu.lopet.common.entity.BaseEntity;
import com.nguyenvu.lopet.role.entity.Role;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Bảng nối account ↔ role, khai báo tường minh (không dùng bảng nối ngầm) vì cần thêm cột audit:
 * ai cấp quyền và cấp lúc nào.
 *
 * <p>Cột {@code account_id}/{@code role_id} vừa là khoá chính vừa là FK, nên quan hệ
 * {@code @ManyToOne} tới chúng phải để {@code insertable=false, updatable=false} — giá trị được ghi
 * qua chính hai trường khoá.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "account_role")
@IdClass(AccountRoleId.class)
@SQLRestriction("deletedAt is null")
public class AccountRole extends BaseEntity {

    @Id
    @Column(name = "account_id", nullable = false)
    private Integer accountId;

    @Id
    @Column(name = "role_id", nullable = false)
    private Integer roleId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", insertable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", insertable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Role role;

    /**
     * Staff đã cấp role này — lấy từ token của người thao tác, không nhận từ body.
     * Giữ dạng quan hệ (không phải cột Integer trần) để FK {@code ON DELETE SET NULL} còn nguyên trong
     * schema; gán bằng entity reference nên không tốn thêm truy vấn.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "granted_by")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private Account grantedBy;

    @Column(name = "granted_at", columnDefinition = "datetime not null default CURRENT_TIMESTAMP")
    private LocalDateTime grantedAt;
}
