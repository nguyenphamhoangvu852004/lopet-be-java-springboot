package com.nguyenvu.lopet.post;

import com.nguyenvu.lopet.account.entity.Account;
import com.nguyenvu.lopet.account.repository.AccountRepository;
import com.nguyenvu.lopet.common.exception.BadRequestException;
import com.nguyenvu.lopet.common.exception.NotFoundException;
import com.nguyenvu.lopet.post.dto.CursorPage;
import com.nguyenvu.lopet.post.dto.OffsetPage;
import com.nguyenvu.lopet.post.dto.PostDtos;
import com.nguyenvu.lopet.post.entity.MediaType;
import com.nguyenvu.lopet.post.entity.Post;
import com.nguyenvu.lopet.post.entity.PostLike;
import com.nguyenvu.lopet.post.repository.PostLikeRepository;
import com.nguyenvu.lopet.post.repository.PostRepository;
import com.nguyenvu.lopet.upload.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PostService {
    private static final int SUGGEST_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;

    private final PostRepository postRepository;
    private final PostLikeRepository postLikeRepository;
    private final AccountRepository accountRepository;
    private final CloudinaryService cloudinaryService;

    private static MediaType toMediaType(UploadKind kind) {
        return kind == UploadKind.VIDEO ? MediaType.VIDEO : MediaType.IMAGE;
    }

    @Transactional(readOnly = true)
    public CursorPage<PostDtos.PostListItem> getAllByCursorStrategy(Integer lastCursor, int size) {
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("size must be between 1 and " + MAX_PAGE_SIZE);
        }

        Pageable pageable = PageRequest.of(0, size + 1);
        List<Post> listPost = this.postRepository.fetchNextPage(lastCursor, pageable);

        boolean hasNext = listPost.size() > size;
        if (hasNext) {
            listPost = listPost.subList(0, size);
        }
        List<PostDtos.PostListItem> resDto = listPost.stream().map(PostMapper::toListItem).collect(Collectors.toList());

        Integer nextCursor = hasNext && !listPost.isEmpty() ? listPost.get(listPost.size() - 1).getId() : null;
        return new CursorPage<>(resDto, nextCursor, hasNext);
    }

    @Transactional(readOnly = true)
    public OffsetPage<PostDtos.PostListItem> getAll(String content, int page, int limit) {
        if (page < 1) {
            throw new BadRequestException("page must be greater than or equal to 1");
        }
        if (limit < 1 || limit > MAX_PAGE_SIZE) {
            throw new BadRequestException("limit must be between 1 and " + MAX_PAGE_SIZE);
        }

        long totalItems = postRepository.countMatching(content);
        List<Integer> ids = postRepository.findIdsMatching(content, PageRequest.of(page - 1, limit));
        if (ids.isEmpty()) {
            return OffsetPage.of(List.of(), page, limit, totalItems);
        }

        List<PostDtos.PostListItem> items = postRepository.findAllByIdsWithDetails(ids).stream()
                .map(PostMapper::toListItem)
                .toList();
        return OffsetPage.of(items, page, limit, totalItems);
    }

    @Transactional(readOnly = true)
    public PostDtos.PostDetail getOneById(Integer id) {
        return PostMapper.toDetail(requirePost(id));
    }

    @Transactional(readOnly = true)
    public List<PostDtos.PostByAccountItem> getByAccountId(Integer accountId) {
        return postRepository.findByAuthor(accountId).stream()
                .map(PostMapper::toByAccountItem)
                .toList();
    }

    @Transactional
    public PostDtos.CreatePostResponse create(Integer accountId, String content,
                                              MultipartFile[] images, MultipartFile[] videos) {
        Account author = accountRepository.findById(accountId)
                .orElseThrow(() -> new BadRequestException("No account found"));

        Post post = Post.create(author, content);

        List<UploadedFile> listImagesUploaded = cloudinaryService.upload(new Images(images));
        listImagesUploaded.forEach(file -> post.addMedia(file.url(), toMediaType(file.kind())));

        List<UploadedFile> listVideoUploaded = cloudinaryService.upload(new Videos(videos));
        listVideoUploaded.forEach(file -> post.addMedia(file.url(), toMediaType(file.kind())));


        Post saved = postRepository.saveAndFlush(post);

        return new PostDtos.CreatePostResponse(saved.authorId(), saved.getId(), saved.getContent(),
                PostMapper.toMediasWithId(saved), saved.getCreatedAt(), saved.getUpdatedAt());
    }

    @Transactional
    public PostDtos.UpdatePostResponse update(Integer postId, Integer callerId, String content,
                                              List<Integer> keepMediaIds, MultipartFile[] images,
                                              MultipartFile[] videos) {
        Post post = requirePost(postId);

        post.editContentBy(callerId, content);
        post.keepOnlyMedia(keepMediaIds);
        for (UploadedFile media : cloudinaryService.upload(new Images(images), new Videos(videos))) {
            post.addMedia(media.url(), toMediaType(media.kind()));
        }
        post.requirePublishable();

        Post updated = postRepository.saveAndFlush(post);

        return new PostDtos.UpdatePostResponse(updated.authorId(), updated.getId(), updated.getContent(),
                PostMapper.toMediasWithId(updated), updated.getCreatedAt(), updated.getUpdatedAt());
    }

    @Transactional
    public PostDtos.DeletePostResponse delete(Integer postId, Integer callerId) {
        Post post = postRepository.findById(postId).orElseThrow(() -> new NotFoundException("Post not found"));

        post.softDelete(callerId);
        postRepository.save(post);

        return new PostDtos.DeletePostResponse(postId);
    }

    @Transactional
    public PostDtos.ReactResponse like(Integer postId, Integer accountId) {
        Post post = requirePost(postId);
        Account account = requireAccount(accountId);

        if (postLikeRepository.findByAccountAndPost(account.getId(), post.getId()).isPresent()) {
            return new PostDtos.ReactResponse("You have already liked this post");
        }

        postLikeRepository.save(PostLike.of(post, account));

        return new PostDtos.ReactResponse("Like post successfully");
    }

    @Transactional
    public PostDtos.ReactResponse unlike(Integer postId, Integer accountId) {
        Post post = requirePost(postId);
        Account account = requireAccount(accountId);

        PostLike existing = postLikeRepository.findByAccountAndPost(account.getId(), post.getId())
                .orElse(null);
        if (existing == null) {
            return new PostDtos.ReactResponse("You have already unliked this post");
        }

        postLikeRepository.delete(existing);
        return new PostDtos.ReactResponse("Unlike post successfully");
    }

    private Post requirePost(Integer postId) {
        return postRepository.findDetailById(postId)
                .orElseThrow(() -> new NotFoundException("Post not found"));
    }

    private Account requireAccount(Integer accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new BadRequestException("No account found"));
    }
}
