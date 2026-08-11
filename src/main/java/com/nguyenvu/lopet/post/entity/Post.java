package com.nguyenvu.lopet.post.entity;

import java.util.LinkedHashSet;
import java.util.Set;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.SQLRestriction;

import com.nguyenvu.lopet.account.entity.Account;
import com.nguyenvu.lopet.common.entity.BaseEntity;
import com.nguyenvu.lopet.group.entity.Group;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "posts")
@SQLRestriction("deletedAt is null")
public class Post extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /** Tên trường bên TS là {@code accounts} (số nhiều) dù là quan hệ n-1; cột vẫn là account_id */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Account account;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Group group;

    /**
     * NULLABLE và chỉ được gán qua {@link #applyType()}. Dữ liệu cũ có thể còn NULL, vì vậy mọi
     * quyết định về quyền xem phải dựa vào {@code group_id} chứ không phải cột này.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "postType", columnDefinition = "enum('GROUP','USER')")
    private PostType postType;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "postScope", nullable = false,
            columnDefinition = "enum('PUBLIC','FRIEND','PRIVATE') not null default 'PUBLIC'")
    private PostScope postScope = PostScope.PUBLIC;

    @Builder.Default
    @OneToMany(mappedBy = "post", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private Set<PostMedia> postMedias = new LinkedHashSet<>();

    @Builder.Default
    @OneToMany(mappedBy = "post", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private Set<PostLike> postLikes = new LinkedHashSet<>();

    /** Bản sao của {@code Posts.setType()} — bài có group là bài nhóm, không thì là bài cá nhân */
    public void applyType() {
        this.postType = this.group != null ? PostType.GROUP : PostType.USER;
    }
}
