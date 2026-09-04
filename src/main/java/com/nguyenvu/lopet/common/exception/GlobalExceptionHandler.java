package com.nguyenvu.lopet.common.exception;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.anthropic.errors.AnthropicException;
import com.nguyenvu.lopet.common.response.HttpStatusMessage;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(HttpException.class)
    public ResponseEntity<ErrorResponse> handleHttpException(HttpException exception) {
        return ResponseEntity.status(exception.getCode())
                .body(new ErrorResponse(exception.getCode(), exception.getMessage()));
    }

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

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException exception) {
        String supported = exception.getSupportedMediaTypes().stream()
                .map(MediaType::toString)
                .collect(Collectors.joining(", "));
        String message = supported.isEmpty()
                ? exception.getMessage()
                : exception.getMessage() + ". Endpoint only accepts: " + supported;
        return ResponseEntity.status(415).body(new ErrorResponse(415, message));
    }

    @ExceptionHandler({RedisConnectionFailureException.class, RedisSystemException.class})
    public ResponseEntity<ErrorResponse> handleRedisFailure(RuntimeException exception) {
        log.error("Redis failure", exception);
        return ResponseEntity.status(503)
                .body(new ErrorResponse(503, "The cache backend is temporarily unavailable, please try again later"));
    }

    @ExceptionHandler(AnthropicException.class)
    public ResponseEntity<ErrorResponse> handleAnthropicFailure(AnthropicException exception) {
        log.error("Anthropic call failed", exception);
        return ResponseEntity.status(503)
                .body(new ErrorResponse(503, "The assistant is not responding, please try again later"));
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
