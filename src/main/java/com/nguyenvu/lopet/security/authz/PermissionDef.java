package com.nguyenvu.lopet.security.authz;

import com.nguyenvu.lopet.role.entity.PermissionScope;

public record PermissionDef(String code, String resource, String action, PermissionScope scope, String description) {
    public static PermissionDef of(String code, String description) {
        String[] parts = code.split(":");
        String resource = parts[0];
        String action = parts.length > 1 ? parts[1] : "";
        boolean own = parts.length > 2 && "own".equals(parts[2]);
        return new PermissionDef(code, resource, action, own ? PermissionScope.OWN : PermissionScope.ANY, description);
    }
}
