package com.nguyenvu.lopet.security.authz;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Ma trận phân quyền là nơi một sai sót lặng lẽ mở toang hệ thống, nên từng nhánh của
 * {@code resolvePermissions}/{@code hasPermission} đều được chốt lại ở đây.
 */
class PermissionCatalogTest {

    @Test
    void tai_khoan_khong_role_van_co_du_quyen_baseline() {
        Set<String> granted = PermissionCatalog.resolvePermissions(List.of());

        assertThat(granted).contains("post:create", "comment:create", "group:create",
                "report:create", "accountProfile:update:own", "petProfile:update:own", "advertiser:register");
        assertThat(granted).hasSize(PermissionCatalog.BASELINE_PERMISSIONS.size());
    }

    @Test
    void user_thuong_khong_co_quyen_van_hanh() {
        Set<String> granted = PermissionCatalog.resolvePermissions(List.of());

        assertThat(PermissionCatalog.hasPermission(granted, "account:read")).isFalse();
        assertThat(PermissionCatalog.hasPermission(granted, "account:ban")).isFalse();
        assertThat(PermissionCatalog.hasPermission(granted, "account:setRole")).isFalse();
        assertThat(PermissionCatalog.hasPermission(granted, "post:delete")).isFalse();
        assertThat(PermissionCatalog.hasPermission(granted, "report:read")).isFalse();
    }

    @Test
    void admin_qua_moi_quyen_nho_wildcard() {
        Set<String> granted = PermissionCatalog.resolvePermissions(List.of("ADMIN"));

        for (PermissionDef definition : PermissionCatalog.ALL_PERMISSIONS) {
            assertThat(PermissionCatalog.hasPermission(granted, definition.code())).isTrue();
        }
        // Kể cả quyền chưa tồn tại trong danh mục
        assertThat(PermissionCatalog.hasPermission(granted, "quyen:chua:co")).isTrue();
    }

    @Test
    void moderator_kiem_duyet_duoc_nhung_khong_cap_role_duoc() {
        Set<String> granted = PermissionCatalog.resolvePermissions(List.of("MODERATOR"));

        assertThat(PermissionCatalog.hasPermission(granted, "post:delete")).isTrue();
        assertThat(PermissionCatalog.hasPermission(granted, "group:delete")).isTrue();
        assertThat(PermissionCatalog.hasPermission(granted, "report:resolve")).isTrue();
        assertThat(PermissionCatalog.hasPermission(granted, "account:ban")).isTrue();

        // Đây là ranh giới quan trọng nhất: MODERATOR không được tự nâng quyền
        assertThat(PermissionCatalog.hasPermission(granted, "account:setRole")).isFalse();
        assertThat(PermissionCatalog.hasPermission(granted, "account:delete")).isFalse();
        assertThat(PermissionCatalog.hasPermission(granted, "advertiser:approve")).isFalse();
    }

    @Test
    void support_chi_doc_khong_ghi() {
        Set<String> granted = PermissionCatalog.resolvePermissions(List.of("SUPPORT"));

        assertThat(PermissionCatalog.hasPermission(granted, "account:read")).isTrue();
        assertThat(PermissionCatalog.hasPermission(granted, "report:read")).isTrue();
        assertThat(PermissionCatalog.hasPermission(granted, "advertiser:read")).isTrue();

        assertThat(PermissionCatalog.hasPermission(granted, "account:ban")).isFalse();
        assertThat(PermissionCatalog.hasPermission(granted, "report:resolve")).isFalse();
        assertThat(PermissionCatalog.hasPermission(granted, "post:delete")).isFalse();
    }

    @Test
    void role_la_khong_lam_mat_quyen_baseline() {
        // Token cũ mang role đã bị bỏ (ví dụ 'ADS') chỉ đơn giản là không cấp thêm gì
        Set<String> granted = PermissionCatalog.resolvePermissions(List.of("ADS", "USER"));

        assertThat(PermissionCatalog.hasPermission(granted, "post:create")).isTrue();
        assertThat(PermissionCatalog.hasPermission(granted, "account:read")).isFalse();
    }

    @Test
    void wildcard_theo_tai_nguyen_duoc_chap_nhan() {
        assertThat(PermissionCatalog.hasPermission(Set.of("post:*"), "post:delete")).isTrue();
        assertThat(PermissionCatalog.hasPermission(Set.of("post:*"), "account:delete")).isFalse();
    }

    @Test
    void danh_muc_khong_co_ma_trung_lap() {
        assertThat(PermissionCatalog.ALL_PERMISSIONS.stream().map(PermissionDef::code).distinct().count())
                .isEqualTo(PermissionCatalog.ALL_PERMISSIONS.size());
    }

    @Test
    void hau_to_own_duoc_ghi_nhan_thanh_scope_OWN() {
        PermissionDef own = PermissionCatalog.BASELINE_PERMISSIONS.stream()
                .filter(definition -> definition.code().equals("post:update:own"))
                .findFirst().orElseThrow();

        assertThat(own.resource()).isEqualTo("post");
        assertThat(own.action()).isEqualTo("update");
        assertThat(own.scope().name()).isEqualTo("OWN");
    }
}
