package com.nguyenvu.lopet.pet.entity;

import java.time.LocalDate;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import com.nguyenvu.lopet.account.entity.Account;
import com.nguyenvu.lopet.common.entity.BaseEntity;
import com.nguyenvu.lopet.petprofile.entity.PetProfile;

import jakarta.persistence.CascadeType;
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
 * Con vật — dữ liệu SINH HỌC và quyền sở hữu. Mọi thứ hiện ra ngoài mạng xã hội (tên hiển thị,
 * avatar, bio, handle, phạm vi riêng tư) nằm ở {@link PetProfile}.
 *
 * <p>Ranh giới đó không phải thẩm mỹ. Loài, giống, ngày sinh, giới tính là sự thật về con vật và
 * không đổi theo cách nó xuất hiện trước người khác; còn hồ sơ công khai thì đổi liên tục và là thứ
 * duy nhất người lạ được đọc. Trộn chung một bảng thì mỗi lần trả hồ sơ công khai đều phải nhớ loại
 * bỏ tay các cột riêng tư.
 *
 * <p><b>Sở hữu là 1:N</b>: {@code account_id} NOT NULL, một tài khoản nhiều thú cưng. Bảng nối
 * {@code pet_ownerships} (đồng sở hữu PRIMARY_OWNER/CO_OWNER) đã bị bỏ — mô hình đó khiến câu hỏi
 * "petId này có thuộc accountId trong token không?" cần một truy vấn bảng nối ở MỌI request tương
 * tác, trong khi cột khoá ngoại trả lời được ngay và cache được vào Redis.
 *
 * <p>{@code id} của bảng NÀY (không phải của {@code pet_profiles}) là thứ mọi nội dung xã hội trỏ
 * tới. Hồ sơ công khai có thể bị đổi hoặc khoá; danh tính của con vật thì không.
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

    /**
     * Chủ sở hữu. NOT NULL và không có luồng nào cho client gửi giá trị này — nó luôn suy ra từ
     * danh tính trong token (xem {@code PetService.create}).
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Account account;

    /**
     * Tên gọi thật của con vật. KHÁC {@code petProfile.displayName} — cái sau là tên hiện ra ngoài
     * và người dùng đổi thoải mái; cái này là dữ liệu của chủ, chỉ chủ đọc được qua danh sách
     * "thú cưng của tôi".
     */
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

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 20)
    private PetStatus status = PetStatus.ACTIVE;

    /**
     * Quan hệ 1-1, khoá ngoại nằm ở phía {@code pet_profiles.pet_id}.
     *
     * <p>{@code CascadeType.ALL}: hồ sơ công khai không có vòng đời riêng — nó sinh ra cùng con vật
     * và chết cùng con vật. Đây là một nửa của bất biến "không tồn tại Pet thiếu PetProfile"; nửa
     * còn lại là {@code PetService.create} dựng cả hai trong cùng một transaction.
     */
    @OneToOne(mappedBy = "pet", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private PetProfile petProfile;
}
