package com.fashtry.backend.common.aspect;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 异常日志切面：Controller 层抛出异常时，将完整异常信息输出到控制台
 */
@Aspect
@Component
public class ExceptionLoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(ExceptionLoggingAspect.class);

    @AfterThrowing(pointcut = "execution(* com.fashtry.backend.controller..*.*(..))", throwing = "ex")
    public void logException(JoinPoint joinPoint, Throwable ex) {
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        log.error("========== 异常捕获 ==========");
        log.error("类: {}.{}", className, methodName);
        log.error("异常类型: {}", ex.getClass().getName());
        log.error("异常信息: {}", ex.getMessage());
        log.error("堆栈跟踪:", ex);
        log.error("==============================");
    }
}
