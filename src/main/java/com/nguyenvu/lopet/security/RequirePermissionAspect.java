package com.nguyenvu.lopet.security;

import java.util.Arrays;
import java.util.Set;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

import com.nguyenvu.lopet.common.exception.ForbiddenException;
import com.nguyenvu.lopet.security.authz.PermissionCatalog;
import com.nguyenvu.lopet.security.jwt.UserPrincipal;

@Aspect
@Component
public class RequirePermissionAspect {

    @Before("@annotation(requirePermission)")
    public void check(RequirePermission requirePermission) {
        UserPrincipal principal = CurrentUser.optional();
        if (principal == null) {
            throw new ForbiddenException("Chưa xác thực");
        }

        Set<String> granted = PermissionCatalog.resolvePermissions(principal.roles());
        boolean allowed = Arrays.stream(requirePermission.value())
                .anyMatch(permission -> PermissionCatalog.hasPermission(granted, permission));

        if (!allowed) {
            throw new ForbiddenException("Thiếu quyền: " + String.join(" hoặc ", requirePermission.value()));
        }
    }
}
