package com.nguyenvu.lopet.common.exception;

/**
 * Tài khoản chưa sở hữu thú cưng nào nhưng đang gọi một API tương tác.
 *
 * <p>Quy tắc nghiệp vụ của mô hình mới: mạng xã hội này lấy PET làm thực thể hoạt động, nên một
 * tài khoản không có pet thì không có tư cách nào để đăng bài, bình luận hay theo dõi. Chặn ở tầng
 * use-case chứ không phải chỉ ẩn nút trên frontend.
 *
 * <p>403 kèm thông điệp riêng, tách khỏi {@link ForbiddenException} chung, vì đây là trạng thái mà
 * client SỬA ĐƯỢC: hướng người dùng sang luồng tạo thú cưng thay vì báo "không có quyền".
 */
public class NoPetOwnedException extends HttpException {

    public NoPetOwnedException() {
        super(403, "Tài khoản chưa có thú cưng nào — hãy tạo một hồ sơ thú cưng trước khi tương tác");
    }
}
