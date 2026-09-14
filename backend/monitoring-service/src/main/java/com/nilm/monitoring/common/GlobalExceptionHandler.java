package com.nilm.monitoring.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e, HttpServletRequest request) {
        List<ErrorResponse.FieldErrorDetail> errors = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ErrorResponse.FieldErrorDetail(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest().body(ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(), "VALIDATION_ERROR",
                "입력값 검증에 실패했습니다.", request.getRequestURI(), errors));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException e, HttpServletRequest request) {
        List<ErrorResponse.FieldErrorDetail> errors = e.getConstraintViolations().stream()
                .map(v -> new ErrorResponse.FieldErrorDetail(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return ResponseEntity.badRequest().body(ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(), "VALIDATION_ERROR",
                "입력값 검증에 실패했습니다.", request.getRequestURI(), errors));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleHandlerMethodValidation(
            HandlerMethodValidationException e, HttpServletRequest request) {
        List<ErrorResponse.FieldErrorDetail> errors = e.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new ErrorResponse.FieldErrorDetail(
                                result.getMethodParameter().getParameterName(), error.getDefaultMessage())))
                .toList();
        return ResponseEntity.badRequest().body(ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(), "VALIDATION_ERROR",
                "입력값 검증에 실패했습니다.", request.getRequestURI(), errors));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            BadRequestException.class,
            IllegalArgumentException.class})
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception e, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(), "VALIDATION_ERROR",
                "입력값 검증에 실패했습니다.", request.getRequestURI(), List.of()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(
            NoResourceFoundException e, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of(
                HttpStatus.NOT_FOUND.value(), "NOT_FOUND",
                "요청한 리소스를 찾을 수 없습니다.", request.getRequestURI(), List.of()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(
            ResourceNotFoundException e, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of(
                HttpStatus.NOT_FOUND.value(), "NOT_FOUND", e.getMessage(),
                request.getRequestURI(), List.of()));
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(
            ForbiddenException e, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorResponse.of(
                HttpStatus.FORBIDDEN.value(), "FORBIDDEN", e.getMessage(),
                request.getRequestURI(), List.of()));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(
            ConflictException e, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of(
                HttpStatus.CONFLICT.value(), "CONFLICT", e.getMessage(),
                request.getRequestURI(), List.of()));
    }

    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, DataIntegrityViolationException.class})
    public ResponseEntity<ErrorResponse> handlePersistenceConflict(
            RuntimeException e, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of(
                HttpStatus.CONFLICT.value(), "CONFLICT", "다른 요청과 충돌했습니다.",
                request.getRequestURI(), List.of()));
    }

    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleServiceUnavailable(
            ServiceUnavailableException e, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ErrorResponse.of(
                HttpStatus.SERVICE_UNAVAILABLE.value(), "SERVICE_UNAVAILABLE", e.getMessage(),
                request.getRequestURI(), List.of()));
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ErrorResponse> handleTooManyRequests(
            TooManyRequestsException e, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ErrorResponse.of(
                HttpStatus.TOO_MANY_REQUESTS.value(), "TOO_MANY_REQUESTS", e.getMessage(),
                request.getRequestURI(), List.of()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e, HttpServletRequest request) {
        log.error("Unexpected error at {}", request.getRequestURI(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErrorResponse.of(
                HttpStatus.INTERNAL_SERVER_ERROR.value(), "INTERNAL_SERVER_ERROR",
                "서버 내부 오류가 발생했습니다.", request.getRequestURI(), List.of()));
    }
}
