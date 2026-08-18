package com.nguyenvu.lopet.post;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.nguyenvu.lopet.common.media.CloudinaryService;
import com.nguyenvu.lopet.common.response.ApiResponse;
import com.nguyenvu.lopet.post.dto.PostDtos;
import com.nguyenvu.lopet.post.entity.MediaType;
import com.nguyenvu.lopet.security.Auth;
import com.nguyenvu.lopet.security.CurrentUser;
import com.nguyenvu.lopet.security.RequirePermission;

import com.nguyenvu.lopet.security.petcontext.PetContext;
import com.nguyenvu.lopet.security.petcontext.RequirePet;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;
    private final PostAccessGuard postAccessGuard;
    private final CloudinaryService cloudinaryService;

    /* ---------- Đọc: optionalAuth, lọc theo quyền xem ---------- */

    /**
     * Các route đọc dưới đây dùng {@code @Auth(required = false)} chứ không phải bắt buộc đăng nhập:
     * giữ nguyên khả năng xem nội dung công khai khi chưa đăng nhập, đồng thời nhận diện được người
     * đã đăng nhập để mở đúng phần nội dung họ được xem. Việc lọc nằm ở tầng repository.
     *
     * <p>Chúng cũng KHÔNG mang {@code @RequirePet}: đọc nội dung không phải là hành động nhân danh
     * một con vật, nên bắt gửi {@code X-Pet-Id} sẽ chặn cả khách vãng lai. Vì thế người xem được
     * nhận diện bằng {@code PetContext.optional()} — có header thì mở thêm nhánh "bài của pet này"
     * và "nhóm mà pet này tham gia", không có thì vẫn thấy đủ nội dung công khai và nội dung của
     * chính tài khoản mình.
     */
    @GetMapping("/suggest")
    @Auth(required = false)
    public ApiResponse<List<PostDtos.PostSuggestItem>> getSuggestList() {
        return ApiResponse.ok("Get suggest posts successfully",
                postService.getSuggestList(CurrentUser.viewerId(), PetContext.optional()));
    }

    @GetMapping
    @Auth(required = false)
    public ApiResponse<List<PostDtos.PostListItem>> getAll(
            @RequestParam(required = false) String content,
            @RequestParam(required = false) String groupId) {
        return ApiResponse.ok("Get posts successfully",
                postService.getAll(sanitize(content), parseId(groupId), CurrentUser.viewerId(),
                        PetContext.optional()));
    }

    @GetMapping("/{id}")
    @Auth(required = false)
    public ApiResponse<PostDtos.PostDetail> getById(@PathVariable Integer id) {
        return ApiResponse.ok("Get post successfully",
                postService.getOneById(id, CurrentUser.viewerId(), PetContext.optional()));
    }

    /**
     * Bài của một THÚ CƯNG — đơn vị tác giả sau khi {@code posts.account_id} thành
     * {@code posts.pet_id}. Đây là route nên dùng cho trang hồ sơ thú cưng.
     */
    @GetMapping("/pets/{id}")
    @Auth(required = false)
    public ApiResponse<List<PostDtos.PostByAccountItem>> getByPetId(@PathVariable Integer id) {
        return ApiResponse.ok("Get list by pet id " + id + " successfully",
                postService.getByPetId(id, CurrentUser.viewerId(), PetContext.optional()));
    }

    /**
     * Giữ lại route theo tài khoản: nó nay trả bài của TẤT CẢ thú cưng thuộc tài khoản đó. Không bỏ
     * đi vì client cũ đang gọi, và ý nghĩa "mọi bài của người này" vẫn diễn tả được sau khi đổi
     * khoá ngoại — khác với việc trả về id tài khoản trong từng bài, thứ đã không còn tồn tại.
     */
    @GetMapping("/accounts/{id}")
    @Auth(required = false)
    public ApiResponse<List<PostDtos.PostByAccountItem>> getByAccountId(@PathVariable Integer id) {
        return ApiResponse.ok("Get list by account id " + id + " successfully",
                postService.getByAccountId(id, CurrentUser.viewerId(), PetContext.optional()));
    }

    /* ---------- Ghi ---------- */

    @PostMapping(consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Auth
    @RequirePermission("post:create")
    @RequirePet
    public ApiResponse<PostDtos.CreatePostResponse> create(
            @RequestParam(required = false) String content,
            @RequestParam(required = false) String groupId,
            @RequestParam(required = false) String scope,
            @RequestPart(name = "images", required = false) MultipartFile[] images,
            @RequestPart(name = "videos", required = false) MultipartFile[] videos) {
        return ApiResponse.created("Create post successfully",
                postService.create(content, parseId(groupId), scope, uploadAll(images, videos)));
    }

    @PostMapping(consumes = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Auth
    @RequirePermission("post:create")
    @RequirePet
    public ApiResponse<PostDtos.CreatePostResponse> createJson(@RequestBody PostDtos.CreatePostRequest request) {
        return ApiResponse.created("Create post successfully",
                postService.create(request.content(), request.groupId(), request.scope(), List.of()));
    }

    /**
     * Ownership được kiểm TRƯỚC khi upload lên Cloudinary — request bị từ chối không được phép tốn
     * một lượt upload. ADMIN KHÔNG được bỏ qua: sửa nội dung của người khác không phải việc của kiểm
     * duyệt viên (kiểm duyệt thì xoá, không viết hộ).
     */
    @PutMapping(path = "/{postId}", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @Auth
    @RequirePermission("post:update:own")
    @RequirePet
    public ApiResponse<PostDtos.UpdatePostResponse> update(
            @PathVariable Integer postId,
            @RequestParam(required = false) String content,
            @RequestParam(required = false) String scope,
            @RequestParam(name = "oldIdsMedia", required = false) List<Integer> oldIdsMedia,
            @RequestPart(name = "images", required = false) MultipartFile[] images,
            @RequestPart(name = "videos", required = false) MultipartFile[] videos) {
        postAccessGuard.requireOwnerToEdit(postId);
        return ApiResponse.ok("Update post successfully",
                postService.update(postId, CurrentUser.require().id(), content, scope, oldIdsMedia,
                        uploadAll(images, videos)));
    }

    @PutMapping(path = "/{postId}", consumes = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
    @Auth
    @RequirePermission("post:update:own")
    @RequirePet
    public ApiResponse<PostDtos.UpdatePostResponse> updateJson(@PathVariable Integer postId,
                                                                @RequestBody PostDtos.UpdatePostRequest request) {
        postAccessGuard.requireOwnerToEdit(postId);
        return ApiResponse.ok("Update post successfully",
                postService.update(postId, CurrentUser.require().id(), request.content(), request.scope(),
                        request.oldIdsMedia(), List.of()));
    }

    /** Xoá bài: ADMIN/MODERATOR có quyền {@code post:delete} được bỏ qua ownership để kiểm duyệt */
    @DeleteMapping("/{id}")
    @Auth
    @RequirePermission({"post:delete:own", "post:delete"})
    @RequirePet
    public ApiResponse<PostDtos.DeletePostResponse> delete(@PathVariable Integer id) {
        postAccessGuard.requireOwnerToDelete(id);
        return ApiResponse.ok("Delete post successfully", postService.delete(id));
    }

    @PostMapping("/like")
    @Auth
    @RequirePet
    public ApiResponse<PostDtos.ReactResponse> like(@RequestBody PostDtos.ReactRequest request) {
        return ApiResponse.ok("Like post successfully",
                postService.like(request.postId(), CurrentUser.require().id()));
    }

    @PostMapping("/unlike")
    @Auth
    @RequirePet
    public ApiResponse<PostDtos.ReactResponse> unlike(@RequestBody PostDtos.ReactRequest request) {
        return ApiResponse.ok("Unlike post successfully",
                postService.unlike(request.postId(), CurrentUser.require().id()));
    }

    private List<PostService.UploadedMedia> uploadAll(MultipartFile[] images, MultipartFile[] videos) {
        List<PostService.UploadedMedia> medias = new ArrayList<>();
        if (images != null) {
            for (MultipartFile image : images) {
                if (!image.isEmpty()) {
                    medias.add(new PostService.UploadedMedia(
                            cloudinaryService.upload(image, CloudinaryService.IMAGE), MediaType.IMAGE));
                }
            }
        }
        if (videos != null) {
            for (MultipartFile video : videos) {
                if (!video.isEmpty()) {
                    medias.add(new PostService.UploadedMedia(
                            cloudinaryService.upload(video, CloudinaryService.VIDEO), MediaType.VIDEO));
                }
            }
        }
        return medias;
    }

    /**
     * Client hiện tại đôi khi gửi literal chuỗi {@code "undefined"} — bản TS loại bỏ giá trị đó
     * tường minh, nên ở đây cũng phải coi nó như không truyền.
     */
    private String sanitize(String value) {
        return value == null || value.isEmpty() || "undefined".equals(value) ? null : value;
    }

    private Integer parseId(String value) {
        String sanitized = sanitize(value);
        if (sanitized == null) {
            return null;
        }
        try {
            return Integer.valueOf(sanitized);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
