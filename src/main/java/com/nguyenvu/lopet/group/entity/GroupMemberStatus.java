package com.nguyenvu.lopet.group.entity;

/**
 * Trạng thái của một hàng {@code group_members}. Trước đây bảng không có cột này: "tồn tại hàng"
 * đồng nghĩa "đang là thành viên", nên không có cách nào diễn tả một yêu cầu đang chờ.
 *
 * <p><b>PENDING mang hai nghĩa, phân biệt bằng {@code invited_by} chứ không bằng một giá trị enum
 * thứ ba</b> — xem {@link GroupMember#getInvitedByAccountId()}:
 *
 * <pre>
 *   PENDING + invited_by IS NULL      -> tự xin vào nhóm PRIVATE, quản trị nhóm duyệt
 *   PENDING + invited_by IS NOT NULL  -> được thành viên mời, chính người được mời duyệt
 * </pre>
 *
 * <p>Không có giá trị REJECTED: từ chối thì XOÁ hàng, đúng như rời nhóm. Nhờ vậy người bị từ chối
 * vẫn xin lại được, và mọi truy vấn chỉ phải phân biệt hai trạng thái.
 *
 * <p><b>Mọi truy vấn hỏi "người này có phải thành viên không" BẮT BUỘC lọc {@code status = ACTIVE}.</b>
 * Bỏ sót một chỗ là một lỗ riêng tư: hàng PENDING sinh ra bởi hành động tự nguyện của chính người
 * ngoài, nên coi PENDING là thành viên sẽ cho phép bất kỳ ai tự mở cửa vào nhóm PRIVATE.
 */
public enum GroupMemberStatus {
    PENDING,
    ACTIVE
}
