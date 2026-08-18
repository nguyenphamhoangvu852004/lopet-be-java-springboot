package com.nguyenvu.lopet.comment;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.nguyenvu.lopet.comment.entity.Comment;
import com.nguyenvu.lopet.comment.repository.CommentRepository;
import com.nguyenvu.lopet.post.repository.PostRepository;
import com.nguyenvu.lopet.security.CurrentUser;
import com.nguyenvu.lopet.security.authz.OwnershipGuard;
import com.nguyenvu.lopet.security.petcontext.PetContext;

import lombok.RequiredArgsConstructor;

/**
 * Xoá bình luận: chủ bình luận, hoặc staff có quyền {@code post:delete} (kiểm duyệt).
 *
 * <p>Quyền trên bình luận LUÔN giải quyết qua bài cha, không qua bản thân bình luận: nạp bình luận
 * rồi mới đối chiếu xem người gọi có được xem bài chứa nó không. Nếu chỉ nạp theo id, phản hồi 403
 * (tồn tại, không phải của bạn) và 404 (không tồn tại) sẽ khác nhau — đủ để quét id và biết bình
 * luận nào đang tồn tại trong một bài PRIVATE.
 *
 * <p>Staff kiểm duyệt không bị ảnh hưởng: guard xét bypass TRƯỚC khi nạp, nên vẫn xoá được bình
 * luận trong bài riêng tư bị báo cáo.
 *
 * <p>Chủ bình luận là chủ TÀI KHOẢN của pet đã viết nó, không phải bản thân pet: {@code OwnershipGuard}
 * so với danh tính trong token, và người dùng không được mất quyền xoá bình luận của mình chỉ vì
 * đang thao tác nhân danh một con khác.
 */
@Component
@RequiredArgsConstructor
public class CommentAccessGuard {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;

    @Transactional(readOnly = true)
    public void requireOwnerToDelete(Integer commentId) {
        Integer viewerId = CurrentUser.viewerId();
        Integer viewerPetId = PetContext.optional();
        OwnershipGuard.check(
                () -> commentRepository.findDetailById(commentId)
                        .filter(comment -> comment.getPost() != null)
                        .filter(comment -> postRepository
                                .findVisibleById(comment.getPost().getId(), viewerId, viewerPetId)
                                .isPresent())
                        .orElse(null),
                (Comment comment) -> OwnershipGuard.owners(
                        comment.getPet() == null || comment.getPet().getAccount() == null
                                ? null
                                : comment.getPet().getAccount().getId()),
                OwnershipGuard.DEFAULT_BYPASS);
    }
}
