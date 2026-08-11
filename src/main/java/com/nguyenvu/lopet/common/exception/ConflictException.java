package com.nguyenvu.lopet.common.exception;

import com.nguyenvu.lopet.common.response.HttpStatusMessage;

public class ConflictException extends HttpException {

    public ConflictException() {
        this(HttpStatusMessage.CONFLICT);
    }

    public ConflictException(String message) {
        super(409, message);
    }
}
