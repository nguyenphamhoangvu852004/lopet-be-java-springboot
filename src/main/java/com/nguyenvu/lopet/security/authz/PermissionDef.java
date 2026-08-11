package com.nguyenvu.lopet.security.authz;

import com.nguyenvu.lopet.role.entity.PermissionScope;

/**
 * Quy ước mã: {@code resource:action} hoặc {@code resource:action:own}. Hậu tố {@code :own} nghĩa là
 * qua được tầng permission vẫn CHƯA đủ — còn phải qua tầng ownership mới được đụng vào tài nguyên
 * cụ thể.
 */
public record PermissionDef(String code, String resource, String action, PermissionScope scope, String description) {

    public static PermissionDef of(String code, String description) {
        String[] parts = code.split(":");
        String resource = parts[0];
        String action = parts.length > 1 ? parts[1] : "";
        boolean own = parts.length > 2 && "own".equals(parts[2]);
        return new PermissionDef(code, resource, action, own ? PermissionScope.OWN : PermissionScope.ANY, description);
    }
}
