package com.nguyenvu.lopet.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Envelope thành công, khớp từng ký tự với {@code sendResponse()} của lopet-be:
 *
 * <pre>{ "statusCode": 200, "message": "...", "data": ... }</pre>
 *
 * {@code data} được bỏ qua khi null vì bên TS các endpoint như POST /v1/emails gán
 * {@code data: undefined} — JSON.stringify loại bỏ khoá đó khỏi body. Trả về {@code "data": null}
 * sẽ là một khoá lạ mà client cũ chưa từng thấy.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(int statusCode, String message, T data) {

    public static <T> ApiResponse<T> of(int statusCode, String message, T data) {
        return new ApiResponse<>(statusCode, message, data);
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(200, message, data);
    }

    public static <T> ApiResponse<T> created(String message, T data) {
        return new ApiResponse<>(201, message, data);
    }

    public static ApiResponse<Void> message(int statusCode, String message) {
        return new ApiResponse<>(statusCode, message, null);
    }
}
