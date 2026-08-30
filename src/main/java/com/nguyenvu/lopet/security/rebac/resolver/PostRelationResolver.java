package com.nguyenvu.lopet.security.rebac.resolver;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.nguyenvu.lopet.post.entity.Post;
import com.nguyenvu.lopet.post.repository.PostRepository;
import com.nguyenvu.lopet.security.jwt.UserPrincipal;
import com.nguyenvu.lopet.security.rebac.ObjectRef;
import com.nguyenvu.lopet.security.rebac.Relation;
import com.nguyenvu.lopet.security.rebac.RelationResolver;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PostRelationResolver implements RelationResolver {

    private final PostRepository postRepository;

    @Override
    public String type() {
        return ObjectRef.POST;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Set<Relation>> relationsOf(Integer objectId, UserPrincipal caller) {
        Integer viewerId = viewerIdOf(caller);

        return postRepository.findVisibleById(objectId, viewerId)
                .map(post -> relations(post, viewerId));
    }

    private Set<Relation> relations(Post post, Integer viewerId) {
        EnumSet<Relation> relations = EnumSet.of(Relation.VIEWER);

        Integer ownerId = post.getAccount() == null ? null : post.getAccount().getId();
        if (viewerId != null && viewerId.equals(ownerId)) {
            relations.add(Relation.OWNER);
        }
        return relations;
    }
}
