package com.nguyenvu.lopet.comment;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nguyenvu.lopet.account.entity.Account;
import com.nguyenvu.lopet.account.repository.AccountRepository;
import com.nguyenvu.lopet.accountprofile.entity.AccountProfile;
import com.nguyenvu.lopet.comment.dto.CommentDtos;
import com.nguyenvu.lopet.comment.entity.Comment;
import com.nguyenvu.lopet.comment.repository.CommentRepository;
import com.nguyenvu.lopet.common.exception.BadRequestException;
import com.nguyenvu.lopet.common.exception.ForbiddenException;
import com.nguyenvu.lopet.post.entity.Post;
import com.nguyenvu.lopet.post.repository.PostRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final AccountRepository accountRepository;

    @Transactional
    public CommentDtos.CreateCommentResponse create(Integer accountId, Integer postId, Integer replyCommentId,
                                                     String content, String imageUrl) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new BadRequestException("No account found"));

        Post post = postRepository.findDetailById(postId)
                .orElseThrow(() -> new BadRequestException("No post found"));

        Comment parent = null;
        if (replyCommentId != null) {
            Comment candidate = commentRepository.findDetailById(replyCommentId)
                    .orElseThrow(() -> new BadRequestException("No comment found"));
            if (candidate.getPost() == null || !candidate.getPost().getId().equals(post.getId())) {
                throw new BadRequestException("No comment found");
            }
            parent = candidate;
        }

        Comment saved = commentRepository.save(Comment.builder()
                .images(imageUrl == null ? "" : imageUrl)
                .text(content)
                .account(account)
                .parent(parent)
                .post(post)
                .build());

        return new CommentDtos.CreateCommentResponse(saved.getId());
    }

    @Transactional(readOnly = true)
    public CommentDtos.GetCommentsResponse getAllFromPost(Integer postId) {
        Post post = postRepository.findDetailById(postId)
                .orElseThrow(() -> new BadRequestException("No post found"));

        List<CommentDtos.CommentItem> items = commentRepository.findAllByPostId(post.getId()).stream()
                .map(this::toItem)
                .toList();

        return new CommentDtos.GetCommentsResponse(post.getId(), items);
    }

    @Transactional
    public CommentDtos.DeleteCommentResponse delete(Integer commentId, Integer callerId) {
        Comment comment = commentRepository.findDetailById(commentId)
                .orElseThrow(() -> new BadRequestException("No comment found"));

        // Only the comment author or the post owner may delete — replaces the removed ReBAC rule.
        if (!callerId.equals(authorIdOf(comment)) && !callerId.equals(ownerAccountIdOf(comment.getPost()))) {
            throw new ForbiddenException("You are not allowed to delete this comment");
        }

        commentRepository.delete(comment);
        return new CommentDtos.DeleteCommentResponse(comment.getId());
    }

    private CommentDtos.CommentItem toItem(Comment comment) {
        Account account = comment.getAccount();
        AccountProfile profile = account == null ? null : account.getAccountProfile();

        CommentDtos.CommentProfile profileDto = new CommentDtos.CommentProfile(
                profile == null ? 0 : profile.getId(),
                orEmpty(profile == null ? null : profile.getAvatarUrl()),
                orEmpty(profile == null ? null : profile.getCoverUrl()),
                orEmpty(profile == null ? null : profile.getBio()),
                orEmpty(profile == null ? null : profile.getFullName()),
                orEmpty(profile == null ? null : profile.getPhoneNumber()),
                profile == null || profile.getSex() == null ? 0 : profile.getSex(),
                profile == null || profile.getDateOfBirth() == null ? LocalDate.now() : profile.getDateOfBirth(),
                orEmpty(profile == null ? null : profile.getHometown()));

        CommentDtos.CommentAccount accountDto = new CommentDtos.CommentAccount(
                account == null ? 0 : account.getId(),
                orEmpty(account == null ? null : account.getUsername()),
                orEmpty(account == null ? null : account.getEmail()), profileDto);

        return new CommentDtos.CommentItem(comment.getId(), accountDto,
                comment.getParent() == null ? null : comment.getParent().getId(),
                comment.getText(), comment.getImages(), comment.getCreatedAt());
    }

    private Integer ownerAccountIdOf(Post post) {
        return post == null || post.getAccount() == null ? null : post.getAccount().getId();
    }

    private Integer authorIdOf(Comment comment) {
        return comment.getAccount() == null ? null : comment.getAccount().getId();
    }

    private String orEmpty(String value) {
        return value == null ? "" : value;
    }
}
