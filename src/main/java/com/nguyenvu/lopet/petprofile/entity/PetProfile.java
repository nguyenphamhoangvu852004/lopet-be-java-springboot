package com.nguyenvu.lopet.petprofile.entity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import com.nguyenvu.lopet.common.entity.BaseEntity;
import com.nguyenvu.lopet.pet.entity.Pet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Mặt công khai của một thú cưng — thực thể thật sự hoạt động trên mạng xã hội.
 *
 * <p><b>Không bảng nội dung nào trỏ FK vào đây.</b> Bài viết, bình luận, thành viên nhóm đều trỏ
 * vào {@code pets.id}. Lý do: hồ sơ công khai là thứ đổi được và khoá được (đổi handle, ẩn hồ sơ,
 * DEACTIVATED), còn khoá ngoại phải trỏ vào một danh tính bất biến. Trỏ vào {@code pet_profiles.id}
 * thì mỗi lần kiểm duyệt khoá một hồ sơ là kéo theo câu hỏi phải làm gì với hàng nghìn bài viết
 * đang tham chiếu nó.
 *
 * <p>{@code pet_id} UNIQUE là cái khoá bất biến "một con vật đúng một hồ sơ" ở tầng DB — tầng ứng
 * dụng có bug gì thì ràng buộc này vẫn giữ.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "pet_profiles")
@SQLRestriction("deletedAt is null")
public class PetProfile extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /**
     * Chủ của hồ sơ này. UNIQUE ở tầng DB nhờ {@code @OneToOne} — Hibernate sinh ràng buộc unique
     * cho cột khoá ngoại của quan hệ 1-1.
     */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pet_id", nullable = false, unique = true)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Pet pet;

    /**
     * Định danh trong URL và trong {@code @mention}. Chuẩn hoá về chữ thường ngay ở
     * {@code PetPolicy.requireHandle} trước khi tới đây, nên so sánh trùng không phụ thuộc collation
     * của MySQL.
     *
     * <p>Là khoá tra cứu CÔNG KHAI thay cho id số: {@code GET /v1/pet-profiles/handle/{handle}}.
     */
    @Column(name = "handle", nullable = false, unique = true, length = 30)
    private String handle;

    /** Tên hiện ra ngoài. Seed bằng {@code pet.name} lúc tạo, sau đó hai trường sống độc lập. */
    @Column(name = "display_name", nullable = false, length = 50)
    private String displayName;

    @Column(name = "avatar_url", columnDefinition = "text")
    private String avatarUrl;

    @Column(name = "cover_url", columnDefinition = "text")
    private String coverUrl;

    @Column(name = "bio", length = 500)
    private String bio;

    /**
     * Ngừng hoạt động = ẩn khỏi mạng xã hội, KHÔNG xoá. Ghi kèm {@code deletedAt} nên
     * {@code @SQLRestriction} ở trên tự loại hồ sơ khỏi mọi truy vấn; giữ thêm cột status để bản
     * ghi tự mô tả được trạng thái khi đọc thẳng trong DB.
     */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 20)
    private PetProfileStatus status = PetProfileStatus.ACTIVE;

    /** Cấu hình riêng tư — nguồn sự thật duy nhất cho "ai được xem hồ sơ này" */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "visibility", nullable = false, length = 20)
    private PetVisibility visibility = PetVisibility.PUBLIC;
}
