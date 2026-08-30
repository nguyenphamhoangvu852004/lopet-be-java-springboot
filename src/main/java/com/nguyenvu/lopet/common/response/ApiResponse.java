package com.nguyenvu.lopet.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;

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
