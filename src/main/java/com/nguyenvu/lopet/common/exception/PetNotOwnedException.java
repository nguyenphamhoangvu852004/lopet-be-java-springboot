package com.nguyenvu.lopet.common.exception;

/**
 * Header {@code X-Pet-Id} trỏ tới một thú cưng không thuộc tài khoản trong JWT (hoặc không tồn tại,
 * hoặc đã ngừng hoạt động).
 *
 * <p>Ba trường hợp CỐ Ý trả về cùng một phản hồi: phân biệt "không tồn tại" với "của người khác" là
 * đủ để quét id và biết pet nào đang tồn tại — cùng lý do với {@code PetAccessGuard}.
 */
public class PetNotOwnedException extends HttpException {

    public PetNotOwnedException() {
        super(403, "Thú cưng không tồn tại hoặc không thuộc tài khoản của bạn");
    }
}
