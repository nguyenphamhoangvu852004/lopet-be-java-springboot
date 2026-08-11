package com.nguyenvu.lopet.common.exception;

import java.util.List;

/**
 * Envelope thứ ba của lopet-be — do {@code validate()} middleware trả về trực tiếp, không đi qua
 * exception handler: {@code {statusCode, message:"Validation error", errors:[{field, message}]}}.
 */
public record ValidationErrorResponse(int statusCode, String message, List<FieldError> errors) {

    public record FieldError(String field, String message) {
    }
}
