package com.nguyenvu.lopet.security;

import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import com.nguyenvu.lopet.common.exception.UnauthorizedException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        boolean required = handlerMethod.hasMethodAnnotation(Auth.class)
                || handlerMethod.getBeanType().isAnnotationPresent(Auth.class);

        if (required && CurrentUser.optional() == null) {
            throw new UnauthorizedException("Authentication required");
        }
        return true;
    }
}
