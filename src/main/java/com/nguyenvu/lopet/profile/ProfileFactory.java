package com.nguyenvu.lopet.profile;

import com.nguyenvu.lopet.profile.entity.Profile;

/**
 * Nguồn duy nhất sinh row {@code profiles} lúc tài khoản được tạo.
 *
 * <p>Người dùng KHÔNG còn tự tạo hồ sơ: mô hình cũ (tạo hồ sơ rời rồi gắn vào tài khoản bằng một
 * request thứ hai) cho phép gắn hồ sơ của người khác vào tài khoản mình, và đẻ ra hồ sơ mồ côi
 * vĩnh viễn không sửa được. Cấp sẵn một hồ sơ ngay khi đăng ký xoá cả hai trạng thái đó.
 *
 * <p>Có HAI đường tạo {@link com.nguyenvu.lopet.account.entity.Account} —
 * {@link com.nguyenvu.lopet.auth.AuthService#register} và
 * {@link com.nguyenvu.lopet.bootstrap.AdminInitializer#init} — nên giá trị seed phải nằm ở một chỗ,
 * nếu không hai đường sẽ trôi khác nhau. Đây là knob duy nhất để chỉnh dữ liệu khởi tạo.
 */
public final class ProfileFactory {

    /**
     * {@code fullName} lấy theo username thay vì một cái tên bịa: tên bịa sẽ lọt vào kết quả của
     * {@code GET /v1/profiles?fullName=} và hiển thị y như tên thật ở danh sách bạn bè, bình luận.
     *
     * <p>Các trường chuỗi còn lại để rỗng chứ không để null — {@code ProfileService} vốn đã chuẩn
     * hoá bằng chuỗi rỗng, và client cũ dựa vào việc các khoá này luôn tồn tại (xem
     * {@code CommentDtos.CommentProfile}). Riêng {@code sex} và {@code dateOfBirth} giữ null vì
     * không có giá trị mặc định nào đúng.
     */
    public static Profile seedFor(String username) {
        return Profile.builder()
                .fullName(username)
                .bio("Xin chào, mình là " + username)
                .phoneNumber("")
                .hometown("")
                .avatarUrl("")
                .coverUrl("")
                .sex(null)
                .dateOfBirth(null)
                .build();
    }

    private ProfileFactory() {
    }
}
