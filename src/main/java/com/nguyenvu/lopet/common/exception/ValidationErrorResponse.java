package com.nguyenvu.lopet.common.exception;

import java.util.List;

public record ValidationErrorResponse(int statusCode, String message, List<FieldError> errors) {

    public record FieldError(String field, String message) {
    }
}
