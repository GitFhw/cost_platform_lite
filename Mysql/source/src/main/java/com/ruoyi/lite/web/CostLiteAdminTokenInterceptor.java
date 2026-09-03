package com.ruoyi.lite.web;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.lite.config.CostLiteProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 可选的轻量管理接口令牌校验。
 */
@Component
public class CostLiteAdminTokenInterceptor implements HandlerInterceptor {
    private static final String TOKEN_HEADER = "X-Cost-Lite-Token";
    private final CostLiteProperties properties;

    public CostLiteAdminTokenInterceptor(CostLiteProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!properties.isAuthEnabled()) {
            return true;
        }
        String configured = trim(properties.getAdminToken());
        String presented = trim(request.getHeader(TOKEN_HEADER));
        if (presented.isEmpty()) {
            presented = extractBearer(request.getHeader("Authorization"));
        }
        if (!configured.isEmpty() && constantTimeEquals(configured, presented)) {
            return true;
        }
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(JSON.toJSONString(
                AjaxResult.error(HttpStatus.UNAUTHORIZED, "轻量管理接口令牌无效，请配置 X-Cost-Lite-Token")));
        return false;
    }

    private String extractBearer(String authorization) {
        String value = trim(authorization);
        if (value.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return trim(value.substring(7));
        }
        return value;
    }

    private boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
