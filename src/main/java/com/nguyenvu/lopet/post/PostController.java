package com.nguyenvu.lopet.post;

import com.nguyenvu.lopet.common.exception.BadRequestException;
import com.nguyenvu.lopet.common.response.ApiResponse;
import com.nguyenvu.lopet.post.dto.CursorPage;
import com.nguyenvu.lopet.post.dto.OffsetPage;
import com.nguyenvu.lopet.post.dto.PostDtos;
import com.nguyenvu.lopet.security.Auth;
import com.nguyenvu.lopet.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Tag(name = "Post Management", description = "APIs for posts managing")
@RestController
@RequestMapping("/v1/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @Operation(summary = "Get posts by limit - offset strategy")
    @GetMapping("/limit-offset")
    public ApiResponse<OffsetPage<PostDtos.PostListItem>> getAllByLimitOffsetStrategy(@RequestParam(required = false) String content,
                                                                                      @Parameter(description = "First page starts at 1")
                                                                                      @RequestParam(defaultValue = "1") int page,
                                                                                      @Parameter(description = "Number of total record want to return, default 10")
                                                                                      @RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.ok("Get posts successfully", postService.getAll(content, page, limit));
    }

    @Operation(summary = "Get posts by cursor strategy")
    @GetMapping("/cursor")
    public ApiResponse<CursorPage<PostDtos.PostListItem>> getAllByCursorStrategy(@Parameter(description = "If cursor is null, API retrieve data from the start")
                                                                                 @RequestParam(required = false) Integer cursor,
                                                                                 @Parameter(description = "Limit of total amount record start from the last seen record")
                                                                                 @RequestParam(defaultValue = "5") int size) {
        return ApiResponse.ok("Get posts successfully", postService.getAllByCursorStrategy(cursor, size));
    }

    @Operation(summary = "Get post detail by id")
    @GetMapping("/{id}")
    public ApiResponse<PostDtos.PostDetail> getById(@PathVariable Integer id) {
        return ApiResponse.ok("Get post successfully", postService.getOneById(id));
    }

    @Operation(summary = "Get post list by account id")
    @GetMapping("/accounts/{id}")
    public ApiResponse<List<PostDtos.PostByAccountItem>> getByAccountId(@PathVariable Integer id) {
        return ApiResponse.ok("Get list by account id " + id + " successfully", postService.getByAccountId(id));
    }


    @Operation(summary = "Create new post")
    @PostMapping(consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
//    @Auth
    public ApiResponse<PostDtos.CreatePostResponse> create(@RequestParam(required = false) String content,
                                                           @RequestPart(name = "images", required = false) MultipartFile[] images,
                                                           @RequestPart(name = "videos", required = false) MultipartFile[] videos) {

        return ApiResponse.created("Create post successfully", postService.create(CurrentUser.require()
                .id(), content, images, videos));
    }

    @PutMapping(path = "/{postId}", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @Auth
    public ApiResponse<PostDtos.UpdatePostResponse> update(@PathVariable Integer postId,
                                                           @RequestParam(required = false) String content,
                                                           @RequestParam(name = "oldIdsMedia", required = false) List<String> oldIdsMedia, @RequestPart(name = "images", required = false) MultipartFile[] images,
                                                           @RequestPart(name = "videos", required = false) MultipartFile[] videos) {
        return ApiResponse.ok("Update post successfully", postService.update(postId, CurrentUser.require()
                .id(), content, parseKeepMediaIds(oldIdsMedia), images, videos));
    }

    @DeleteMapping("/{id}")
    @Auth
    public ApiResponse<PostDtos.DeletePostResponse> delete(@PathVariable Integer id) {
        return ApiResponse.ok("Delete post successfully", postService.delete(id, CurrentUser.require().id()));
    }

    @Operation(summary = "React to a post: like or unlike")
    @PostMapping("/react")
    @Auth
    public ApiResponse<PostDtos.ReactResponse> react(@Parameter(description = "Allowed values: like, unlike")
                                                     @RequestParam String action,
                                                     @RequestBody PostDtos.ReactRequest request) {
        Integer accountId = CurrentUser.require().id();
        return switch (ReactAction.from(action)) {
            case LIKE -> ApiResponse.ok("Like post successfully", postService.like(request.postId(), accountId));
            case UNLIKE -> ApiResponse.ok("Unlike post successfully", postService.unlike(request.postId(), accountId));
        };
    }

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
                throw new BadRequestException("Invalid oldIdsMedia: " + value);
            }
        }
        return ids;
    }
}
