package com.nguyenvu.lopet.security.authz;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Danh mục permission (RBAC kế thừa)")
class PermissionCatalogTest {

    @Test
    @DisplayName("tài khoản không có role nào vẫn được cấp đủ tập baseline")
    void tai_khoan_khong_role_van_co_du_quyen_baseline() {
        Set<String> granted = PermissionCatalog.resolvePermissions(List.of());

        assertThat(granted).containsExactlyInAnyOrder(
                "accountProfile:update:own", "friendship:manage:own");
        assertThat(granted).hasSize(PermissionCatalog.BASELINE_PERMISSIONS.size());
    }

    @Test
    @DisplayName("user thường không chạm được quyền vận hành nào")
    void user_thuong_khong_co_quyen_van_hanh() {
        Set<String> granted = PermissionCatalog.resolvePermissions(List.of());

        for (PermissionDef staff : PermissionCatalog.STAFF_PERMISSIONS) {
            assertThat(PermissionCatalog.hasPermission(granted, staff.code()))
                    .as("user thường KHÔNG được có %s", staff.code())
                    .isFalse();
        }
    }

    @Test
    @DisplayName("ADMIN qua mọi quyền nhờ wildcard, kể cả quyền chưa tồn tại")
    void admin_qua_moi_quyen_nho_wildcard() {
        Set<String> granted = PermissionCatalog.resolvePermissions(List.of("ADMIN"));

        for (PermissionDef definition : PermissionCatalog.ALL_PERMISSIONS) {
            assertThat(PermissionCatalog.hasPermission(granted, definition.code())).isTrue();
        }
        assertThat(PermissionCatalog.hasPermission(granted, "quyen:chua:co")).isTrue();
    }

    @Test
    @DisplayName("MODERATOR khoá được tài khoản nhưng không tự nâng quyền")
    void moderator_ban_duoc_nhung_khong_cap_role_duoc() {
        Set<String> granted = PermissionCatalog.resolvePermissions(List.of("MODERATOR"));

        assertThat(PermissionCatalog.hasPermission(granted, "account:ban")).isTrue();

        assertThat(PermissionCatalog.hasPermission(granted, "account:setRole")).isFalse();
        assertThat(PermissionCatalog.hasPermission(granted, "account:delete")).isFalse();
        assertThat(PermissionCatalog.hasPermission(granted, "account:read")).isFalse();
    }

    @Test
    @DisplayName("quyền gỡ bài của MODERATOR KHÔNG còn ở đây — nó là một nhánh trong RebacModel")
    void quyen_kiem_duyet_noi_dung_da_chuyen_sang_rebac() {
        assertThat(PermissionCatalog.ALL_PERMISSIONS.stream()
                .noneMatch(definition -> definition.code().startsWith("post:")
                        || definition.code().startsWith("comment:"))).isTrue();
    }

    @Test
    @DisplayName("mã của bốn module đã gỡ không còn sót trong danh mục")
    void ma_cua_module_da_go_khong_con_sot() {
        for (String daGo : List.of("group:", "ads:", "advertiser:", "report:")) {
            assertThat(PermissionCatalog.ALL_PERMISSIONS.stream()
                    .noneMatch(definition -> definition.code().startsWith(daGo)))
                    .as("còn sót mã %s trong danh mục", daGo)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("SUPPORT chỉ đọc, không ghi")
    void support_chi_doc_khong_ghi() {
        Set<String> granted = PermissionCatalog.resolvePermissions(List.of("SUPPORT"));

        assertThat(PermissionCatalog.hasPermission(granted, "account:read")).isTrue();

        assertThat(PermissionCatalog.hasPermission(granted, "account:ban")).isFalse();
        assertThat(PermissionCatalog.hasPermission(granted, "account:delete")).isFalse();
        assertThat(PermissionCatalog.hasPermission(granted, "account:setRole")).isFalse();
    }

    @Test
    @DisplayName("role lạ trong token cũ không làm mất quyền baseline")
    void role_la_khong_lam_mat_quyen_baseline() {
        Set<String> granted = PermissionCatalog.resolvePermissions(List.of("ADS", "USER"));

        assertThat(PermissionCatalog.hasPermission(granted, "accountProfile:update:own")).isTrue();
        assertThat(PermissionCatalog.hasPermission(granted, "account:read")).isFalse();
    }

    @Test
    @DisplayName("wildcard theo tài nguyên được chấp nhận, nhưng không lan sang tài nguyên khác")
    void wildcard_theo_tai_nguyen_duoc_chap_nhan() {
        assertThat(PermissionCatalog.hasPermission(Set.of("account:*"), "account:delete")).isTrue();
        assertThat(PermissionCatalog.hasPermission(Set.of("account:*"), "friendship:manage:own")).isFalse();
    }

    @Test
    @DisplayName("danh mục không có mã trùng lặp")
    void danh_muc_khong_co_ma_trung_lap() {
        assertThat(PermissionCatalog.ALL_PERMISSIONS.stream().map(PermissionDef::code).distinct().count())
                .isEqualTo(PermissionCatalog.ALL_PERMISSIONS.size());
    }

    @Test
    @DisplayName("hậu tố :own được ghi nhận thành scope OWN")
    void hau_to_own_duoc_ghi_nhan_thanh_scope_OWN() {
        PermissionDef own = PermissionCatalog.BASELINE_PERMISSIONS.stream()
                .filter(definition -> definition.code().equals("accountProfile:update:own"))
                .findFirst().orElseThrow();

        assertThat(own.resource()).isEqualTo("accountProfile");
        assertThat(own.action()).isEqualTo("update");
        assertThat(own.scope().name()).isEqualTo("OWN");
    }
}
