package com.nguyenvu.lopet.security.rebac.resolver;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.nguyenvu.lopet.comment.entity.Comment;
import com.nguyenvu.lopet.comment.repository.CommentRepository;
import com.nguyenvu.lopet.security.jwt.UserPrincipal;
import com.nguyenvu.lopet.security.rebac.ObjectRef;
import com.nguyenvu.lopet.security.rebac.Relation;
import com.nguyenvu.lopet.security.rebac.RelationResolver;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CommentRelationResolver implements RelationResolver {

    private final CommentRepository commentRepository;
    private final PostRelationResolver postRelationResolver;

    @Override
    public String type() {
        return ObjectRef.COMMENT;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Set<Relation>> relationsOf(Integer objectId, UserPrincipal caller) {
        Optional<Comment> found = commentRepository.findDetailById(objectId);
        if (found.isEmpty()) {
            return Optional.empty();
        }

        Comment comment = found.get();
        if (comment.getPost() == null) {
            return Optional.empty();
        }

        Optional<Set<Relation>> parentRelations =
                postRelationResolver.relationsOf(comment.getPost().getId(), caller);
        if (parentRelations.isEmpty()) {
            return Optional.empty();
        }

        EnumSet<Relation> relations = EnumSet.of(Relation.VIEWER);

        Integer viewerId = viewerIdOf(caller);
        Integer ownerId = comment.getAccount() == null ? null : comment.getAccount().getId();
        if (viewerId != null && viewerId.equals(ownerId)) {
            relations.add(Relation.OWNER);
        }
        return Optional.of(relations);
    }
}
