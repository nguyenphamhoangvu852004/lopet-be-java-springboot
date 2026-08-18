package com.nguyenvu.lopet.post;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.nguyenvu.lopet.post.entity.Post;
import com.nguyenvu.lopet.post.repository.PostRepository;
import com.nguyenvu.lopet.security.CurrentUser;
import com.nguyenvu.lopet.security.authz.OwnershipGuard;
import com.nguyenvu.lopet.security.petcontext.PetContext;

import lombok.RequiredArgsConstructor;

/**
 * Nạp bài cho các kiểm tra sở hữu, ĐÃ lọc theo quyền xem của người gọi.
 *
 * <p>Cố ý dùng bản có lọc chứ không phải bản nạp thô: guard trả 404 khi không nạp được và 403 khi
 * có tài nguyên nhưng không phải chủ. Nếu nạp bằng bản không lọc thì hai lỗi đó phân biệt được
 * "bài này tồn tại nhưng không phải của bạn" với "không có bài nào" — đủ để quét id và biết bài
 * PRIVATE nào đang tồn tại, dù không đọc được nội dung.
 *
 * <p>Chủ bài luôn tự xem được bài mình (nhánh C và E của visibility) nên bộ lọc không bao giờ chặn
 * nhầm người có quyền thật, kể cả khi họ đang thao tác nhân danh một thú cưng khác của mình; staff
 * kiểm duyệt cũng không ảnh hưởng vì guard xét bypass TRƯỚC khi nạp.
 *
 * <p>Quyền sở hữu so ở mức TÀI KHOẢN ({@code OwnershipGuard} so với {@code caller.id()} của token),
 * nên chủ bài là chủ của pet tác giả. Nếu so theo pet thì mỗi lần người dùng đổi con đang thao tác
 * là mất quyền sửa/xoá nội dung của chính mình.
 *
 * <p>Là bean riêng chứ không phải method trong controller: {@code @Transactional} chỉ có tác dụng
 * khi lời gọi đi qua proxy, gọi nội bộ trong cùng một lớp thì annotation bị bỏ qua.
 */
@Component
@RequiredArgsConstructor
public class PostAccessGuard {

    private final PostRepository postRepository;

    /** Sửa bài: KHÔNG cho ADMIN bỏ qua — kiểm duyệt thì xoá, không viết hộ */
    @Transactional(readOnly = true)
    public void requireOwnerToEdit(Integer postId) {
        check(postId, OwnershipGuard.NO_BYPASS);
    }

    /** Xoá bài: ADMIN/MODERATOR có quyền post:delete được bỏ qua ownership */
    @Transactional(readOnly = true)
    public void requireOwnerToDelete(Integer postId) {
        check(postId, OwnershipGuard.DEFAULT_BYPASS);
    }

    private void check(Integer postId, java.util.Set<com.nguyenvu.lopet.role.entity.RoleName> bypassRoles) {
        Integer viewerId = CurrentUser.viewerId();
        Integer viewerPetId = PetContext.optional();
        OwnershipGuard.check(
                () -> postRepository.findVisibleById(postId, viewerId, viewerPetId).orElse(null),
                (Post post) -> OwnershipGuard.owners(
                        post.getPet() == null || post.getPet().getAccount() == null
                                ? null
                                : post.getPet().getAccount().getId()),
                bypassRoles);
    }
}
