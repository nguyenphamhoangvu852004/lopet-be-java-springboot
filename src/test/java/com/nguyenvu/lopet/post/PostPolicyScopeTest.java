package com.nguyenvu.lopet.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nguyenvu.lopet.common.exception.BadRequestException;
import com.nguyenvu.lopet.group.repository.GroupMemberRepository;
import com.nguyenvu.lopet.group.repository.GroupRepository;
import com.nguyenvu.lopet.post.entity.PostScope;

/**
 * Ràng buộc scope là phần thuần logic của postPolicy nên kiểm được không cần database.
 */
@ExtendWith(MockitoExtension.class)
class PostPolicyScopeTest {

    @Mock
    private GroupRepository groupRepository;
    @Mock
    private GroupMemberRepository groupMemberRepository;

    private PostPolicy policy() {
        return new PostPolicy(groupRepository, groupMemberRepository);
    }

    @Test
    void bai_ca_nhan_nhan_du_ba_scope() {
        PostPolicy policy = policy();

        assertThat(policy.parseScope("PUBLIC", false)).isEqualTo(PostScope.PUBLIC);
        assertThat(policy.parseScope("FRIEND", false)).isEqualTo(PostScope.FRIEND);
        assertThat(policy.parseScope("PRIVATE", false)).isEqualTo(PostScope.PRIVATE);
    }

    @Test
    void bai_nhom_chi_nhan_PUBLIC_va_PRIVATE() {
        PostPolicy policy = policy();

        assertThat(policy.parseScope("PUBLIC", true)).isEqualTo(PostScope.PUBLIC);
        assertThat(policy.parseScope("PRIVATE", true)).isEqualTo(PostScope.PRIVATE);
    }

    /**
     * FRIEND bị loại khỏi bài nhóm vì "bạn bè của tác giả" không phải tập con của nhóm — cho phép nó
     * sẽ tạo ra một chiều hiển thị mà không nhánh nào của visibility diễn tả được, và bài sẽ lặng lẽ
     * biến mất khỏi mọi feed.
     */
    @Test
    void bai_nhom_khong_nhan_scope_FRIEND() {
        assertThatThrownBy(() -> policy().parseScope("FRIEND", true))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("bài nhóm")
                .hasMessageContaining("PUBLIC | PRIVATE");
    }

    @Test
    void scope_rac_bi_tu_choi() {
        assertThatThrownBy(() -> policy().parseScope("EVERYONE", false))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("bài cá nhân")
                .hasMessageContaining("PUBLIC | FRIEND | PRIVATE");
    }

    @Test
    void thieu_scope_bi_tu_choi() {
        assertThatThrownBy(() -> policy().parseScope(null, false))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void scope_phan_biet_hoa_thuong() {
        assertThatThrownBy(() -> policy().parseScope("public", false))
                .isInstanceOf(BadRequestException.class);
    }
}
