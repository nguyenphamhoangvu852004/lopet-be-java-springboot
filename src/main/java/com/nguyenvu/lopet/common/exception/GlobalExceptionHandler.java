package com.nguyenvu.lopet.common.exception;

import java.util.Comparator;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.nguyenvu.lopet.common.response.HttpStatusMessage;

import lombok.extern.slf4j.Slf4j;

/**
 * Điểm xử lý lỗi tập trung, thay cho {@code globalExceptionMiddleware} của Express.
 *
 * <p>Ba dạng body được giữ nguyên như backend cũ:
 * <ol>
 *   <li>lỗi nghiệp vụ  → {@code {statusCode, message}}</li>
 *   <li>lỗi validation → {@code {statusCode:400, message:"Validation error", errors:[…]}}</li>
 *   <li>lỗi ngoài dự kiến → {@code {statusCode:500, message:<message gốc>}} — TS đọc
 *       {@code error.code ?? 500} nên mọi {@code new Error(...)} đều rơi vào nhánh này kèm nguyên
 *       văn message, chứ không bị che thành "INTERNAL SERVER ERROR".</li>
 * </ol>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(HttpException.class)
    public ResponseEntity<ErrorResponse> handleHttpException(HttpException exception) {
        return ResponseEntity.status(exception.getCode())
                .body(new ErrorResponse(exception.getCode(), exception.getMessage()));
    }

    /**
     * Sắp xếp theo tên field để danh sách lỗi ổn định giữa các lần chạy. Joi dùng thứ tự khai báo
     * trong schema; Bean Validation không bảo đảm thứ tự nào cả, nên cần một quy tắc tất định thay
     * thế — nội dung từng phần tử vẫn giống hệt.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        List<ValidationErrorResponse.FieldError> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new ValidationErrorResponse.FieldError(error.getField(), error.getDefaultMessage()))
                .sorted(Comparator.comparing(ValidationErrorResponse.FieldError::field)
                        .thenComparing(ValidationErrorResponse.FieldError::message))
                .toList();

        return ResponseEntity.status(400)
                .body(new ValidationErrorResponse(400, "Validation error", errors));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception exception) {
        log.error("Unhandled exception", exception);
        String message = exception.getMessage() != null
                ? exception.getMessage()
                : HttpStatusMessage.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(500).body(new ErrorResponse(500, message));
    }
}
