package com.nguyenvu.lopet.pet.entity;

import java.time.LocalDate;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

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

/**
 * Bảng KHÔNG có cột chủ sở hữu: quan hệ người ↔ thú cưng nằm hoàn toàn ở {@code pet_ownerships},
 * vì một thú cưng có thể có nhiều người đồng sở hữu.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "pets")
@SQLRestriction("deletedAt is null")
// Mỗi trường enum đều kèm @JdbcTypeCode(VARCHAR): từ Hibernate 6, @Enumerated(STRING) trên MySQL sinh
// ra kiểu `enum('A','B')` native chứ không phải varchar, và thuộc tính `length` bị bỏ qua. Schema của
// module này chọn varchar để thêm một loài/trạng thái mới không cần ALTER TABLE.
public class Pet extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "species", nullable = false, length = 30)
    private PetSpecies species;

    @Column(name = "breed", length = 100)
    private String breed;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "gender", nullable = false, length = 20)
    private PetGender gender;

    /**
     * Tên cột phải khai tường minh: naming strategy của dự án giữ nguyên tên property, để trống sẽ
     * sinh ra cột {@code dateOfBirth} thay vì {@code date_of_birth}.
     */
    @Column(name = "date_of_birth", nullable = false, columnDefinition = "date")
    private LocalDate dateOfBirth;

    @Column(name = "bio", length = 500)
    private String bio;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 20)
    private PetStatus status = PetStatus.ACTIVE;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "visibility", nullable = false, length = 20)
    private PetVisibility visibility = PetVisibility.PUBLIC;
}
