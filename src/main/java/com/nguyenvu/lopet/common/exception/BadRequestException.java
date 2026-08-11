package com.nguyenvu.lopet.common.exception;

import com.nguyenvu.lopet.common.response.HttpStatusMessage;

public class BadRequestException extends HttpException {

    public BadRequestException() {
        this(HttpStatusMessage.BAD_REQUEST);
    }

    public BadRequestException(String message) {
        super(400, message);
    }
}
