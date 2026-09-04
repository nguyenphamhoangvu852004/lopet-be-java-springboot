package com.nguyenvu.lopet.comment;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;
    private final CloudinaryService cloudinaryService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Auth
    public ApiResponse<CommentDtos.CreateCommentResponse> create(
            @RequestParam(required = false) String content,
            @RequestParam(required = false) String postId,
            @RequestParam(required = false) String replyCommentId,
            @RequestPart(name = "image", required = false) MultipartFile image) {
        String imageUrl = image == null || image.isEmpty() ? "" : cloudinaryService.uploadImage(image);
        return created(content, postId, replyCommentId, imageUrl);
    }

    private ApiResponse<CommentDtos.CreateCommentResponse> created(String content, String postId,
                                                                    String replyCommentId, String imageUrl) {
        return ApiResponse.created("Created comment successfully",
                commentService.create(CurrentUser.require().id(), parseId(postId),
                        parseReplyId(replyCommentId), content, imageUrl));
    }

    @GetMapping("/{postId}")
    public ApiResponse<CommentDtos.GetCommentsResponse> getAllFromPost(@PathVariable Integer postId) {
        return ApiResponse.ok("Get comment successfully", commentService.getAllFromPost(postId));
    }

    @DeleteMapping("/{commentId}")
    @Auth
    public ApiResponse<CommentDtos.DeleteCommentResponse> delete(@PathVariable Integer commentId) {
        return ApiResponse.ok("Deleted comment successfully",
                commentService.delete(commentId, CurrentUser.require().id()));
    }

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
