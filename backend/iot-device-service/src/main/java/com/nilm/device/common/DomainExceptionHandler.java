package com.nilm.device.common;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 도메인 예외 전용 핸들러 — 공통 GlobalExceptionHandler는 수정하지 않고
 * 도메인 계층의 예외만 여기서 HTTP 응답으로 변환한다.
 */
@Order(0)
@RestControllerAdvice
public class DomainExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException e, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of(
                HttpStatus.NOT_FOUND.value(), "RESOURCE_NOT_FOUND",
                e.getMessage(), request.getRequestURI(), List.of()));
    }

    @ExceptionHandler(InvalidStateTransitionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidTransition(
            InvalidStateTransitionException e, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(), "INVALID_STATE_TRANSITION",
                e.getMessage(), request.getRequestURI(), List.of()));
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ErrorResponse> handleDuplicate(
            DuplicateResourceException e, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of(
                HttpStatus.CONFLICT.value(), "DUPLICATE_RESOURCE",
                e.getMessage(), request.getRequestURI(), List.of()));
    }
}
