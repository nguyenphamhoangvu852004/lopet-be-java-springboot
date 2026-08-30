package com.nguyenvu.lopet.security.rebac;

import java.util.Optional;
import java.util.Set;

import com.nguyenvu.lopet.security.jwt.UserPrincipal;

public interface RelationResolver {

    String type();

    Optional<Set<Relation>> relationsOf(Integer objectId, UserPrincipal caller);

    default Integer viewerIdOf(UserPrincipal caller) {
        return caller == null ? null : caller.id();
    }
}
