package com.nguyenvu.lopet.post;

import com.nguyenvu.lopet.common.exception.BadRequestException;
import com.nguyenvu.lopet.common.media.CloudinaryService;
import com.nguyenvu.lopet.common.response.ApiResponse;
import com.nguyenvu.lopet.post.dto.PostDtos;
import com.nguyenvu.lopet.post.entity.MediaType;
import com.nguyenvu.lopet.security.Auth;
import com.nguyenvu.lopet.security.CurrentUser;
import com.nguyenvu.lopet.security.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Tag(name = "Post Management", description = "APIs for posts managing")
@RestController
@RequestMapping("/v1/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;
    private final PostAccessGuard postAccessGuard;
    private final CloudinaryService cloudinaryService;

    @Operation(summary = "Get suggested posts", description = "Returns a list of posts suggested for the current user (or guest if not authenticated)")
    @GetMapping("/suggest")
    @Auth(required = false)
    public ApiResponse<List<PostDtos.PostSuggestItem>> getSuggestList() {
        return ApiResponse.ok("Get suggest posts successfully", postService.getSuggestList(CurrentUser.viewerId()));
    }

    @GetMapping
    @Auth(required = false)
    public ApiResponse<List<PostDtos.PostListItem>> getAll(@RequestParam(required = false) String content, @RequestParam(required = false) String groupId) {
        return ApiResponse.ok("Get posts successfully", postService.getAll(content, parseId(groupId), CurrentUser.viewerId()));
    }

    @GetMapping("/{id}")
    @Auth(required = false)
    public ApiResponse<PostDtos.PostDetail> getById(@PathVariable Integer id) {
        return ApiResponse.ok("Get post successfully", postService.getOneById(id, CurrentUser.viewerId()));
    }

    @GetMapping("/accounts/{id}")
    @Auth(required = false)
    public ApiResponse<List<PostDtos.PostByAccountItem>> getByAccountId(@PathVariable Integer id) {
        return ApiResponse.ok("Get list by account id " + id + " successfully", postService.getByAccountId(id, CurrentUser.viewerId()));
    }


    @PostMapping(consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Auth
    @RequirePermission("post:create")
    public ApiResponse<PostDtos.CreatePostResponse> create(@RequestParam(required = false) String content, @RequestParam(required = false) String groupId, @RequestParam(required = false) String scope, @RequestPart(name = "images", required = false) MultipartFile[] images, @RequestPart(name = "videos", required = false) MultipartFile[] videos) {
        return ApiResponse.created("Create post successfully", postService.create(CurrentUser.require()
                .id(), content, parseId(groupId), scope, uploadAll(images, videos)));
    }

    @PutMapping(path = "/{postId}", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @Auth
    @RequirePermission("post:update:own")
    public ApiResponse<PostDtos.UpdatePostResponse> update(@PathVariable Integer postId, @RequestParam(required = false) String content, @RequestParam(required = false) String scope, @RequestParam(name = "oldIdsMedia", required = false) List<String> oldIdsMedia, @RequestPart(name = "images", required = false) MultipartFile[] images, @RequestPart(name = "videos", required = false) MultipartFile[] videos) {
        postAccessGuard.requireOwnerToEdit(postId);
        return ApiResponse.ok("Update post successfully", postService.update(postId, CurrentUser.require()
                .id(), content, scope, parseKeepMediaIds(oldIdsMedia), uploadAll(images, videos)));
    }

    /**
     * Xoá bài: ADMIN/MODERATOR có quyền {@code post:delete} được bỏ qua ownership để kiểm duyệt
     */
    @DeleteMapping("/{id}")
    @Auth
    @RequirePermission({"post:delete:own", "post:delete"})
    public ApiResponse<PostDtos.DeletePostResponse> delete(@PathVariable Integer id) {
        postAccessGuard.requireOwnerToDelete(id);
        return ApiResponse.ok("Delete post successfully", postService.delete(id));
    }

    @PostMapping("/like")
    @Auth
    public ApiResponse<PostDtos.ReactResponse> like(@RequestBody PostDtos.ReactRequest request) {
        return ApiResponse.ok("Like post successfully", postService.like(request.postId(), CurrentUser.require().id()));
    }

    @PostMapping("/unlike")
    @Auth
    public ApiResponse<PostDtos.ReactResponse> unlike(@RequestBody PostDtos.ReactRequest request) {
        return ApiResponse.ok("Unlike post successfully", postService.unlike(request.postId(), CurrentUser.require()
                .id()));
    }

    private List<PostService.UploadedMedia> uploadAll(MultipartFile[] images, MultipartFile[] videos) {
        List<PostService.UploadedMedia> medias = new ArrayList<>();
        if (images != null) {
            for (MultipartFile image : images) {
                if (!image.isEmpty()) {
                    medias.add(new PostService.UploadedMedia(cloudinaryService.upload(image, CloudinaryService.IMAGE), MediaType.IMAGE));
                }
            }
        }
        if (videos != null) {
            for (MultipartFile video : videos) {
                if (!video.isEmpty()) {
                    medias.add(new PostService.UploadedMedia(cloudinaryService.upload(video, CloudinaryService.VIDEO), MediaType.VIDEO));
                }
            }
        }
        return medias;
    }

    /**
     * Ba trạng thái của {@code oldIdsMedia}, và chúng KHÁC nhau:
     *
     * <ul>
     *   <li><b>Không gửi field</b> → {@code null} → giữ nguyên media của bài. Đây là ca sửa mỗi
     *       caption; gộp nó với "giữ lại rỗng" chính là bug cũ: đổi một chữ trong nội dung là mất
     *       sạch ảnh, không có cảnh báo và không lấy lại được.</li>
     *   <li><b>Gửi field với giá trị rỗng</b> ({@code oldIdsMedia=}) → danh sách rỗng → xoá hết
     *       media. Form multipart không có cách nào khác để diễn đạt "một danh sách rỗng": lặp field
     *       không lần nào thì y hệt như không gửi.</li>
     *   <li><b>Gửi các id</b> → giữ đúng những id đó, phần còn lại bị xoá.</li>
     * </ul>
     *
     * <p>Id sai định dạng thì ném 400 chứ không lặng lẽ bỏ qua như {@link #parseId}: bỏ qua ở đây
     * nghĩa là media người dùng muốn giữ rơi ra khỏi danh sách và bị xoá theo.
     */
    private List<Integer> parseKeepMediaIds(List<String> raw) {
        if (raw == null) {
            return null;
        }
        List<Integer> ids = new ArrayList<>();
        for (String value : raw) {
            if (value == null || value.isBlank()) {
                continue;
            }
            try {
                ids.add(Integer.valueOf(value.trim()));
            } catch (NumberFormatException exception) {
                throw new BadRequestException("oldIdsMedia không hợp lệ: " + value);
            }
        }
        return ids;
    }

    private Integer parseId(String value) {
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
