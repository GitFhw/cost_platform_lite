package com.ruoyi.lite.web;

import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.lite.config.CostLiteProperties;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Collections;

/**
 * 为母体服务补充轻量宿主的固定操作人上下文。
 *
 * <p>母体服务仍然使用 SecurityUtils 读取审计操作人；这里不改变母体实体和服务契约，
 * 只在没有完整若依登录会话时提供一个受配置控制的实施账号。</p>
 */
@Component
public class CostLiteOperatorInterceptor implements HandlerInterceptor {
    private static final String INSTALLED_ATTRIBUTE = CostLiteOperatorInterceptor.class.getName() + ".installed";
    private final CostLiteProperties properties;

    public CostLiteOperatorInterceptor(CostLiteProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Object principal = SecurityContextHolder.getContext().getAuthentication() == null
                ? null : SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof LoginUser) {
            return true;
        }

        SysUser user = new SysUser();
        user.setUserId(1L);
        user.setUserName(resolveOperator());
        user.setNickName(user.getUserName());
        LoginUser loginUser = new LoginUser(user, Collections.singleton("*:*:*"));
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(loginUser, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        request.setAttribute(INSTALLED_ATTRIBUTE, Boolean.TRUE);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        if (Boolean.TRUE.equals(request.getAttribute(INSTALLED_ATTRIBUTE))) {
            SecurityContextHolder.clearContext();
        }
    }

    private String resolveOperator() {
        String operator = properties.getOperator();
        return operator == null || operator.trim().isEmpty() ? "lite-admin" : operator.trim();
    }
}
