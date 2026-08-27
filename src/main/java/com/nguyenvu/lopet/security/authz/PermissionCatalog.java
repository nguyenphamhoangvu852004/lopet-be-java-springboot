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
            PermissionDef.of("post:create", "Đăng bài"),
            PermissionDef.of("post:update:own", "Sửa bài của chính mình"),
            PermissionDef.of("post:delete:own", "Xoá bài của chính mình"),
            PermissionDef.of("comment:create", "Bình luận"),
            PermissionDef.of("comment:delete:own", "Xoá bình luận của chính mình"),
            PermissionDef.of("group:create", "Tạo nhóm"),
            PermissionDef.of("group:update:own", "Quản trị nhóm mình làm chủ"),
            PermissionDef.of("group:delete:own", "Xoá nhóm mình làm chủ"),
            PermissionDef.of("report:create", "Gửi báo cáo"),
            PermissionDef.of("accountProfile:update:own", "Sửa hồ sơ chủ tài khoản của chính mình"),
            PermissionDef.of("friendship:manage:own", "Quản lý kết bạn của chính mình"),
            // Ba quyền ads dưới đây mới chỉ là điều kiện cần. Điều kiện đủ là tài khoản phải có
            // advertiser_profile ở trạng thái APPROVED — tầng capability kiểm.
            PermissionDef.of("advertiser:register", "Đăng ký hồ sơ nhà quảng cáo"),
            PermissionDef.of("ads:create", "Tạo quảng cáo"),
            PermissionDef.of("ads:update:own", "Sửa quảng cáo của chính mình"),
            PermissionDef.of("ads:delete:own", "Xoá quảng cáo của chính mình"));

    /** Quyền vận hành, chỉ cấp cho staff */
    public static final List<PermissionDef> STAFF_PERMISSIONS = List.of(
            PermissionDef.of("account:read", "Xem danh sách tài khoản"),
            PermissionDef.of("account:ban", "Khoá / mở khoá tài khoản"),
            PermissionDef.of("account:delete", "Xoá tài khoản"),
            PermissionDef.of("account:setRole", "Cấp / thu hồi role của tài khoản"),
            PermissionDef.of("report:read", "Xem danh sách báo cáo"),
            PermissionDef.of("report:resolve", "Xử lý báo cáo"),
            PermissionDef.of("post:delete", "Xoá bài của bất kỳ ai"),
            PermissionDef.of("group:delete", "Xoá nhóm của bất kỳ ai"),
            PermissionDef.of("ads:review", "Duyệt nội dung quảng cáo"),
            PermissionDef.of("advertiser:read", "Xem danh sách hồ sơ nhà quảng cáo"),
            PermissionDef.of("advertiser:approve", "Duyệt / đình chỉ hồ sơ nhà quảng cáo"));

    public static final List<PermissionDef> ALL_PERMISSIONS =
            Stream.concat(BASELINE_PERMISSIONS.stream(), STAFF_PERMISSIONS.stream()).toList();

    public static final Map<RoleName, List<String>> ROLE_PERMISSIONS = Map.of(
            RoleName.ADMIN, List.of(WILDCARD),
            RoleName.MODERATOR, List.of(
                    "report:read", "report:resolve", "post:delete", "group:delete",
                    "account:ban", "ads:review", "advertiser:read"),
            RoleName.SUPPORT, List.of("account:read", "report:read", "advertiser:read"));

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
