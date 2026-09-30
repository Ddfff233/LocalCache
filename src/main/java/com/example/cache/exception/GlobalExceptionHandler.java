package com.example.cache.exception;

import com.example.cache.domain.R;
import com.example.cache.exception.custom.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.messaging.handler.annotation.support.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<R<Void>> handleBusinessException(BusinessException exception) {
        return ResponseEntity.status(resolveStatus(exception.getCode()))
                .body(R.failure(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<R<Void>> handleBadRequest(Exception exception) {
        return ResponseEntity.badRequest().body(R.failure(40001, "请求参数格式错误"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<R<Void>> handleUnknownException(Exception exception) {
        log.error("未处理的系统异常", exception);
        return ResponseEntity.internalServerError().body(R.failure(50000, "系统内部错误"));
    }

    private HttpStatus resolveStatus(int code) {
        return switch (code) {
            case 40001 -> HttpStatus.BAD_REQUEST;
            case 40401, 40402 -> HttpStatus.NOT_FOUND;
            case 40901, 40902 -> HttpStatus.CONFLICT;
            case 42901 -> HttpStatus.TOO_MANY_REQUESTS;
            case 50301 -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
