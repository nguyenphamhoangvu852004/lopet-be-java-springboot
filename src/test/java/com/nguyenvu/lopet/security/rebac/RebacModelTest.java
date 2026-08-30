package com.nguyenvu.lopet.security.rebac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Bảng luật ReBAC")
class RebacModelTest {

    @Test
    @DisplayName("MODERATOR xoá được bài của người khác — đây là bug cũ được sửa")
    void moderator_co_nhanh_xoa_bai() {
        RebacModel.Rule rule = RebacModel.rule(RebacModel.POST_DELETE);

        assertThat(rule.platformRelations()).contains(Relation.MODERATOR, Relation.ADMIN);
        assertThat(rule.objectRelations()).containsExactly(Relation.OWNER);
    }

    @Test
    @DisplayName("nhưng KHÔNG sửa được bài của người khác: kiểm duyệt thì xoá, không viết hộ")
    void khong_ai_bypass_duoc_quyen_sua_bai() {
        RebacModel.Rule rule = RebacModel.rule(RebacModel.POST_UPDATE);

        assertThat(rule.platformRelations()).isEmpty();
        assertThat(rule.objectRelations()).containsExactly(Relation.OWNER);
    }

    @Test
    @DisplayName("ADMIN không đọc được tin nhắn riêng — giữ nguyên ngữ nghĩa NO_BYPASS cũ")
    void tin_nhan_khong_co_nhanh_nen_tang() {
        RebacModel.Rule rule = RebacModel.rule(RebacModel.MESSAGE_READ);

        assertThat(rule.platformRelations()).isEmpty();
        assertThat(rule.objectRelations()).containsExactly(Relation.OWNER);
    }

    @Test
    @DisplayName("action đích platform chỉ đòi đã đăng nhập")
    void tao_bai_va_binh_luan_chi_doi_dang_nhap() {
        for (String action : new String[] {RebacModel.POST_CREATE, RebacModel.COMMENT_CREATE}) {
            RebacModel.Rule rule = RebacModel.rule(action);

            assertThat(rule.platformRelations()).containsExactly(Relation.AUTHENTICATED);
            assertThat(rule.objectRelations()).isEmpty();
            assertThat(rule.requiresAuth()).isTrue();
        }
    }

    @Test
    @DisplayName("action chưa khai báo ném ngay thay vì âm thầm từ chối")
    void action_la_khong_bi_nuot_lang_le() {
        assertThatThrownBy(() -> RebacModel.rule("post:teleport"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("post:teleport");
    }

    @Test
    @DisplayName("mọi luật đều có ít nhất một đường được phép — không có luật chết")
    void khong_co_luat_nao_khong_the_thoa() {
        for (String action : RebacModel.actions()) {
            RebacModel.Rule rule = RebacModel.rule(action);

            assertThat(rule.platformRelations().isEmpty() && rule.objectRelations().isEmpty())
                    .as("luật '%s' không có đường nào được phép, nghĩa là nó từ chối tất cả", action)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("mọi action ghi đều đòi xác thực")
    void moi_action_deu_doi_xac_thuc() {
        for (String action : RebacModel.actions()) {
            assertThat(RebacModel.rule(action).requiresAuth())
                    .as("action '%s'", action)
                    .isTrue();
        }
    }
}
