package com.nguyenvu.lopet.group.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.SQLRestriction;

import com.nguyenvu.lopet.common.entity.BaseEntity;
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
 * Rời nhóm / bị từ chối = XOÁ hẳn bản ghi, không có BANNED hay LEFT. Nhưng "tồn tại bản ghi" KHÔNG
 * còn đồng nghĩa "đang là thành viên" — xem {@link #status}: một yêu cầu vào nhóm hoặc một lời mời
 * chưa trả lời cũng là một bản ghi ở đây, với {@code status = PENDING}.
 *
 * <p>Khoá chính ghép là {@code (group_id, account_id)}: thành viên nhóm là TÀI KHOẢN, cùng một loại
 * danh tính với tác giả bài viết trong nhóm. Giữ hai loại danh tính khác nhau cho hai câu hỏi đó là
 * thứ đã buộc mọi truy vấn phân quyền phải mang theo hai tham số người xem.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "group_members")
@IdClass(GroupMemberId.class)
@SQLRestriction("deletedAt is null")
public class GroupMember extends BaseEntity {

    @Id
    @Column(name = "group_id", nullable = false)
    private Integer groupId;

    @Id
    @Column(name = "account_id", nullable = false)
    private Integer accountId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", insertable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", insertable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false,
            columnDefinition = "enum('OWNER','ADMIN','MEMBER') not null default 'MEMBER'")
    private GroupMemberRole role;

    /**
     * Thời điểm tư cách thành viên BẮT ĐẦU THẬT, không phải thời điểm tạo hàng: hàng PENDING được
     * đóng dấu lại khi được duyệt hoặc được chấp nhận. Muốn biết yêu cầu gửi lúc nào thì đọc
     * {@code createdAt} của {@code BaseEntity}.
     */
    @Column(name = "joined_at", columnDefinition = "datetime not null default CURRENT_TIMESTAMP")
    private LocalDateTime joinedAt;

    /**
     * Mặc định ACTIVE để hàng cũ và hàng tạo trực tiếp (chủ nhóm lúc tạo nhóm, tự vào nhóm PUBLIC)
     * không phải khai lại — đồng thời khớp {@code default 'ACTIVE'} của cột, nên dữ liệu đã có sẵn
     * không cần backfill.
     */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false,
            columnDefinition = "enum('PENDING','ACTIVE') not null default 'ACTIVE'")
    private GroupMemberStatus status = GroupMemberStatus.ACTIVE;

    /**
     * Tài khoản đã gửi lời mời — cột phân biệt hai loại PENDING, xem {@link GroupMemberStatus}.
     *
     * <p>NULL nghĩa là không ai mời: người dùng tự xin vào nhóm PRIVATE và quản trị nhóm duyệt. Khác
     * NULL nghĩa là một thành viên đã mời, và người duyệt là chính người được mời. Nhờ vậy "ai được
     * quyền trả lời hàng này" suy ra được từ dữ liệu, không cần thêm cờ.
     *
     * <p>Lưu id thô thay vì {@code @ManyToOne Account}: người mời có thể bị xoá mềm sau đó, và
     * {@code @SQLRestriction} trên {@code Account} sẽ biến quan hệ thành null — làm mất luôn thông
     * tin "hàng này là lời mời" và biến nó thành một yêu cầu xin vào mà quản trị nhóm duyệt được.
     *
     * <p><b>Cố ý KHÔNG đặt khoá ngoại</b>, cùng lý do như {@code notifications.objectId}. Hai lựa
     * chọn còn lại đều sai: {@code ON DELETE SET NULL} gây đúng cái lệch nghĩa vừa nói, còn
     * {@code ON DELETE CASCADE} sẽ xoá cả những hàng đã thành ACTIVE — tức là đá một thành viên thật
     * ra khỏi nhóm chỉ vì người từng mời họ bị xoá. Đổi lại, giá trị ở đây có thể trỏ tới một tài
     * khoản không còn tồn tại; tầng đọc phải chịu được điều đó và chỉ dùng cột này để phân loại
     * PENDING.
     */
    @Column(name = "invited_by")
    private Integer invitedByAccountId;
}
