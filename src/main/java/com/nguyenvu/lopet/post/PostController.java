package com.nguyenvu.lopet.post;

import com.nguyenvu.lopet.common.exception.BadRequestException;
import com.nguyenvu.lopet.common.media.CloudinaryService;
import com.nguyenvu.lopet.common.response.ApiResponse;
import com.nguyenvu.lopet.post.dto.CursorPage;
import com.nguyenvu.lopet.post.dto.OffsetPage;
import com.nguyenvu.lopet.post.dto.PostDtos;
import com.nguyenvu.lopet.post.entity.MediaType;
import com.nguyenvu.lopet.security.Auth;
import com.nguyenvu.lopet.security.CurrentUser;
import com.nguyenvu.lopet.security.rebac.ObjectRef;
import com.nguyenvu.lopet.security.rebac.RebacEngine;
import com.nguyenvu.lopet.security.rebac.RebacModel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
    private final RebacEngine rebac;
    private final CloudinaryService cloudinaryService;

    @Operation(summary = "Get suggested posts", description = "Returns a list of posts suggested for the current user (or guest if not authenticated)")
    @GetMapping("/suggest")
    @Auth(required = false)
    public ApiResponse<List<PostDtos.PostSuggestItem>> getSuggestList() {
        return ApiResponse.ok("Get suggest posts successfully", postService.getSuggestList(CurrentUser.viewerId()));
    }

    @Operation(summary = "Get posts")
    @GetMapping
    @Auth(required = false)
    public ApiResponse<OffsetPage<PostDtos.PostListItem>> getAll(@RequestParam(required = false) String content, @Parameter(description = "First page starts at 1") @RequestParam(defaultValue = "1") int page, @Parameter(description = "Number of total record want to return, default 10") @RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.ok("Get posts successfully", postService.getAll(content, CurrentUser.viewerId(), page, limit));
    }

    @Operation(summary = "Get posts by cursor strategy")
    @GetMapping("/cursor")
    @Auth(required = false)
    public ApiResponse<CursorPage<PostDtos.PostListItem>> getAllByCursorStrategy(@Parameter(description = "If cursor is null, API retrieve data from the start") @RequestParam(required = false) Integer cursor, @Parameter(description = "Limit of total amount record start from the last seen record") @RequestParam(defaultValue = "5") int size) {
        return ApiResponse.ok("Get posts successfully", postService.getAllByCursorStrategy(cursor, size));
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
    public ApiResponse<PostDtos.CreatePostResponse> create(@RequestParam(required = false) String content, @RequestParam(required = false) String scope, @RequestPart(name = "images", required = false) MultipartFile[] images, @RequestPart(name = "videos", required = false) MultipartFile[] videos) {
        rebac.require(RebacModel.POST_CREATE, ObjectRef.platform());
        return ApiResponse.created("Create post successfully", postService.create(CurrentUser.require()
                .id(), content, scope, uploadAll(images, videos)));
    }

    @PutMapping(path = "/{postId}", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @Auth
    public ApiResponse<PostDtos.UpdatePostResponse> update(@PathVariable Integer postId, @RequestParam(required = false) String content, @RequestParam(required = false) String scope, @RequestParam(name = "oldIdsMedia", required = false) List<String> oldIdsMedia, @RequestPart(name = "images", required = false) MultipartFile[] images, @RequestPart(name = "videos", required = false) MultipartFile[] videos) {
        rebac.require(RebacModel.POST_UPDATE, ObjectRef.post(postId));
        return ApiResponse.ok("Update post successfully", postService.update(postId, CurrentUser.require()
                .id(), content, scope, parseKeepMediaIds(oldIdsMedia), uploadAll(images, videos)));
    }

    @DeleteMapping("/{id}")
    @Auth
    public ApiResponse<PostDtos.DeletePostResponse> delete(@PathVariable Integer id) {
        rebac.require(RebacModel.POST_DELETE, ObjectRef.post(id));
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
