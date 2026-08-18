package com.nguyenvu.lopet.comment.entity;

import java.util.LinkedHashSet;
import java.util.Set;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.SQLRestriction;

import com.nguyenvu.lopet.common.entity.BaseEntity;
import com.nguyenvu.lopet.pet.entity.Pet;
import com.nguyenvu.lopet.post.entity.Post;

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
@Table(name = "comments")
@SQLRestriction("deletedAt is null")
public class Comment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /** Tên cột là {@code text} đúng như bên TS, dù trường DTO tương ứng lại tên là {@code content} */
    @Column(name = "text", nullable = false, columnDefinition = "text")
    private String text;

    /** Xoá bình luận cha kéo theo toàn bộ bình luận con (FK CASCADE) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parentId")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Comment parent;

    @Builder.Default
    @OneToMany(mappedBy = "parent", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private Set<Comment> replies = new LinkedHashSet<>();

    @Column(name = "images", columnDefinition = "text")
    private String images;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Post post;

    /**
     * Tác giả bình luận là một THÚ CƯNG. NOT NULL như cột {@code account_id} cũ — script di trú xoá
     * hẳn những bình luận không quy được về pet nào thay vì để cột rỗng.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pet_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Pet pet;
}
