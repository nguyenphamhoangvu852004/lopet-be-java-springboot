package com.nguyenvu.lopet.comment;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.nguyenvu.lopet.common.media.CloudinaryService;
import com.nguyenvu.lopet.common.response.ApiResponse;
import com.nguyenvu.lopet.comment.dto.CommentDtos;
import com.nguyenvu.lopet.security.Auth;
import com.nguyenvu.lopet.security.CurrentUser;
import com.nguyenvu.lopet.security.RequirePermission;

import com.nguyenvu.lopet.security.petcontext.PetContext;
import com.nguyenvu.lopet.security.petcontext.RequirePet;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;
    private final CommentAccessGuard commentAccessGuard;
    private final CloudinaryService cloudinaryService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Auth
    @RequirePermission("comment:create")
    @RequirePet
    public ApiResponse<CommentDtos.CreateCommentResponse> create(
            @RequestParam(required = false) String content,
            @RequestParam(required = false) String postId,
            @RequestParam(required = false) String replyCommentId,
            @RequestPart(name = "image", required = false) MultipartFile image) {
        String imageUrl = image == null || image.isEmpty() ? "" : cloudinaryService.uploadImage(image);
        return created(content, postId, replyCommentId, imageUrl);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Auth
    @RequirePermission("comment:create")
    @RequirePet
    public ApiResponse<CommentDtos.CreateCommentResponse> createJson(
            @RequestBody CommentDtos.CreateCommentRequest request) {
        return created(request.content(), request.postId(), request.replyCommentId(), "");
    }

    private ApiResponse<CommentDtos.CreateCommentResponse> created(String content, String postId,
                                                                    String replyCommentId, String imageUrl) {
        return ApiResponse.created("Created comment successfully",
                commentService.create(CurrentUser.require().id(), parseId(postId),
                        parseReplyId(replyCommentId), content, imageUrl));
    }

    /**
     * Đọc bình luận dùng xác thực mềm: khách vẫn xem được bình luận của bài công khai, còn bài riêng
     * tư thì phải nhận diện được người xem mới lọc đúng.
     */
    @GetMapping("/{postId}")
    @Auth(required = false)
    public ApiResponse<CommentDtos.GetCommentsResponse> getAllFromPost(@PathVariable Integer postId) {
        return ApiResponse.ok("Get comment successfully",
                commentService.getAllFromPost(postId, CurrentUser.viewerId(), PetContext.optional()));
    }

    @DeleteMapping("/{commentId}")
    @Auth
    @RequirePermission({"comment:delete:own", "post:delete"})
    @RequirePet
    public ApiResponse<CommentDtos.DeleteCommentResponse> delete(@PathVariable Integer commentId) {
        commentAccessGuard.requireOwnerToDelete(commentId);
        return ApiResponse.ok("Deleted comment successfully", commentService.delete(commentId));
    }

    /**
     * {@code replyCommentId} rỗng/thiếu nghĩa là bình luận gốc. Bản TS dùng
     * {@code replyCommentId ? Number(replyCommentId) : null} — chuỗi rác vẫn ra NaN và bị tầng dưới
     * coi là falsy, nên ở đây giá trị không parse được cũng cho ra null thay vì ném lỗi.
     */
    private Integer parseReplyId(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Integer parseId(String value) {
        return value == null || value.isEmpty() ? null : Integer.valueOf(value);
    }
}
