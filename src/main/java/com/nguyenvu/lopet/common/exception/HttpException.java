package com.nguyenvu.lopet.common.exception;

import lombok.Getter;

/**
 * Tương đương {@code HttpError} của lopet-be: mang sẵn mã HTTP để handler trung tâm khỏi phải đoán.
 */
@Getter
public class HttpException extends RuntimeException {

    private final int code;

    public HttpException(int code, String message) {
        super(message);
        this.code = code;
    }
}
