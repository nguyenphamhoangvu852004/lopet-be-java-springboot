package com.nguyenvu.lopet.common.exception;

import com.nguyenvu.lopet.common.response.HttpStatusMessage;

public class UnauthorizedException extends HttpException {

    public UnauthorizedException() {
        this(HttpStatusMessage.UNAUTHORIZED);
    }

    public UnauthorizedException(String message) {
        super(401, message);
    }
}
