package com.nguyenvu.lopet.common.exception;

public class RawJwtException extends HttpException {

    public RawJwtException(String message) {
        super(500, message);
    }
}
