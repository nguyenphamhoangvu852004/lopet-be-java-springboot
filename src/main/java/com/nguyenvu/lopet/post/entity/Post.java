package com.nguyenvu.lopet.post.entity;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.SQLRestriction;

import com.nguyenvu.lopet.account.entity.Account;
import com.nguyenvu.lopet.common.entity.BaseEntity;
import com.nguyenvu.lopet.common.exception.BadRequestException;
import com.nguyenvu.lopet.common.exception.ForbiddenException;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Aggregate root của một bài viết: sở hữu nội dung và danh sách media.
 *
 * <p>Like nằm ngoài aggregate — {@code postLikes} chỉ để đọc ({@link #likeCount()}),
 * việc thêm/bớt like đi qua PostLikeRepository để không phải nạp một collection
 * không giới hạn chỉ để thả tim một cái.
 *
 * <p>Không có setter public: mọi thay đổi trạng thái đi qua method nghiệp vụ,
 * và method nào ghi thì tự kiểm tra quyền sở hữu.
 */
@Getter
@Builder(access = AccessLevel.PACKAGE)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "posts")
@SQLRestriction("deletedAt is null")
public class Post extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Account account;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @Builder.Default
    @OneToMany(mappedBy = "post", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<PostMedia> postMedias = new LinkedHashSet<>();

    @Builder.Default
    @OneToMany(mappedBy = "post", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private Set<PostLike> postLikes = new LinkedHashSet<>();

    public static Post create(Account author, String content) {
        if (author == null) {
            throw new BadRequestException("A post must have an author");
        }
        Post post = new Post();
        post.account = author;
        post.content = normalize(content);
        post.postMedias = new LinkedHashSet<>();
        post.postLikes = new LinkedHashSet<>();
        post.requirePublishable();
        return post;
    }

    public static Post createWithMedia(Account author, String content,Iterable<PostMedia> medias) {
        if (author == null) {
            throw new BadRequestException("A post must have an author");
        }
        Post post = new Post();
        post.account = author;
        post.content = normalize(content);
        post.postMedias = new LinkedHashSet<>();
        post.postLikes = new LinkedHashSet<>();
        post.requirePublishable();
        return post;
    }

    public Integer authorId() {
        return this.account == null ? null : this.account.getId();
    }

    public boolean isOwnedBy(Integer accountId) {
        Integer owner = authorId();
        return owner != null && owner.equals(accountId);
    }

    public List<PostMedia> mediasSorted() {
        return this.postMedias.stream()
                .sorted(Comparator.comparing(PostMedia::getId,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    public int likeCount() {
        return this.postLikes.size();
    }

    @Override
    public String toString() {
        return "Post(id=" + this.id + ")";
    }

    public Set<PostMedia> getPostMedias() {
        return Collections.unmodifiableSet(this.postMedias);
    }

    public Set<PostLike> getPostLikes() {
        return Collections.unmodifiableSet(this.postLikes);
    }


    public void requireOwnedBy(Integer callerId) {
        if (!isOwnedBy(callerId)) {
            throw new ForbiddenException("You are not the owner of this post");
        }
    }

    public void editContentBy(Integer callerId, String content) {
        requireOwnedBy(callerId);
        this.content = normalize(content);
    }

    public PostMedia addMedia(String mediaUrl, MediaType type) {
        if (mediaUrl == null || mediaUrl.isBlank()) {
            throw new BadRequestException("Media url must not be empty");
        }
        if (type == null) {
            throw new BadRequestException("Media type must not be empty");
        }
        PostMedia media = PostMedia.builder()
                .post(this)
                .mediaUrl(mediaUrl)
                .mediaType(type)
                .build();
        this.postMedias.add(media);
        return media;
    }

    public void keepOnlyMedia(Collection<Integer> keepIds) {
        if (keepIds == null) {
            return;
        }
        Set<Integer> owned = new HashSet<>();
        for (PostMedia media : this.postMedias) {
            if (media.getId() != null) {
                owned.add(media.getId());
            }
        }
        for (Integer id : keepIds) {
            if (!owned.contains(id)) {
                throw new BadRequestException("Old media not found: ID " + id);
            }
        }
        this.postMedias.removeIf(media -> media.getId() != null && !keepIds.contains(media.getId()));
    }

    public void deleteBy(Integer callerId) {
        requireOwnedBy(callerId);
        markDeleted();
        this.postMedias.forEach(PostMedia::softDelete);
    }

    public void softDelete(Integer callerId) {
        requireOwnedBy(callerId);
        markDeleted();
        this.postMedias.forEach(PostMedia::softDelete);
    }

    public void requirePublishable() {
        if (Objects.toString(this.content, "").isBlank() && this.postMedias.isEmpty()) {
            throw new BadRequestException("A post must have content or at least one media");
        }
    }

    private static String normalize(String content) {
        return content == null ? "" : content.trim();
    }
}
