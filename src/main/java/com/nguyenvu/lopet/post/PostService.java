package com.nguyenvu.lopet.post;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.nguyenvu.lopet.post.dto.CursorPage;
import com.nguyenvu.lopet.post.dto.OffsetPage;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nguyenvu.lopet.account.entity.Account;
import com.nguyenvu.lopet.account.repository.AccountRepository;
import com.nguyenvu.lopet.common.exception.BadRequestException;
import com.nguyenvu.lopet.common.exception.ForbiddenException;
import com.nguyenvu.lopet.common.exception.NotFoundException;
import com.nguyenvu.lopet.post.dto.PostDtos;
import com.nguyenvu.lopet.post.entity.MediaType;
import com.nguyenvu.lopet.post.entity.Post;
import com.nguyenvu.lopet.post.entity.PostLike;
import com.nguyenvu.lopet.post.entity.PostMedia;
import com.nguyenvu.lopet.post.repository.PostLikeRepository;
import com.nguyenvu.lopet.post.repository.PostMediaRepository;
import com.nguyenvu.lopet.post.repository.PostRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PostService {
    private static final int SUGGEST_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;

    private final PostRepository postRepository;
    private final PostMediaRepository postMediaRepository;
    private final PostLikeRepository postLikeRepository;
    private final AccountRepository accountRepository;

    @Transactional(readOnly = true)
    public List<PostDtos.PostSuggestItem> getSuggestList() {
        List<Integer> ids = postRepository.findIdsMatching(null, PageRequest.of(0, SUGGEST_SIZE));
        if (ids.isEmpty()) {
            return List.of();
        }
        return postRepository.findAllByIdsWithDetails(ids).stream()
                .map(PostMapper::toSuggestItem)
                .toList();
    }

    @Transactional(readOnly = true)
    public CursorPage<PostDtos.PostListItem> getAllByCursorStrategy(Integer lastCursor,int size) {
        Pageable pageable  = PageRequest.of(0,size+1);
        List<Post> listPost = this.postRepository.fetchNextPage(lastCursor,pageable);

        boolean hasNext = listPost.size() > size;
        if (hasNext) {
            listPost = listPost.subList(0,size);
        }
        List<PostDtos.PostListItem> resDto = listPost.stream().map(PostMapper::toListItem).collect(Collectors.toList());

        Integer nextCursor = hasNext && !listPost.isEmpty() ? listPost.get(listPost.size() -1).getId() : null;
        return new CursorPage<>(resDto,nextCursor, hasNext);
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
        Post post = postRepository.findDetailById(id).orElseThrow(NotFoundException::new);
        return PostMapper.toDetail(post);
    }

    @Transactional(readOnly = true)
    public List<PostDtos.PostByAccountItem> getByAccountId(Integer accountId) {
        return postRepository.findByAuthor(accountId).stream()
                .map(PostMapper::toByAccountItem)
                .toList();
    }

    @Transactional
    public PostDtos.CreatePostResponse create(Integer accountId, String content,
                                               List<UploadedMedia> medias) {
        Account account = accountRepository.findById(accountId).orElseThrow(BadRequestException::new);

        Post saved = postRepository.save(Post.builder()
                .account(account)
                .content(content)
                .build());

        List<PostDtos.MediaWithId> savedMedias = new ArrayList<>();
        for (UploadedMedia media : medias) {
            PostMedia entity = postMediaRepository.save(PostMedia.builder()
                    .post(saved)
                    .mediaUrl(media.url())
                    .mediaType(media.type())
                    .build());
            savedMedias.add(PostMapper.toMediaWithId(entity));
        }

        LocalDateTime now = LocalDateTime.now();
        return new PostDtos.CreatePostResponse(saved.getAccount().getId(), saved.getId(),
                saved.getContent(), savedMedias, now, now);
    }

    @Transactional
    public PostDtos.UpdatePostResponse update(Integer postId, Integer callerId, String content,
                                               List<Integer> keepMediaIds, List<UploadedMedia> newMedias) {
        Post post = postRepository.findDetailById(postId)
                .orElseThrow(() -> new BadRequestException("Post not found"));

        if (!callerId.equals(ownerAccountIdOf(post))) {
            throw new ForbiddenException("You are not the owner of this post");
        }

        post.setContent(content);

        Post updated = postRepository.save(post);

        List<PostDtos.MediaWithId> result = new ArrayList<>();
        if (keepMediaIds == null) {
            postMediaRepository.findByPostId(postId).forEach(media -> result.add(PostMapper.toMediaWithId(media)));
        } else {
            Map<Integer, PostMedia> current = postMediaRepository.findByPostId(postId).stream()
                    .collect(Collectors.toMap(PostMedia::getId, media -> media));

            for (Integer id : keepMediaIds) {
                PostMedia media = current.get(id);
                if (media == null) {
                    throw new BadRequestException("Old media not found: ID " + id);
                }
                result.add(PostMapper.toMediaWithId(media));
            }

            if (keepMediaIds.isEmpty()) {
                postMediaRepository.deleteAllByPostId(postId);
            } else {
                postMediaRepository.deleteByPostIdAndIdNotIn(postId, keepMediaIds);
            }
            postMediaRepository.flush();
        }
        for (UploadedMedia media : newMedias) {
            PostMedia entity = postMediaRepository.save(PostMedia.builder()
                    .post(updated)
                    .mediaUrl(media.url())
                    .mediaType(media.type())
                    .build());
            result.add(PostMapper.toMediaWithId(entity));
        }

        return new PostDtos.UpdatePostResponse(ownerAccountIdOf(updated), updated.getId(),
                updated.getContent(), result,
                updated.getCreatedAt(), LocalDateTime.now());
    }

    @Transactional
    public PostDtos.DeletePostResponse delete(Integer postId, Integer callerId) {
        Post post = postRepository.findById(postId).orElseThrow(BadRequestException::new);

        if (!callerId.equals(ownerAccountIdOf(post))) {
            throw new ForbiddenException("You are not the owner of this post");
        }

        postRepository.delete(post);
        return new PostDtos.DeletePostResponse(postId);
    }

    @Transactional
    public PostDtos.ReactResponse like(Integer postId, Integer accountId) {
        Post post = postRepository.findDetailById(postId).orElseThrow(BadRequestException::new);
        Account account = accountRepository.findById(accountId).orElseThrow(BadRequestException::new);

        if (postLikeRepository.findByAccountAndPost(account.getId(), post.getId()).isPresent()) {
            return new PostDtos.ReactResponse("You have already liked this post");
        }

        postLikeRepository.save(PostLike.builder().post(post).account(account).build());

        return new PostDtos.ReactResponse("Like post successfully");
    }

    @Transactional
    public PostDtos.ReactResponse unlike(Integer postId, Integer accountId) {
        Post post = postRepository.findDetailById(postId).orElseThrow(BadRequestException::new);
        Account account = accountRepository.findById(accountId).orElseThrow(BadRequestException::new);

        PostLike existing = postLikeRepository.findByAccountAndPost(account.getId(), post.getId())
                .orElse(null);
        if (existing == null) {
            return new PostDtos.ReactResponse("You have already unliked this post");
        }

        postLikeRepository.delete(existing);
        return new PostDtos.ReactResponse("Unlike post successfully");
    }

    private Integer ownerAccountIdOf(Post post) {
        return post.getAccount() == null ? null : post.getAccount().getId();
    }

    public record UploadedMedia(String url, MediaType type) {
    }
}
