package com.nguyenvu.lopet.common.exception;

import com.nguyenvu.lopet.common.response.HttpStatusMessage;

public class ForbiddenException extends HttpException {

    public ForbiddenException() {
        this(HttpStatusMessage.FORBIDDEN);
    }

    public ForbiddenException(String message) {
        super(403, message);
    }
}
