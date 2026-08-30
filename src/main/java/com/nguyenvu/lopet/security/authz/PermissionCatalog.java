package com.nguyenvu.lopet.security.authz;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import com.nguyenvu.lopet.role.entity.RoleName;


public final class PermissionCatalog {

    public static final String WILDCARD = "*";

    public static final List<PermissionDef> BASELINE_PERMISSIONS = List.of(
            PermissionDef.of("accountProfile:update:own", "Sửa hồ sơ chủ tài khoản của chính mình"),
            PermissionDef.of("friendship:manage:own", "Quản lý kết bạn của chính mình"));

    public static final List<PermissionDef> STAFF_PERMISSIONS = List.of(
            PermissionDef.of("account:read", "Xem danh sách tài khoản"),
            PermissionDef.of("account:ban", "Khoá / mở khoá tài khoản"),
            PermissionDef.of("account:delete", "Xoá tài khoản"),
            PermissionDef.of("account:setRole", "Cấp / thu hồi role của tài khoản"));

    public static final List<PermissionDef> ALL_PERMISSIONS =
            Stream.concat(BASELINE_PERMISSIONS.stream(), STAFF_PERMISSIONS.stream()).toList();

    public static final Map<RoleName, List<String>> ROLE_PERMISSIONS = Map.of(
            RoleName.ADMIN, List.of(WILDCARD),
            RoleName.MODERATOR, List.of("account:ban"),
            RoleName.SUPPORT, List.of("account:read"));

    public static Set<String> resolvePermissions(List<String> roles) {
        Set<String> granted = new HashSet<>();
        BASELINE_PERMISSIONS.forEach(permission -> granted.add(permission.code()));

        if (roles == null) {
            return granted;
        }
        for (String role : roles) {
            RoleName roleName;
            try {
                roleName = RoleName.valueOf(role);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            granted.addAll(ROLE_PERMISSIONS.getOrDefault(roleName, List.of()));
        }
        return granted;
    }

    public static boolean hasPermission(Set<String> granted, String required) {
        if (granted.contains(WILDCARD) || granted.contains(required)) {
            return true;
        }
        return granted.contains(required.split(":")[0] + ":*");
    }

    private PermissionCatalog() {
    }
}
