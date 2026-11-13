package com.fashtry.backend.common.exception;

import com.fashtry.backend.bean.vo.Result;
import com.fashtry.backend.common.enums.ResultCode;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理
 * - 对客户端返回统一、安全的错误信息，不暴露内部异常细节
 * - 完整异常信息输出到控制台便于排查
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException ex) {
        log.warn("[业务异常] code={}, message={}", ex.getCode(), ex.getMessage());
        return Result.error(ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class, ConstraintViolationException.class})
    public Result<Void> handleValidationException(Exception ex) {
        String detail = ex.getMessage();
        log.warn("[参数校验失败] {}", detail);
        return Result.error(ResultCode.PARAM_INVALID, "参数校验失败");
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception ex) {
        log.error("[系统异常] {}", ex.getMessage(), ex);
        return Result.error(ResultCode.SYSTEM_ERROR);
    }
}
