package com.nguyenvu.lopet.common.exception;

/**
 * Endpoint có {@code @RequirePet} nhưng request không mang header {@code X-Pet-Id}.
 *
 * <p>400 chứ không phải 403: đây là request DỊ DẠNG, không phải request bị từ chối. Trả 403 sẽ khiến
 * client tưởng token của mình hết hiệu lực và đá người dùng ra màn hình đăng nhập, trong khi lỗi
 * thật chỉ là thiếu một header.
 */
public class MissingPetHeaderException extends HttpException {

    public MissingPetHeaderException() {
        super(400, "Thiếu header X-Pet-Id — mọi hành động trên mạng xã hội đều nhân danh một thú cưng");
    }
}
