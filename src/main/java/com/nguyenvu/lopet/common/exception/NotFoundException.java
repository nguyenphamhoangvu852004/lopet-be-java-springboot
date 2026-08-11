package com.nguyenvu.lopet.common.exception;

import com.nguyenvu.lopet.common.response.HttpStatusMessage;

public class NotFoundException extends HttpException {

    public NotFoundException() {
        this(HttpStatusMessage.NOT_FOUND);
    }

    public NotFoundException(String message) {
        super(404, message);
    }
}
