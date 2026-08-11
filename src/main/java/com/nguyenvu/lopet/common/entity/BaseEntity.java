package com.nguyenvu.lopet.common.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Getter;
import lombok.Setter;

/**
 * Ba cột audit của {@code src/entities/base.entity.ts}. Tên cột giữ nguyên camelCase vì TypeORM
 * không đổi tên property khi sinh schema — đổi sang snake_case ở đây là đổi schema.
 *
 * <p>Lưu ý: {@code post_likes} KHÔNG kế thừa lớp này. Entity đó extends BaseEntity của chính
 * TypeORM nên bảng chỉ có {@code id, post_id, account_id}.
 */
@Getter
@Setter
@MappedSuperclass
public abstract class BaseEntity {

    @CreationTimestamp
    @Column(name = "createdAt", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Cột NULL được — TypeORM khai {@code default: null}.
     *
     * <p>Cố ý KHÔNG dùng {@code @UpdateTimestamp}: Hibernate coi mọi cột mang annotation đó là NOT
     * NULL và bỏ qua cả {@code nullable = true} lẫn {@code columnDefinition}, làm DDL lệch schema
     * cũ. Callback vòng đời cho ra đúng ngữ nghĩa của {@code @UpdateDateColumn}: có giá trị ngay từ
     * lần ghi đầu tiên và được làm mới ở mỗi lần cập nhật.
     */
    @Column(name = "updatedAt")
    private LocalDateTime updatedAt;

    /**
     * Xoá mềm. Không luồng nghiệp vụ nào của lopet-be gọi {@code softRemove()} — mọi thao tác xoá
     * đều là xoá cứng — nên cột này luôn NULL trên dữ liệu thật. Nó vẫn được giữ vì mọi truy vấn
     * của TypeORM đều ngầm thêm {@code deletedAt IS NULL}, và các entity dưới đây tái hiện điều đó
     * bằng {@code @SQLRestriction}.
     */
    @Column(name = "deletedAt")
    private LocalDateTime deletedAt;

    @PrePersist
    @PreUpdate
    void touchUpdatedAt() {
        this.updatedAt = LocalDateTime.now();
    }
}
