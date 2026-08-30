package com.nguyenvu.lopet.common.exception;

import lombok.Getter;

@Getter
public class HttpException extends RuntimeException {

    private final int code;

    public HttpException(int code, String message) {
        super(message);
        this.code = code;
    }
}
