package com.nguyenvu.lopet.pet.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;

import com.nguyenvu.lopet.account.entity.Account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Bảng nối pet ↔ account, khai báo tường minh vì cần thêm cột {@code ownership_type}.
 *
 * <p>KHÔNG kế thừa {@code BaseEntity}: bảng chỉ có {@code createdAt}, không có {@code updatedAt}
 * lẫn {@code deletedAt} — mất quyền sở hữu là xoá hẳn bản ghi, nên "tồn tại bản ghi" đã đúng nghĩa
 * là đang sở hữu. Cùng lý do đó, không gắn {@code @SQLRestriction}.
 *
 * <p>Cột {@code pet_id}/{@code user_id} vừa là khoá chính vừa là FK, nên hai quan hệ
 * {@code @ManyToOne} tới chúng phải để {@code insertable=false, updatable=false} — giá trị được ghi
 * qua chính hai trường khoá.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "pet_ownerships")
@IdClass(PetOwnershipId.class)
public class PetOwnership {

    @Id
    @Column(name = "pet_id", nullable = false)
    private Integer petId;

    /** Cột giữ tên {@code user_id} theo schema, nhưng trỏ tới {@code accounts.id} */
    @Id
    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pet_id", insertable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Pet pet;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Account account;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "ownership_type", nullable = false, length = 20)
    private PetOwnershipType ownershipType;

    @CreationTimestamp
    @Column(name = "createdAt", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
