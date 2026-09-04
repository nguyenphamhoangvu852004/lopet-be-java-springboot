package com.nguyenvu.lopet.common.exception;

import com.nguyenvu.lopet.common.response.HttpStatusMessage;

public class ServiceUnavailableException extends HttpException {

    public ServiceUnavailableException() {
        this(HttpStatusMessage.SERVICE_UNAVAILABLE);
    }

    public ServiceUnavailableException(String message) {
        super(503, message);
    }
}
