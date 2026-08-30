package com.nguyenvu.lopet.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.nguyenvu.lopet.common.exception.BadRequestException;
import com.nguyenvu.lopet.post.entity.PostScope;

class PostPolicyScopeTest {

    private PostPolicy policy() {
        return new PostPolicy();
    }

    @Test
    void bai_viet_nhan_du_ba_scope() {
        PostPolicy policy = policy();

        assertThat(policy.parseScope("PUBLIC")).isEqualTo(PostScope.PUBLIC);
        assertThat(policy.parseScope("FRIEND")).isEqualTo(PostScope.FRIEND);
        assertThat(policy.parseScope("PRIVATE")).isEqualTo(PostScope.PRIVATE);
    }

    @Test
    void scope_rac_bi_tu_choi() {
        assertThatThrownBy(() -> policy().parseScope("EVERYONE"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("PUBLIC | FRIEND | PRIVATE");
    }

    @Test
    void thieu_scope_bi_tu_choi() {
        assertThatThrownBy(() -> policy().parseScope(null))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void scope_phan_biet_hoa_thuong() {
        assertThatThrownBy(() -> policy().parseScope("public"))
                .isInstanceOf(BadRequestException.class);
    }
}
