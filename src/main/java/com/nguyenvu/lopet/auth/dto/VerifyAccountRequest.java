package com.nguyenvu.lopet.auth.dto;

/** POST /v1/auth/verify KHÔNG có schema Joi bên TS — không thêm ràng buộc mới ở đây. */
public record VerifyAccountRequest(String email, String password) {
}
