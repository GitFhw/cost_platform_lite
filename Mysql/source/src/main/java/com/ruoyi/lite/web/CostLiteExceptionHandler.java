package com.ruoyi.lite.web;

import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.lite.service.CostLiteBillingException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 轻量接口统一错误响应。
 */
@RestControllerAdvice
public class CostLiteExceptionHandler {
    @ExceptionHandler(CostLiteBillingException.class)
    public AjaxResult handleBilling(CostLiteBillingException exception) {
        AjaxResult result = AjaxResult.error(exception.getCode() == null ? HttpStatus.ERROR : exception.getCode(),
                exception.getMessage());
        result.put("billingLogId", exception.getBillingLogId());
        return result;
    }

    @ExceptionHandler(ServiceException.class)
    public AjaxResult handleService(ServiceException exception) {
        int code = exception.getCode() == null ? HttpStatus.ERROR : exception.getCode();
        return AjaxResult.error(code, safeMessage(exception.getMessage(), "业务处理失败"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public AjaxResult handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + "：" + error.getDefaultMessage())
                .collect(Collectors.joining("；"));
        return AjaxResult.error(HttpStatus.BAD_REQUEST, safeMessage(message, "请求参数校验失败"));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public AjaxResult handleConstraint(ConstraintViolationException exception) {
        return AjaxResult.error(HttpStatus.BAD_REQUEST, safeMessage(exception.getMessage(), "请求参数校验失败"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public AjaxResult handleUnreadable(HttpMessageNotReadableException exception) {
        return AjaxResult.error(HttpStatus.BAD_REQUEST, "请求体不是合法 JSON，请检查字段格式");
    }

    @ExceptionHandler(Exception.class)
    public AjaxResult handleOther(Exception exception) {
        return AjaxResult.error(HttpStatus.ERROR, safeMessage(exception.getMessage(), "系统处理失败，请查看服务日志"));
    }

    private String safeMessage(String message, String fallback) {
        return message == null || message.isBlank() ? fallback : message;
    }
}
